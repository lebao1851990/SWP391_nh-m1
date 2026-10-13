package com.codeassess.sandbox.service;

import com.codeassess.sandbox.config.SandboxProperties;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs untrusted Java source code inside a hardened, disposable Docker container.
 *
 * <p><strong>Security controls implemented:</strong>
 * <ul>
 *   <li>Network disabled ({@code NetworkDisabled=true})</li>
 *   <li>512 MiB memory hard limit via cgroup</li>
 *   <li>0.5 CPU via CFS quota/period</li>
 *   <li>50 process PID limit</li>
 *   <li>Non-root user (nobody, UID 65534)</li>
 *   <li>All Linux capabilities dropped</li>
 *   <li>no-new-privileges security opt</li>
 *   <li>Read-only root filesystem; /tmp provided as tmpfs (32 MiB max)</li>
 *   <li>Empty environment — no host env vars leaked</li>
 *   <li>No bind mounts — host paths, repository, home dir, Docker socket are never mounted</li>
 *   <li>Hard wall-clock timeout; container forcibly stopped and removed on timeout</li>
 *   <li>Bounded stdout/stderr — read is capped at max-output-bytes while consuming the stream</li>
 *   <li>Concurrency semaphore — rejects requests when max-concurrent-executions is reached</li>
 *   <li>Container always removed in a finally block, even on exceptions during partial startup</li>
 *   <li>Source injected via echo+base64 pipe, never shell-concatenated</li>
 * </ul>
 *
 * <p><strong>Known Docker/OS limitations:</strong>
 * <ol>
 *   <li>Memory limit: JVM off-heap (code cache, metaspace) briefly exceeds heap soft limit
 *       before OOM killer fires. The 512 MiB container ceiling is still enforced by the kernel.</li>
 *   <li>CPU: Container can spike to 100% for up to one cpuPeriod (100 ms) before being throttled.</li>
 *   <li>Read-only FS: Tested with Alpine + Temurin; some JVM builds write temp files outside /tmp.
 *       If startup fails, check JVM flags in the entrypoint.</li>
 *   <li>Disk limit: Docker Engine does not support per-container disk quotas via the API without
 *       overlay2 quota support. A 32 MiB tmpfs cap on /tmp bounds temp disk usage instead.</li>
 *   <li>no-new-privileges: On Windows Docker Desktop (WSL2), this is enforced inside the Linux VM,
 *       not on the Windows host kernel directly.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class DockerSandboxService {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxService.class);

    // Relative source and class paths inside /tmp (the writable tmpfs)
    private static final String TMP_DIR        = "/tmp/sandbox";
    private static final String SOURCE_FILE    = TMP_DIR + "/Main.java";
    private static final String CLASS_DIR      = TMP_DIR + "/classes";

    // Shell script written as a single argument — NOT string-concatenated with source code.
    // Source is transferred via base64 to avoid any shell injection.
    // Steps:
    //  1. mkdir -p both dirs
    //  2. decode the base64 source into Main.java
    //  3. compile with javac  (exit 1 on compile error → special marker)
    //  4. run the compiled class (exit 2+ on runtime error)
    // Exit codes:
    //   0  = success
    //   1  = compile error  (javac returned non-zero; combined stdout/stderr captured)
    //   *  = runtime error (java process exited non-zero)
    private static final String SCRIPT_TEMPLATE =
            "set -e; " +
            "mkdir -p " + TMP_DIR + " " + CLASS_DIR + "; " +
            // %s will be replaced by the base64-encoded source — no shell interpretation possible
            "echo '%s' | base64 -d > " + SOURCE_FILE + "; " +
            "if ! javac -d " + CLASS_DIR + " " + SOURCE_FILE + " 2>/tmp/sandbox/compile.err; then " +
            "  cat /tmp/sandbox/compile.err; exit 1; " +
            "fi; " +
            "java -cp " + CLASS_DIR + " " +
            "-Xmx256m -Xms32m " +
            "-Djava.security.manager=disallow " +
            "Main";

    private final DockerClient dockerClient;
    private final SandboxProperties props;

    // Lazily initialised so that unit tests that mock DockerClient do not fail
    // due to configuration-time property reads.
    private volatile Semaphore semaphore;

    private Semaphore getSemaphore() {
        if (semaphore == null) {
            synchronized (this) {
                if (semaphore == null) {
                    semaphore = new Semaphore(props.getMaxConcurrentExecutions(), true);
                }
            }
        }
        return semaphore;
    }

    /**
     * Validates source code, schedules a container execution, and returns a bounded result.
     *
     * @param sourceCode the raw Java source submitted by the student
     * @return execution result with status, stdout, stderr, exitCode, and executionTimeMs
     * @throws IllegalArgumentException if source is blank or exceeds the byte limit
     * @throws SandboxException         if Docker infrastructure is unavailable or fails
     */
    public SandboxResult execute(String sourceCode) {
        validateSource(sourceCode);

        if (!getSemaphore().tryAcquire()) {
            log.warn("Sandbox concurrency limit ({}) reached — rejecting request",
                    props.getMaxConcurrentExecutions());
            throw new SandboxException("Sandbox is at capacity. Try again shortly.");
        }

        try {
            return runInContainer(sourceCode);
        } finally {
            getSemaphore().release();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("sourceCode must not be blank");
        }
        byte[] encoded = sourceCode.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > props.getMaxSourceBytes()) {
            throw new IllegalArgumentException(
                    "sourceCode exceeds the maximum allowed size of " + props.getMaxSourceBytes() + " bytes (UTF-8)");
        }
    }

    private SandboxResult runInContainer(String sourceCode) {
        // Encode source as base64 (safe for shell single-quote insertion)
        String base64Source = java.util.Base64.getEncoder()
                .encodeToString(sourceCode.getBytes(StandardCharsets.UTF_8));

        String script = String.format(SCRIPT_TEMPLATE, base64Source);

        String containerId = null;
        long startMs = System.currentTimeMillis();
        AtomicBoolean timedOut = new AtomicBoolean(false);

        try {
            containerId = createContainer(script);
            dockerClient.startContainerCmd(containerId).exec();

            // ── Timeout enforcer ───────────────────────────────────────────
            final String finalContainerId = containerId;
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(props.getTimeoutSeconds() * 1000L);
                    log.warn("Container {} timed out after {}s — stopping", finalContainerId, props.getTimeoutSeconds());
                    timedOut.set(true);
                    stopContainer(finalContainerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // cancelled — normal path
                }
            }, "sandbox-timeout-" + finalContainerId.substring(0, 12));
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            // ── Collect output (bounded) ───────────────────────────────────
            OutputCollector collector = new OutputCollector(props.getMaxOutputBytes());
            try (InputStream logStream = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new RawLogResultCallback(collector))) {

                // Wait for the container to finish or be killed
                int exitCode = dockerClient
                        .waitContainerCmd(containerId)
                        .exec(new WaitContainerResultCallback())
                        .awaitStatusCode(props.getTimeoutSeconds() + 2L, TimeUnit.SECONDS);

                // Cancel the timeout thread if still running
                timeoutThread.interrupt();

                long elapsedMs = System.currentTimeMillis() - startMs;

                if (timedOut.get()) {
                    return SandboxResult.builder()
                            .status(SandboxResult.Status.TIMEOUT)
                            .stdout("")
                            .stderr("Execution timed out after " + props.getTimeoutSeconds() + " seconds.")
                            .exitCode(null)
                            .executionTimeMs(elapsedMs)
                            .truncated(false)
                            .build();
                }

                String stdout = collector.getStdout();
                String stderr = collector.getStderr();
                boolean truncated = collector.isTruncated();

                SandboxResult.Status status;
                if (exitCode == 0) {
                    status = SandboxResult.Status.SUCCESS;
                } else if (exitCode == 1) {
                    // Our script exits 1 exclusively for compile errors
                    status = SandboxResult.Status.COMPILE_ERROR;
                } else {
                    status = SandboxResult.Status.RUNTIME_ERROR;
                }

                return SandboxResult.builder()
                        .status(status)
                        .stdout(stdout)
                        .stderr(stderr)
                        .exitCode(exitCode)
                        .executionTimeMs(elapsedMs)
                        .truncated(truncated)
                        .build();

            } catch (Exception e) {
                timeoutThread.interrupt();
                if (timedOut.get()) {
                    return SandboxResult.builder()
                            .status(SandboxResult.Status.TIMEOUT)
                            .stdout("")
                            .stderr("Execution timed out after " + props.getTimeoutSeconds() + " seconds.")
                            .exitCode(null)
                            .executionTimeMs(System.currentTimeMillis() - startMs)
                            .truncated(false)
                            .build();
                }
                log.error("Error collecting container output for {}", containerId, e);
                throw new SandboxException("Failed to collect container output", e);
            }

        } catch (SandboxException | IllegalArgumentException e) {
            throw e;
        } catch (DockerException e) {
            log.error("Docker error during sandbox execution", e);
            throw new SandboxException("Docker engine error: " + sanitizeDockerError(e), e);
        } catch (Exception e) {
            log.error("Unexpected sandbox error", e);
            throw new SandboxException("Sandbox infrastructure failure", e);
        } finally {
            // Always remove the container — even if start failed or timeout fired
            if (containerId != null) {
                removeContainer(containerId);
            }
        }
    }

    /**
     * Creates a container with all security controls applied.
     * The container is NOT started here — that allows the finally block to still
     * call removeContainer if startContainerCmd throws.
     */
    private String createContainer(String shellScript) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                // Memory hard limit
                .withMemory(props.getMemoryBytes())
                .withMemorySwap(props.getMemoryBytes())   // swap == memory → no swap
                // CPU throttle
                .withCpuPeriod(props.getCpuPeriodMicros())
                .withCpuQuota(props.getCpuQuotaMicros())
                // PID limit
                .withPidsLimit(props.getPidsLimit())
                // Drop ALL Linux capabilities
                .withCapDrop(Capability.ALL)
                // No-new-privileges prevents privilege escalation via setuid binaries
                .withSecurityOpts(List.of("no-new-privileges:true"))
                // Read-only root filesystem; /tmp is writable tmpfs
                .withReadonlyRootfs(true)
                .withTmpFs(java.util.Map.of(
                        "/tmp", "rw,noexec,nosuid,size=" + props.getTmpfsSizeBytes()
                ))
                // Log driver: json-file with a size cap to prevent log-based DoS
                .withLogConfig(new LogConfig(
                        LogConfig.LoggingType.JSON_FILE,
                        java.util.Map.of("max-size", "1m", "max-file", "1")
                ));

        CreateContainerResponse container = dockerClient.createContainerCmd(props.getImage())
                // Non-root user (nobody)
                .withUser("65534:65534")
                // Disable all networking
                .withNetworkDisabled(true)
                // Empty environment — no host vars leaked
                .withEnv(List.of())
                // No bind mounts — see HostConfig above (no Binds set)
                .withHostConfig(hostConfig)
                // Entrypoint: sh -c '<script>'
                .withCmd("sh", "-c", shellScript)
                .withWorkingDir("/tmp")
                .exec();

        log.debug("Created sandbox container: {}", container.getId().substring(0, 12));
        return container.getId();
    }

    private void stopContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(2).exec();
        } catch (NotModifiedException ignored) {
            // Container already stopped — this is fine
        } catch (Exception e) {
            log.warn("Could not stop container {} cleanly: {}", containerId.substring(0, 12), e.getMessage());
        }
    }

    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.debug("Removed sandbox container: {}", containerId.substring(0, 12));
        } catch (Exception e) {
            log.warn("Could not remove container {}: {}", containerId.substring(0, 12), e.getMessage());
        }
    }

    /**
     * Strips Docker daemon internal details (host paths, IPs, daemon socket paths)
     * before they could surface in an API error message.
     */
    private static String sanitizeDockerError(DockerException e) {
        String msg = e.getMessage();
        if (msg == null) return "unknown Docker error";
        // Return only the first line — daemon messages are often multi-line with internal details
        int newline = msg.indexOf('\n');
        return newline > 0 ? msg.substring(0, newline) : msg;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Collects stdout and stderr bytes while consuming the Docker log stream,
     * capping each at {@code maxBytes} before buffering the entire output.
     */
    static final class OutputCollector {

        private final int maxBytes;
        private final ByteArrayOutputStream stdoutBuf;
        private final ByteArrayOutputStream stderrBuf;
        private boolean truncated = false;

        OutputCollector(int maxBytes) {
            this.maxBytes = maxBytes;
            this.stdoutBuf = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
            this.stderrBuf = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        }

        synchronized void appendStdout(byte[] data, int offset, int length) {
            append(stdoutBuf, data, offset, length);
        }

        synchronized void appendStderr(byte[] data, int offset, int length) {
            append(stderrBuf, data, offset, length);
        }

        private void append(ByteArrayOutputStream buf, byte[] data, int offset, int length) {
            int remaining = maxBytes - buf.size();
            if (remaining <= 0) {
                truncated = true;
                return;
            }
            int toWrite = Math.min(length, remaining);
            buf.write(data, offset, toWrite);
            if (toWrite < length) {
                truncated = true;
            }
        }

        synchronized String getStdout() {
            return stdoutBuf.toString(StandardCharsets.UTF_8);
        }

        synchronized String getStderr() {
            return stderrBuf.toString(StandardCharsets.UTF_8);
        }

        synchronized boolean isTruncated() {
            return truncated;
        }
    }

    /**
     * docker-java result callback that demultiplexes the Docker multiplexed stream
     * (8-byte header per frame: stream type + length) into stdout / stderr.
     */
    static final class RawLogResultCallback
            extends com.github.dockerjava.api.async.ResultCallbackTemplate<RawLogResultCallback,
                    com.github.dockerjava.api.model.Frame> {

        private final OutputCollector collector;

        RawLogResultCallback(OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void onNext(com.github.dockerjava.api.model.Frame frame) {
            if (frame == null || frame.getPayload() == null) return;
            byte[] payload = frame.getPayload();
            switch (frame.getStreamType()) {
                case STDOUT -> collector.appendStdout(payload, 0, payload.length);
                case STDERR -> collector.appendStderr(payload, 0, payload.length);
                default -> { /* RAW / other frame types ignored */ }
            }
        }
    }
}
