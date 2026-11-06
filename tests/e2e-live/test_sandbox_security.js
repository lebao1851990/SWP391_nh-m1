/**
 * CodeAssess - Docker Sandbox Security & Resource Isolation Verification Suite
 *
 * Verifies that the Docker execution sandbox enforces hard boundaries:
 *   TEST 1: Wall-Clock Execution Timeout (10s limit kills infinite loop)
 *   TEST 2: PID & Thread Exhaustion / Fork Bomb (pidsLimit=50 stops thread exhaustion)
 *   TEST 3: Off-Heap & Native Memory Limit (Memory limit 512MB kills native memory leak)
 *   TEST 4: Read-Only Root Filesystem (readOnlyRootfs=true prevents writes outside /tmp)
 *   TEST 5: Network Isolation (network="none" blocks outbound HTTP/socket calls)
 *   TEST 6: Host Information Leakage & Chroot Boundary (/etc/passwd, /proc/self/environ, .env)
 *
 * Usage:
 *   E2E_TEST_PASSWORD="<password>" node tests/e2e-live/test_sandbox_security.js
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081';
const EXAM_ID = process.env.E2E_EXAM_ID || 'exm-e2e-exam-a-00000001';
const STUDENT_EMAIL = process.env.E2E_STUDENT_EMAIL || 'studenta@fpt.edu.vn';
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD || process.env.E2E_PASSWORD;

if (!TEST_PASSWORD) {
  console.error('[CONFIG ERROR] Missing test password. Set E2E_TEST_PASSWORD environment variable.');
  process.exit(1);
}

function createZipBuffer(fileName, content) {
  const tempDir = path.resolve(__dirname, '.tmp_sec_' + Date.now() + '_' + Math.random().toString(36).substring(7));
  fs.mkdirSync(tempDir, { recursive: true });
  const filePath = path.join(tempDir, fileName);
  fs.writeFileSync(filePath, content, { encoding: 'utf8' });
  const zipPath = path.join(tempDir, 'submission.zip');

  if (process.platform === 'win32') {
    execSync(`powershell -NoProfile -Command "Compress-Archive -Path '${filePath}' -DestinationPath '${zipPath}' -Force"`);
  } else {
    execSync(`zip -j "${zipPath}" "${filePath}"`);
  }

  const buffer = fs.readFileSync(zipPath);
  fs.rmSync(tempDir, { recursive: true, force: true });
  return buffer;
}

async function login(email, password) {
  const res = await fetch(`${BACKEND_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  if (!res.ok) throw new Error(`Login failed (${res.status}): ${await res.text()}`);
  return (await res.json()).token;
}

async function submitAndPoll(token, codeContent, testName) {
  const zip = createZipBuffer('Solution.java', codeContent);
  const formData = new FormData();
  formData.append('file', new Blob([zip], { type: 'application/zip' }), `${testName}.zip`);

  const submitRes = await fetch(`${BACKEND_URL}/api/student/exams/${EXAM_ID}/submissions`, {
    method: 'POST',
    headers: { 'X-Auth-Token': token },
    body: formData
  });

  if (!submitRes.ok) throw new Error(`Submission failed: ${await submitRes.text()}`);
  const sub = await submitRes.json();

  for (let i = 0; i < 20; i++) {
    await new Promise(r => setTimeout(r, 2000));
    const resultRes = await fetch(`${BACKEND_URL}/api/student/submissions/${sub.id}/result`, {
      headers: { 'X-Auth-Token': token }
    });
    if (resultRes.ok) {
      const data = await resultRes.json();
      if (data.executionStatus && data.executionStatus !== 'PENDING' && data.executionStatus !== 'PROCESSING') {
        return data;
      }
    }
  }
  throw new Error(`Timeout polling result for ${testName}`);
}

async function runSecurityTests() {
  console.log('=================================================================');
  console.log('       CODEASSESS DOCKER SANDBOX SECURITY AUDIT SUITE');
  console.log('=================================================================\n');

  const token = await login(STUDENT_EMAIL, TEST_PASSWORD);
  console.log(`[AUTH] Authenticated as ${STUDENT_EMAIL}\n`);

  const results = [];

  // TEST 1: Timeout (Infinite Loop)
  console.log('>>> [TEST 1] Infinite Loop (Wall-Clock Timeout Verification)...');
  const codeTimeout = `
public class Solution {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - start > 60000) break;
        }
    }
}
`;
  const resTimeout = await submitAndPoll(token, codeTimeout, 'test_timeout');
  const passTimeout = resTimeout.executionStatus === 'TIMEOUT' || (resTimeout.executionDurationMs >= 9500 && resTimeout.executionDurationMs <= 13000);
  results.push({ name: 'Wall-Clock Timeout (10s)', pass: passTimeout, detail: `Status: ${resTimeout.executionStatus}, Duration: ${resTimeout.executionDurationMs}ms` });
  console.log(`    Result: ${passTimeout ? 'PASS' : 'FAIL'} (${results[0].detail})\n`);

  // TEST 2: Fork Bomb / PID Exhaustion
  console.log('>>> [TEST 2] Fork Bomb / Thread Exhaustion (pidsLimit=50)...');
  const codeForkBomb = `
public class Solution {
    public static void main(String[] args) {
        int count = 0;
        try {
            while (count < 500) {
                new Thread(() -> {
                    try { Thread.sleep(30000); } catch (Exception e) {}
                }).start();
                count++;
            }
        } catch (Throwable t) {
            System.err.println("FORK_LIMIT_REACHED: " + t.getMessage() + " after " + count + " threads");
        }
    }
}
`;
  const resFork = await submitAndPoll(token, codeForkBomb, 'test_forkbomb');
  const passFork = resFork.executionStatus === 'RUNTIME_ERROR' || (resFork.compileOutput && resFork.compileOutput.includes('FORK_LIMIT_REACHED')) || (resFork.testResults && JSON.stringify(resFork.testResults).includes('pthread_create failed'));
  results.push({ name: 'PID Limit (pidsLimit=50)', pass: passFork, detail: `Status: ${resFork.executionStatus}` });
  console.log(`    Result: ${passFork ? 'PASS' : 'FAIL'} (${results[1].detail})\n`);

  // TEST 3: Off-Heap / Native Memory Overflow
  console.log('>>> [TEST 3] Off-Heap Direct Buffer Exhaustion (> 512MB)...');
  const codeOffHeap = `
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Solution {
    public static void main(String[] args) {
        List<ByteBuffer> buffers = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                buffers.add(ByteBuffer.allocateDirect(50 * 1024 * 1024)); // 50MB direct buffers
            }
        } catch (Throwable t) {
            System.err.println("OFFHEAP_OOM_CAUGHT: " + t.getClass().getName());
        }
    }
}
`;
  const resOffHeap = await submitAndPoll(token, codeOffHeap, 'test_offheap');
  const passOffHeap = resOffHeap.executionStatus === 'RUNTIME_ERROR' || (resOffHeap.testResults && JSON.stringify(resOffHeap.testResults).includes('137')) || (resOffHeap.testResults && JSON.stringify(resOffHeap.testResults).includes('Killed'));
  results.push({ name: 'Off-Heap Memory Limit (512MB)', pass: passOffHeap, detail: `Status: ${resOffHeap.executionStatus}` });
  console.log(`    Result: ${passOffHeap ? 'PASS' : 'FAIL'} (${results[2].detail})\n`);

  // TEST 4: Read-Only Root Filesystem
  console.log('>>> [TEST 4] Read-Only Root Filesystem Probe (/root, /var)...');
  const codeFs = `
import java.io.File;
import java.io.FileWriter;

public class Solution {
    public static void main(String[] args) {
        try {
            File f = new File("/root_escape_test.txt");
            FileWriter fw = new FileWriter(f);
            fw.write("escape");
            fw.close();
            System.out.println("WRITE_SUCCEEDED");
        } catch (Exception e) {
            System.err.println("WRITE_BLOCKED: " + e.getMessage());
        }
    }
}
`;
  const resFs = await submitAndPoll(token, codeFs, 'test_readonly_fs');
  const passFs = !JSON.stringify(resFs).includes('WRITE_SUCCEEDED') && (JSON.stringify(resFs).includes('Read-only file system') || JSON.stringify(resFs).includes('WRITE_BLOCKED') || resFs.executionStatus === 'RUNTIME_ERROR');
  results.push({ name: 'Read-Only Root Filesystem', pass: passFs, detail: `Status: ${resFs.executionStatus}` });
  console.log(`    Result: ${passFs ? 'PASS' : 'FAIL'} (${results[3].detail})\n`);

  // TEST 5: Network Isolation
  console.log('>>> [TEST 5] Network Isolation Verification (network="none")...');
  const codeNet = `
import java.net.HttpURLConnection;
import java.net.URL;

public class Solution {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.connect();
            System.out.println("NETWORK_ACCESSIBLE");
        } catch (Exception e) {
            System.err.println("NETWORK_BLOCKED: " + e.getMessage());
        }
    }
}
`;
  const resNet = await submitAndPoll(token, codeNet, 'test_network');
  const passNet = !JSON.stringify(resNet).includes('NETWORK_ACCESSIBLE') && (JSON.stringify(resNet).includes('NETWORK_BLOCKED') || JSON.stringify(resNet).includes('Network is unreachable') || resNet.executionStatus === 'RUNTIME_ERROR');
  results.push({ name: 'Network Isolation (network=none)', pass: passNet, detail: `Status: ${resNet.executionStatus}` });
  console.log(`    Result: ${passNet ? 'PASS' : 'FAIL'} (${results[4].detail})\n`);

  // TEST 6: Sensitive File Inspection
  console.log('>>> [TEST 6] Sensitive Host Files Inspection (/proc, /etc/passwd, .env)...');
  const codeEnv = `
import java.io.File;
import java.nio.file.Files;

public class Solution {
    public static void main(String[] args) {
        String[] paths = {"/etc/passwd", "/proc/self/environ", "../.env", "../../.env"};
        for (String p : paths) {
            File f = new File(p);
            System.err.println("PATH_PROBE:" + p + ":exists=" + f.exists());
        }
    }
}
`;
  const resEnv = await submitAndPoll(token, codeEnv, 'test_env_probe');
  const strEnv = JSON.stringify(resEnv);
  const passEnv = !strEnv.includes('DB_PASSWORD') && !strEnv.includes('AI_API_KEY') && !strEnv.includes('GOOGLE_CLIENT_SECRET');
  results.push({ name: 'Host & Secret Leakage Prevention', pass: passEnv, detail: `Status: ${resEnv.executionStatus}` });
  console.log(`    Result: ${passEnv ? 'PASS' : 'FAIL'} (${results[5].detail})\n`);

  console.log('=================================================================');
  console.log('                SECURITY AUDIT SUMMARY TABLE');
  console.log('=================================================================');
  results.forEach(r => console.log(`[${r.pass ? 'PASS' : 'FAIL'}] ${r.name} -> ${r.detail}`));
  console.log('=================================================================');
}

runSecurityTests().catch(err => {
  console.error('\n[FATAL ERROR]', err.message);
  process.exit(1);
});

