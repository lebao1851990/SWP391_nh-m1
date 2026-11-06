const http = require('node:http');
const crypto = require('node:crypto');

const DOCKER_SOCKET = process.env.DOCKER_SOCKET || '/var/run/docker.sock';
const DOCKER_HOST = process.env.DOCKER_HOST || (process.env.DOCKER_SOCKET ? null : 'http://docker-proxy:2375');
const SANDBOX_IMAGE = 'eclipse-temurin:21-jdk-alpine';
const MAX_MEMORY_BYTES = 512 * 1024 * 1024; // 512 MiB
const MAX_NANO_CPUS = 500_000_000; // 0.5 CPU
const MAX_PIDS = 50;
const TMP_DIR = '/tmp/sandbox';
const CLASS_DIR = '/tmp/sandbox/classes';

function dockerApiRequest(method, path, body = null, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    let options;
    if (DOCKER_HOST && DOCKER_HOST.startsWith('http')) {
      const url = new URL(DOCKER_HOST);
      options = {
        hostname: url.hostname,
        port: url.port ? Number(url.port) : 2375,
        path: `/v1.45${path}`,
        method,
        headers: {
          'Content-Type': 'application/json',
        },
        timeout: timeoutMs,
      };
    } else {
      options = {
        socketPath: DOCKER_SOCKET,
        path: `/v1.45${path}`,
        method,
        headers: {
          'Content-Type': 'application/json',
        },
        timeout: timeoutMs,
      };
    }

    const req = http.request(options, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => {
        const buffer = Buffer.concat(chunks);
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: buffer,
        });
      });
    });

    req.on('timeout', () => {
      req.destroy(new Error(`Docker API request timed out after ${timeoutMs}ms`));
    });

    req.on('error', (err) => reject(err));

    if (body) {
      const payload = typeof body === 'string' ? body : JSON.stringify(body);
      req.write(payload);
    }
    req.end();
  });
}

function demuxDockerLogs(buffer) {
  let stdout = '';
  let stderr = '';
  let offset = 0;

  while (offset + 8 <= buffer.length) {
    const streamType = buffer[offset];
    const size = buffer.readUInt32BE(offset + 4);
    offset += 8;

    if (offset + size > buffer.length) {
      // Incomplete frame, consume rest
      const remaining = buffer.toString('utf-8', offset);
      if (streamType === 2) stderr += remaining;
      else stdout += remaining;
      break;
    }

    const chunk = buffer.toString('utf-8', offset, offset + size);
    offset += size;

    if (streamType === 2) {
      stderr += chunk;
    } else {
      stdout += chunk;
    }
  }

  // Fallback if header was plain text
  if (!stdout && !stderr && buffer.length > 0) {
    stdout = buffer.toString('utf-8');
  }

  return { stdout, stderr };
}

function normalizeOutput(text) {
  if (text == null) return '';
  return text
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => line.trimEnd())
    .join('\n')
    .trim();
}

function extractMainClassName(sourceCode) {
  if (sourceCode) {
    const match = sourceCode.match(/\bpublic\s+(?:final\s+)?class\s+([A-Za-z0-9_$]+)/);
    if (match && match[1]) {
      return match[1];
    }
  }
  return 'Main';
}

class DockerRunner {
  static validateGatekeeperPolicy(createPayload) {
    if (createPayload.Image !== SANDBOX_IMAGE) {
      throw new Error(`Gatekeeper Violation: Image ${createPayload.Image} is unauthorized. Must be ${SANDBOX_IMAGE}`);
    }
    if (createPayload.HostConfig) {
      const hc = createPayload.HostConfig;
      if (hc.Privileged) {
        throw new Error('Gatekeeper Violation: Privileged containers are strictly forbidden.');
      }
      if (hc.Binds && hc.Binds.length > 0) {
        throw new Error('Gatekeeper Violation: Host volume bind mounts are strictly forbidden.');
      }
      if (hc.Memory > MAX_MEMORY_BYTES) {
        throw new Error(`Gatekeeper Violation: Memory exceeds limit of ${MAX_MEMORY_BYTES} bytes.`);
      }
      if (hc.NanoCPUs > MAX_NANO_CPUS) {
        throw new Error(`Gatekeeper Violation: CPU exceeds limit of ${MAX_NANO_CPUS} NanoCPUs.`);
      }
      if (hc.PidsLimit > MAX_PIDS) {
        throw new Error(`Gatekeeper Violation: PIDs limit exceeds maximum ${MAX_PIDS}.`);
      }
      if (!hc.ReadonlyRootfs) {
        throw new Error('Gatekeeper Violation: Root filesystem must be read-only.');
      }
    }
    if (!createPayload.NetworkDisabled) {
      throw new Error('Gatekeeper Violation: Network must be disabled.');
    }
  }

  static async createSandboxContainer(shellScript) {
    const payload = {
      Image: SANDBOX_IMAGE,
      User: '65534:65534', // nobody:nobody
      NetworkDisabled: true,
      WorkingDir: '/tmp',
      Env: ['LANG=C.UTF-8', 'LC_ALL=C.UTF-8'],
      Cmd: ['sh', '-c', shellScript],
      HostConfig: {
        Memory: MAX_MEMORY_BYTES,
        MemorySwap: MAX_MEMORY_BYTES,
        NanoCPUs: MAX_NANO_CPUS,
        PidsLimit: MAX_PIDS,
        ReadonlyRootfs: true,
        CapDrop: ['ALL'],
        SecurityOpt: ['no-new-privileges:true'],
        Tmpfs: {
          '/tmp': 'rw,noexec,nosuid,size=33554432', // 32 MiB tmpfs
        },
        Binds: [],
        Privileged: false,
        LogConfig: {
          Type: 'json-file',
          Config: { 'max-size': '1m', 'max-file': '1' },
        },
      },
    };

    // Strict Gatekeeper assertion
    DockerRunner.validateGatekeeperPolicy(payload);

    const res = await dockerApiRequest('POST', '/containers/create', payload);
    if (res.statusCode !== 201) {
      throw new Error(`Failed to create sandbox container: HTTP ${res.statusCode} ${res.body.toString()}`);
    }
    const data = JSON.parse(res.body.toString());
    return data.Id;
  }

  static async removeContainer(containerId) {
    if (!containerId) return;
    try {
      await dockerApiRequest('DELETE', `/containers/${containerId}?v=1&force=1`, null, 5000);
    } catch (e) {
      console.warn(`[Sandbox] Failed to remove container ${containerId.substring(0, 12)}:`, e.message);
    }
  }

  static async stopContainer(containerId) {
    if (!containerId) return;
    try {
      await dockerApiRequest('POST', `/containers/${containerId}/stop?t=1`, null, 3000);
    } catch {
      try {
        await dockerApiRequest('POST', `/containers/${containerId}/kill`, null, 2000);
      } catch {}
    }
  }

  static async runTestCaseSuite({ sourceCode, testCases, mainClassName, timeoutSeconds = 10 }) {
    const className = mainClassName || extractMainClassName(sourceCode);
    const base64Source = Buffer.from(sourceCode, 'utf-8').toString('base64');
    const runToken = crypto.randomBytes(8).toString('hex');
    const compileMarker = `TC_COMPILE_${runToken}`;
    const tcStart = `TC_START_${runToken}_`;
    const tcExit = `TC_EXIT_${runToken}_`;
    const tcTime = `TC_TIME_${runToken}_`;
    const tcOut = `TC_OUT_${runToken}_`;
    const tcErr = `TC_ERR_${runToken}_`;
    const tcEnd = `TC_END_${runToken}_`;

    const sourceFilePath = `${TMP_DIR}/${className}.java`;
    let script = 'set -e; ';
    script += `mkdir -p ${TMP_DIR} ${CLASS_DIR}; `;
    script += `echo '${base64Source}' | base64 -d > ${sourceFilePath}; `;
    script += `if ! javac -d ${CLASS_DIR} ${sourceFilePath} 2>/tmp/sandbox/compile.err; then `;
    script += `  echo '<<<${compileMarker}:FAILED>>>'; `;
    script += '  cat /tmp/sandbox/compile.err; ';
    script += '  exit 1; ';
    script += 'fi; ';
    script += `echo '<<<${compileMarker}:SUCCESS>>>'; `;
    script += 'set +e; ';

    let totalTimeoutSec = 8; // startup and compile budget
    for (let i = 0; i < testCases.length; i++) {
      const tc = testCases[i];
      const input = tc.inputData != null ? tc.inputData : '';
      const base64Input = Buffer.from(input, 'utf-8').toString('base64');
      const tcTimeout = tc.timeoutSeconds > 0 ? tc.timeoutSeconds : 5;
      totalTimeoutSec += tcTimeout;

      script += `echo '${base64Input}' | base64 -d > /tmp/sandbox/in_${i}.txt; `;
      script += `echo '<<<${tcStart}${i}>>>'; `;
      script += 'START_TS=$(date +%s%3N 2>/dev/null || date +%s000); ';
      script += `timeout -k 1s -s KILL ${tcTimeout} java -cp ${CLASS_DIR} -Xmx256m -Xms32m -Djava.security.manager=disallow ${className} < /tmp/sandbox/in_${i}.txt > /tmp/sandbox/out_${i}.txt 2> /tmp/sandbox/err_${i}.txt; `;
      script += 'ec=$?; ';
      script += 'END_TS=$(date +%s%3N 2>/dev/null || date +%s000); ';
      script += 'DUR=$((END_TS - START_TS)); ';
      script += `echo '<<<${tcExit}${i}:'$ec'>>>'; `;
      script += `echo '<<<${tcTime}${i}:'$DUR'>>>'; `;
      script += `echo '<<<${tcOut}${i}>>>'; `;
      script += `cat /tmp/sandbox/out_${i}.txt; `;
      script += `echo '<<<${tcErr}${i}>>>'; `;
      script += `cat /tmp/sandbox/err_${i}.txt; `;
      script += `echo '<<<${tcEnd}${i}>>>'; `;
    }
    script += 'exit 0;';

    const maxWaitSec = Math.max(totalTimeoutSec, timeoutSeconds);
    let containerId = null;
    let timedOut = false;
    const startMs = Date.now();

    try {
      containerId = await DockerRunner.createSandboxContainer(script);

      // Start container
      const startRes = await dockerApiRequest('POST', `/containers/${containerId}/start`);
      if (startRes.statusCode !== 204 && startRes.statusCode !== 304) {
        throw new Error(`Failed to start container: HTTP ${startRes.statusCode}`);
      }

      // Timeout watchdog
      const timer = setTimeout(() => {
        timedOut = true;
        DockerRunner.stopContainer(containerId);
      }, maxWaitSec * 1000);

      // Wait for container completion
      try {
        await dockerApiRequest('POST', `/containers/${containerId}/wait`, null, (maxWaitSec + 3) * 1000);
      } finally {
        clearTimeout(timer);
      }

      const totalElapsedMs = Date.now() - startMs;

      // Collect logs
      const logRes = await dockerApiRequest('GET', `/containers/${containerId}/logs?stdout=1&stderr=1`);
      const { stdout: demuxOut, stderr: demuxErr } = demuxDockerLogs(logRes.body);
      const fullLog = `${demuxOut}\n${demuxErr}`;

      // Check compilation marker
      const failedMarker = `<<<${compileMarker}:FAILED>>>`;
      const successMarker = `<<<${compileMarker}:SUCCESS>>>`;

      if (fullLog.includes(failedMarker)) {
        const idx = fullLog.indexOf(failedMarker);
        const compileErr = fullLog.substring(idx + failedMarker.length).trim();
        const itemResults = testCases.map((tc) => ({
          testCaseId: tc.id != null ? String(tc.id) : null,
          testCaseName: tc.name,
          isHidden: !!tc.isHidden,
          status: 'COMPILE_ERROR',
          errorMessage: compileErr,
          actualOutput: '',
          expectedOutput: tc.expectedOutput || '',
          pointsAwarded: 0.0,
          maxPoints: tc.points != null ? Number(tc.points) : 0.0,
          executionTimeMs: 0,
          exitCode: 1,
        }));

        return {
          compiled: false,
          compileOutput: compileErr,
          itemResults,
          totalDurationMs: totalElapsedMs,
        };
      }

      const compiled = fullLog.includes(successMarker);
      const itemResults = [];

      for (let i = 0; i < testCases.length; i++) {
        const tc = testCases[i];
        const startTag = `<<<${tcStart}${i}>>>`;
        const exitTagPrefix = `<<<${tcExit}${i}:`;
        const timeTagPrefix = `<<<${tcTime}${i}:`;
        const outTag = `<<<${tcOut}${i}>>>`;
        const errTag = `<<<${tcErr}${i}>>>`;
        const endTag = `<<<${tcEnd}${i}>>>`;

        if (!fullLog.includes(startTag) || !fullLog.includes(endTag)) {
          itemResults.push({
            testCaseId: tc.id != null ? String(tc.id) : null,
            testCaseName: tc.name,
            isHidden: !!tc.isHidden,
            status: timedOut ? 'TIMEOUT' : 'NOT_EXECUTED',
            errorMessage: timedOut ? 'Execution timed out' : 'Process terminated prematurely',
            actualOutput: '',
            expectedOutput: tc.expectedOutput || '',
            pointsAwarded: 0.0,
            maxPoints: tc.points != null ? Number(tc.points) : 0.0,
            executionTimeMs: 0,
            exitCode: null,
          });
          continue;
        }

        // Parse exit code
        let exitCode = null;
        const exitStart = fullLog.indexOf(exitTagPrefix);
        if (exitStart >= 0) {
          const exitEnd = fullLog.indexOf('>>>', exitStart);
          if (exitEnd > exitStart) {
            const codeStr = fullLog.substring(exitStart + exitTagPrefix.length, exitEnd).trim();
            exitCode = parseInt(codeStr, 10);
            if (isNaN(exitCode)) exitCode = null;
          }
        }

        // Parse duration
        let durationMs = 0;
        const timeStart = fullLog.indexOf(timeTagPrefix);
        if (timeStart >= 0) {
          const timeEnd = fullLog.indexOf('>>>', timeStart);
          if (timeEnd > timeStart) {
            const timeStr = fullLog.substring(timeStart + timeTagPrefix.length, timeEnd).trim();
            durationMs = parseInt(timeStr, 10) || 0;
          }
        }

        // Parse stdout
        let stdout = '';
        const outStart = fullLog.indexOf(outTag);
        const errStart = fullLog.indexOf(errTag);
        if (outStart >= 0 && errStart > outStart) {
          stdout = fullLog.substring(outStart + outTag.length, errStart);
          if (stdout.startsWith('\r\n')) stdout = stdout.substring(2);
          else if (stdout.startsWith('\n')) stdout = stdout.substring(1);
        }

        // Parse stderr
        let stderr = '';
        const endStart = fullLog.indexOf(endTag);
        if (errStart >= 0 && endStart > errStart) {
          stderr = fullLog.substring(errStart + errTag.length, endStart);
          if (stderr.startsWith('\r\n')) stderr = stderr.substring(2);
          else if (stderr.startsWith('\n')) stderr = stderr.substring(1);
        }

        let status = 'FAILED';
        let pointsAwarded = 0.0;
        let errorMessage = null;

        if (exitCode === 124 || exitCode === 137 || exitCode === 143) {
          status = 'TIMEOUT';
          errorMessage = `Execution timed out (exceeded ${tc.timeoutSeconds || 5}s limit)`;
        } else if (exitCode != null && exitCode !== 0) {
          status = 'RUNTIME_ERROR';
          errorMessage = stderr.trim();
        } else {
          const actualNorm = normalizeOutput(stdout);
          const expectedNorm = normalizeOutput(tc.expectedOutput);
          if (actualNorm === expectedNorm) {
            status = 'PASSED';
            pointsAwarded = tc.points != null ? Number(tc.points) : 10.0;
          } else {
            status = 'FAILED';
          }
        }

        itemResults.push({
          testCaseId: tc.id != null ? String(tc.id) : null,
          testCaseName: tc.name,
          isHidden: !!tc.isHidden,
          status,
          actualOutput: stdout,
          expectedOutput: tc.expectedOutput || '',
          pointsAwarded,
          maxPoints: tc.points != null ? Number(tc.points) : 10.0,
          executionTimeMs: durationMs,
          exitCode,
          errorMessage,
        });
      }

      return {
        compiled,
        compileOutput: compiled ? 'Compilation successful' : 'Compilation failed',
        itemResults,
        totalDurationMs: totalElapsedMs,
      };
    } finally {
      if (containerId) {
        await DockerRunner.removeContainer(containerId);
      }
    }
  }
}

module.exports = {
  DockerRunner,
  dockerApiRequest,
  demuxDockerLogs,
  normalizeOutput,
};
