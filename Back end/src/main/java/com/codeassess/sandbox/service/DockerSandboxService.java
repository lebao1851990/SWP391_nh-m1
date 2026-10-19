package com.codeassess.sandbox.service;

import com.codeassess.sandbox.config.SandboxProperties;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.Ulimit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchItem;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchSuiteResult;
import com.codeassess.testcase.dto.sandbox.TestCaseExecutionItemResult;
import com.codeassess.testcase.util.OutputNormalizer;

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
public class DockerSandboxService {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxService.class);

    // Relative source and class paths inside /tmp (the writable tmpfs)
    private static final String TMP_DIR        = "/tmp/sandbox";
    private static final String CLASS_DIR      = TMP_DIR + "/classes";

    private static final java.util.regex.Pattern PUBLIC_CLASS_PATTERN =
            java.util.regex.Pattern.compile("\\bpublic\\s+(?:final\\s+)?class\\s+([A-Za-z0-9_$]+)");

    static String extractMainClassName(String sourceCode) {
        if (sourceCode != null) {
            java.util.regex.Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (name != null && name.matches("^[A-Za-z0-9_$]+$")) {
                    return name;
                }
            }
        }
        return "Main";
    }

    // Shell script written as a single argument — NOT string-concatenated with source code.
    // Source is transferred via base64 to avoid any shell injection.
    // Steps:
    //  1. mkdir -p both dirs
    //  2. decode the base64 source into <ClassName>.java
    //  3. compile with javac  (exit 1 on compile error → special marker)
    //  4. run the compiled class (exit 2+ on runtime error)
    // Exit codes:
    //   0  = success
    //   1  = compile error  (javac returned non-zero; compiler output routed to stderr)
    //   *  = runtime error (java process exited non-zero)
    private static final String SCRIPT_TEMPLATE =
            "set -e; " +
            "mkdir -p " + TMP_DIR + " " + CLASS_DIR + "; " +
            // %1$s is the class name, %2$s is the base64-encoded source
            "echo '%2$s' | base64 -d > " + TMP_DIR + "/%1$s.java; " +
            "if ! javac -d " + CLASS_DIR + " " + TMP_DIR + "/%1$s.java 2>/tmp/sandbox/compile.err; then " +
            "  cat /tmp/sandbox/compile.err >&2; exit 1; " +
            "fi; " +
            "set +e; " +
            "java -cp " + CLASS_DIR + " " +
            "-Xmx256m -Xms32m " +
            "-Djava.security.manager=disallow " +
            "%1$s; " +
            "ec=$?; " +
            "[ $ec -eq 1 ] && exit 2 || exit $ec";

    // Multi-file execution script: compiles all .java files listed in /tmp/sandbox/sources.txt
    // %s is replaced by the validated entry-point class name
    private static final String MULTI_FILE_SCRIPT_TEMPLATE =
            "set -e; " +
            "mkdir -p " + CLASS_DIR + "; " +
            "if ! javac -d " + CLASS_DIR + " @/tmp/sandbox/sources.txt 2>/tmp/sandbox/compile.err; then " +
            "  cat /tmp/sandbox/compile.err; exit 1; " +
            "fi; " +
            "set +e; " +
            "java -cp " + CLASS_DIR + " " +
            "-Xmx256m -Xms32m " +
            "-Djava.security.manager=disallow " +
            "%s; " +
            "ec=$?; " +
            "[ $ec -eq 1 ] && exit 2 || exit $ec";

    // Multi-file compile-only script (when no executable main method is executed)
    private static final String MULTI_FILE_COMPILE_ONLY_SCRIPT =
            "set -e; " +
            "mkdir -p " + CLASS_DIR + "; " +
            "if ! javac -d " + CLASS_DIR + " @/tmp/sandbox/sources.txt 2>/tmp/sandbox/compile.err; then " +
            "  cat /tmp/sandbox/compile.err; exit 1; " +
            "fi; " +
            "exit 0";

    // Conservative limit: max 16 MiB for multi-file workspace inside 32 MiB tmpfs
    private static final long MAX_WORKSPACE_BYTES_FOR_TMPFS = 16L * 1024 * 1024;

    private final DockerClient dockerClient;
    private final SandboxProperties props;

    public DockerSandboxService(DockerClient dockerClient, SandboxProperties props) {
        this.dockerClient = dockerClient;
        this.props = props;
    }

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

    /**
     * Executes a multi-file extracted workspace inside an isolated, hardened Docker container.
     *
     * @param workspace        safely extracted submission workspace
     * @param entryPointClass  optional fully qualified or simple main class name; if null/empty, compiles only
     * @return {@link SandboxResult} capturing compilation, execution stdout/stderr, and duration
     * @throws IllegalArgumentException if workspace is null/empty or entry-point name is invalid
     * @throws SandboxException         if Docker infrastructure fails
     */
    public SandboxResult executeWorkspace(ExtractedWorkspace workspace, String entryPointClass) {
        if (workspace == null || workspace.files() == null || workspace.files().isEmpty()) {
            throw new IllegalArgumentException("Workspace cannot be null or empty");
        }

        validateWorkspaceFiles(workspace);

        boolean hasJava = workspace.files().stream().anyMatch(ExtractedFile::isJavaSource);
        if (!hasJava) {
            throw new IllegalArgumentException("Workspace contains no Java source files to compile");
        }

        if (workspace.totalExtractedBytes() > MAX_WORKSPACE_BYTES_FOR_TMPFS) {
            log.warn("Workspace size ({} bytes) exceeds sandbox tmpfs threshold ({} bytes)",
                    workspace.totalExtractedBytes(), MAX_WORKSPACE_BYTES_FOR_TMPFS);
            return SandboxResult.builder()
                    .status(SandboxResult.Status.CAPACITY_EXCEEDED)
                    .stdout("")
                    .stderr("Workspace total extracted size (" + workspace.totalExtractedBytes()
                            + " bytes) exceeds maximum sandbox tmpfs threshold of "
                            + MAX_WORKSPACE_BYTES_FOR_TMPFS + " bytes (32 MiB tmpfs capacity)")
                    .exitCode(null)
                    .executionTimeMs(0)
                    .truncated(false)
                    .build();
        }

        if (entryPointClass != null && !entryPointClass.isBlank()) {
            String trimmed = entryPointClass.trim();
            if (!trimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$")) {
                throw new IllegalArgumentException("Invalid entry point class name: " + entryPointClass);
            }
        }

        if (!getSemaphore().tryAcquire()) {
            log.warn("Sandbox concurrency limit ({}) reached — rejecting request",
                    props.getMaxConcurrentExecutions());
            throw new SandboxException("Sandbox is at capacity. Try again shortly.");
        }

        try {
            return runWorkspaceInContainer(workspace, entryPointClass != null ? entryPointClass.trim() : null);
        } finally {
            getSemaphore().release();
        }
    }

    /**
     * Executes single-file Java source code against a batch of test cases inside an isolated Docker container.
     * Compiles once; if compile fails, returns compiled=false with compilation error.
     * If compile succeeds, runs each test case sequentially with input redirected from stdin,
     * enforcing individual per-test timeouts with /usr/bin/timeout.
     *
     * @param sourceCode raw Java source code
     * @param testCases list of test cases with input, expected output, and timeout
     * @return test suite execution results
     */
    public TestCaseBatchSuiteResult executeTestCaseSuite(String sourceCode, List<TestCaseBatchItem> testCases) {
        validateSource(sourceCode);
        if (testCases == null || testCases.isEmpty()) {
            throw new IllegalArgumentException("testCases list must not be empty");
        }

        if (!getSemaphore().tryAcquire()) {
            log.warn("Sandbox concurrency limit ({}) reached — rejecting request",
                    props.getMaxConcurrentExecutions());
            throw new SandboxException("Sandbox is at capacity. Try again shortly.");
        }

        try {
            return runTestCaseSuiteInContainer(sourceCode, testCases);
        } finally {
            getSemaphore().release();
        }
    }

    private TestCaseBatchSuiteResult runTestCaseSuiteInContainer(String sourceCode, List<TestCaseBatchItem> testCases) {
        String base64Source = Base64.getEncoder().encodeToString(sourceCode.getBytes(StandardCharsets.UTF_8));
        String runToken = UUID.randomUUID().toString().replace("-", "");
        String compileMarker = "TC_COMPILE_" + runToken;
        String tcStart = "TC_START_" + runToken + "_";
        String tcExit = "TC_EXIT_" + runToken + "_";
        String tcTime = "TC_TIME_" + runToken + "_";
        String tcOut = "TC_OUT_" + runToken + "_";
        String tcErr = "TC_ERR_" + runToken + "_";
        String tcEnd = "TC_END_" + runToken + "_";

        String mainClassName = extractMainClassName(sourceCode);
        String sourceFile = TMP_DIR + "/" + mainClassName + ".java";

        StringBuilder script = new StringBuilder();
        script.append("set -e; ");
        script.append("mkdir -p ").append(TMP_DIR).append(" ").append(CLASS_DIR).append("; ");
        script.append("echo '").append(base64Source).append("' | base64 -d > ").append(sourceFile).append("; ");
        script.append("if ! javac -d ").append(CLASS_DIR).append(" ").append(sourceFile).append(" 2>/tmp/sandbox/compile.err; then ");
        script.append("  echo '<<<").append(compileMarker).append(":FAILED>>>'; ");
        script.append("  cat /tmp/sandbox/compile.err; ");
        script.append("  exit 1; ");
        script.append("fi; ");
        script.append("echo '<<<").append(compileMarker).append(":SUCCESS>>>'; ");
        script.append("set +e; ");

        int totalTimeoutSeconds = 15; // 15s startup and compilation budget
        for (int i = 0; i < testCases.size(); i++) {
            TestCaseBatchItem tc = testCases.get(i);
            String input = tc.getInputData() != null ? tc.getInputData() : "";
            String base64Input = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
            int timeoutSec = tc.getTimeoutSeconds() > 0 ? tc.getTimeoutSeconds() : 5;
            totalTimeoutSeconds += timeoutSec;

            script.append("echo '").append(base64Input).append("' | base64 -d > /tmp/sandbox/in_").append(i).append(".txt; ");
            script.append("echo '<<<").append(tcStart).append(i).append(">>>'; ");
            script.append("START_TS=$(date +%s%3N 2>/dev/null || date +%s000); ");
            script.append("timeout ").append(timeoutSec).append(" java -cp ").append(CLASS_DIR)
                  .append(" -Xmx256m -Xms32m -Djava.security.manager=disallow ").append(mainClassName).append(" < /tmp/sandbox/in_").append(i)
                  .append(".txt > /tmp/sandbox/out_").append(i).append(".txt 2> /tmp/sandbox/err_").append(i).append(".txt; ");
            script.append("ec=$?; ");
            script.append("END_TS=$(date +%s%3N 2>/dev/null || date +%s000); ");
            script.append("DUR=$((END_TS - START_TS)); ");
            script.append("echo '<<<").append(tcExit).append(i).append(":'$ec'>>>'; ");
            script.append("echo '<<<").append(tcTime).append(i).append(":'$DUR'>>>'; ");
            script.append("echo '<<<").append(tcOut).append(i).append(">>>'; ");
            script.append("cat /tmp/sandbox/out_").append(i).append(".txt; ");
            script.append("echo '<<<").append(tcErr).append(i).append(">>>'; ");
            script.append("cat /tmp/sandbox/err_").append(i).append(".txt; ");
            script.append("echo '<<<").append(tcEnd).append(i).append(">>>'; ");
        }
        script.append("exit 0;");

        String containerId = null;
        long startMs = System.currentTimeMillis();
        AtomicBoolean timedOut = new AtomicBoolean(false);

        try {
            containerId = createContainer(script.toString());
            dockerClient.startContainerCmd(containerId).exec();

            final String finalContainerId = containerId;
            final int maxWaitSec = Math.max(totalTimeoutSeconds, props.getTimeoutSeconds());
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(maxWaitSec * 1000L);
                    log.warn("Test suite container {} timed out after {}s — stopping", finalContainerId, maxWaitSec);
                    timedOut.set(true);
                    stopContainer(finalContainerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "sandbox-tc-timeout-" + finalContainerId.substring(0, 12));
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            OutputCollector collector = new OutputCollector(props.getMaxOutputBytes());
            try (RawLogResultCallback logCallback = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new RawLogResultCallback(collector))) {

                dockerClient.waitContainerCmd(containerId)
                        .exec(new WaitContainerResultCallback())
                        .awaitStatusCode(maxWaitSec + 2L, TimeUnit.SECONDS);

                try {
                    logCallback.awaitCompletion(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {}

                timeoutThread.interrupt();
                long totalElapsedMs = System.currentTimeMillis() - startMs;

                String fullLog = collector.getStdout() + "\n" + collector.getStderr();
                log.info("Full test suite container output for {}: [{}]", containerId, fullLog);

                // Check compile marker
                String failedMarker = "<<<" + compileMarker + ":FAILED>>>";
                String successMarker = "<<<" + compileMarker + ":SUCCESS>>>";

                if (fullLog.contains(failedMarker)) {
                    int idx = fullLog.indexOf(failedMarker);
                    String compileErr = fullLog.substring(idx + failedMarker.length()).trim();
                    List<TestCaseExecutionItemResult> items = new ArrayList<>();
                    for (TestCaseBatchItem tc : testCases) {
                        items.add(TestCaseExecutionItemResult.builder()
                                .testCaseId(tc.getId())
                                .testCaseName(tc.getName())
                                .isHidden(tc.getIsHidden())
                                .status("COMPILE_ERROR")
                                .errorMessage(compileErr)
                                .actualOutput("")
                                .expectedOutput(tc.getExpectedOutput())
                                .pointsAwarded(BigDecimal.ZERO)
                                .maxPoints(tc.getPoints())
                                .executionTimeMs(0L)
                                .exitCode(1)
                                .build());
                    }
                    return TestCaseBatchSuiteResult.builder()
                            .compiled(false)
                            .compileOutput(compileErr)
                            .itemResults(items)
                            .totalDurationMs(totalElapsedMs)
                            .build();
                }

                boolean compiled = fullLog.contains(successMarker);
                List<TestCaseExecutionItemResult> items = new ArrayList<>();

                for (int i = 0; i < testCases.size(); i++) {
                    TestCaseBatchItem tc = testCases.get(i);
                    String startTag = "<<<" + tcStart + i + ">>>";
                    String exitTagPrefix = "<<<" + tcExit + i + ":";
                    String timeTagPrefix = "<<<" + tcTime + i + ":";
                    String outTag = "<<<" + tcOut + i + ">>>";
                    String errTag = "<<<" + tcErr + i + ">>>";
                    String endTag = "<<<" + tcEnd + i + ">>>";

                    if (!fullLog.contains(startTag) || !fullLog.contains(endTag)) {
                        items.add(TestCaseExecutionItemResult.builder()
                                .testCaseId(tc.getId())
                                .testCaseName(tc.getName())
                                .isHidden(tc.getIsHidden())
                                .status(timedOut.get() ? "TIMEOUT" : "NOT_EXECUTED")
                                .errorMessage(timedOut.get() ? "Execution timed out" : "Process terminated prematurely")
                                .actualOutput("")
                                .expectedOutput(tc.getExpectedOutput())
                                .pointsAwarded(BigDecimal.ZERO)
                                .maxPoints(tc.getPoints())
                                .executionTimeMs(0L)
                                .exitCode(null)
                                .build());
                        continue;
                    }

                    // Extract exit code
                    Integer exitCode = null;
                    int exitStart = fullLog.indexOf(exitTagPrefix);
                    if (exitStart >= 0) {
                        int exitEnd = fullLog.indexOf(">>>", exitStart);
                        if (exitEnd > exitStart) {
                            String codeStr = fullLog.substring(exitStart + exitTagPrefix.length(), exitEnd).trim();
                            try {
                                exitCode = Integer.parseInt(codeStr);
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    // Extract time ms
                    Long execDurationMs = null;
                    int timeStart = fullLog.indexOf(timeTagPrefix);
                    if (timeStart >= 0) {
                        int timeEnd = fullLog.indexOf(">>>", timeStart);
                        if (timeEnd > timeStart) {
                            String timeStr = fullLog.substring(timeStart + timeTagPrefix.length(), timeEnd).trim();
                            try {
                                execDurationMs = Long.parseLong(timeStr);
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    // Extract stdout
                    String stdout = "";
                    int outStart = fullLog.indexOf(outTag);
                    int errStart = fullLog.indexOf(errTag);
                    if (outStart >= 0 && errStart > outStart) {
                        stdout = fullLog.substring(outStart + outTag.length(), errStart);
                        if (stdout.startsWith("\r\n")) {
                            stdout = stdout.substring(2);
                        } else if (stdout.startsWith("\n")) {
                            stdout = stdout.substring(1);
                        }
                    }

                    // Extract stderr
                    String stderr = "";
                    int endStart = fullLog.indexOf(endTag);
                    if (errStart >= 0 && endStart > errStart) {
                        stderr = fullLog.substring(errStart + errTag.length(), endStart);
                        if (stderr.startsWith("\r\n")) {
                            stderr = stderr.substring(2);
                        } else if (stderr.startsWith("\n")) {
                            stderr = stderr.substring(1);
                        }
                    }

                    String status;
                    BigDecimal pointsAwarded = BigDecimal.ZERO;
                    String errorMessage = null;

                    if (exitCode != null && (exitCode == 124 || exitCode == 137 || exitCode == 143)) {
                        status = "TIMEOUT";
                        errorMessage = "Execution timed out (exceeded " + tc.getTimeoutSeconds() + "s limit)";
                    } else if (exitCode != null && exitCode != 0) {
                        status = "RUNTIME_ERROR";
                        errorMessage = stderr.trim();
                    } else {
                        boolean matches = OutputNormalizer.matches(stdout, tc.getExpectedOutput());
                        if (matches) {
                            status = "PASSED";
                            pointsAwarded = tc.getPoints() != null ? tc.getPoints() : BigDecimal.valueOf(10.00);
                        } else {
                            status = "FAILED";
                        }
                    }

                    items.add(TestCaseExecutionItemResult.builder()
                            .testCaseId(tc.getId())
                            .testCaseName(tc.getName())
                            .isHidden(tc.getIsHidden())
                            .status(status)
                            .actualOutput(stdout)
                            .expectedOutput(tc.getExpectedOutput())
                            .pointsAwarded(pointsAwarded)
                            .maxPoints(tc.getPoints())
                            .executionTimeMs(execDurationMs)
                            .exitCode(exitCode)
                            .errorMessage(errorMessage)
                            .build());
                }

                return TestCaseBatchSuiteResult.builder()
                        .compiled(compiled)
                        .compileOutput(compiled ? "Compilation successful" : "Failed")
                        .itemResults(items)
                        .totalDurationMs(totalElapsedMs)
                        .build();

            } catch (Exception e) {
                timeoutThread.interrupt();
                log.error("Error collecting container test suite output for {}", containerId, e);
                throw new SandboxException("Failed to collect container test suite output", e);
            }
        } catch (SandboxException | IllegalArgumentException e) {
            throw e;
        } catch (DockerException e) {
            log.error("Docker error during sandbox test suite execution", e);
            throw new SandboxException("Docker engine error: " + sanitizeDockerError(e), e);
        } catch (Exception e) {
            log.error("Unexpected sandbox error during test suite execution", e);
            throw new SandboxException("Sandbox infrastructure failure", e);
        } finally {
            if (containerId != null) {
                removeContainer(containerId);
            }
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

        String mainClassName = extractMainClassName(sourceCode);
        String script = String.format(SCRIPT_TEMPLATE, mainClassName, base64Source);

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
            try (RawLogResultCallback logCallback = dockerClient.logContainerCmd(containerId)
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
                boolean isInterrupted = timedOut.get()
                        || Thread.currentThread().isInterrupted()
                        || e instanceof InterruptedException
                        || (e.getCause() instanceof InterruptedException);
                if (isInterrupted) {
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

    // Bounded size for inline Base64 TAR fallback to fit safely within Linux kernel MAX_ARG_STRLEN (128 KiB)
    private static final int MAX_INLINE_TAR_BYTES = 90 * 1024;

    private SandboxResult runWorkspaceInContainer(ExtractedWorkspace workspace, String entryPointClass) {
        byte[] tarBytes;
        try {
            tarBytes = createWorkspaceTar(workspace);
        } catch (IOException e) {
            throw new SandboxException("Failed to create workspace archive", e);
        }
        return runWorkspaceInContainerWithTar(tarBytes, entryPointClass);
    }

    /**
     * Executes an in-memory TAR workspace payload inside the isolated Docker container.
     * Visible for testing to verify fail-closed behavior on corrupted/malformed archive bytes.
     */
    public SandboxResult runWorkspaceInContainerWithTar(byte[] tarBytes, String entryPointClass) {
        if (tarBytes.length > MAX_INLINE_TAR_BYTES) {
            log.warn("Workspace archive size ({} bytes) exceeds inline transfer limit ({} bytes)",
                    tarBytes.length, MAX_INLINE_TAR_BYTES);
            return SandboxResult.builder()
                    .status(SandboxResult.Status.CAPACITY_EXCEEDED)
                    .stdout("")
                    .stderr("Workspace archive size (" + tarBytes.length
                            + " bytes) exceeds the inline container transfer limit of "
                            + MAX_INLINE_TAR_BYTES + " bytes")
                    .exitCode(null)
                    .executionTimeMs(0)
                    .truncated(false)
                    .build();
        }

        String base64Tar = Base64.getEncoder().encodeToString(tarBytes);
        return runWorkspaceInContainerInternal(tarBytes, base64Tar, entryPointClass);
    }

    /**
     * Executes a raw base64 TAR string inside the isolated container.
     * Visible for testing to verify fail-closed behavior on corrupted/invalid Base64 payloads.
     */
    public SandboxResult runWorkspaceInContainerWithBase64Tar(String base64Tar, String entryPointClass) {
        return runWorkspaceInContainerInternal(null, base64Tar, entryPointClass);
    }

    private SandboxResult runWorkspaceInContainerInternal(byte[] tarBytes, String base64Tar, String entryPointClass) {
        // Prepend inline base64 TAR extraction into /tmp to guarantee safe unpack inside tmpfs
        // while preserving strict ReadonlyRootfs=true (which Docker Engine daemon otherwise blocks in copyArchive).
        // Uses 'set -e -o pipefail' (supported by BusyBox ash in Alpine) so that if EITHER base64 decode
        // or tar extraction fails, the pipeline exits with non-zero and triggers the fail-closed handler (exit 43),
        // preventing partial or corrupted workspace files from proceeding to javac or java.
        String unpackPrefix = "set -e -o pipefail 2>/dev/null || set -e; " +
                "if ! ( echo '" + base64Tar + "' | base64 -d | tar -xf - -C /tmp ); then " +
                "echo 'Failed to unpack workspace archive' >&2; exit 43; " +
                "fi; ";
        String script = (entryPointClass != null && !entryPointClass.isBlank())
                ? unpackPrefix + String.format(MULTI_FILE_SCRIPT_TEMPLATE, entryPointClass)
                : unpackPrefix + MULTI_FILE_COMPILE_ONLY_SCRIPT;

        String containerId = null;
        long startMs = System.currentTimeMillis();
        AtomicBoolean timedOut = new AtomicBoolean(false);

        try {
            containerId = createContainer(script);

            // Copy workspace TAR archive directly into container tmpfs (/tmp) if raw TAR is available.
            // Note: Docker Engine daemon intentionally rejects PUT /containers/{id}/archive
            // when container.HostConfig.ReadonlyRootfs is true (moby #35749).
            // The try-catch ensures compatibility with environments/mocks where copyArchive succeeds
            // without weakening read-only rootfs security controls.
            if (tarBytes != null && tarBytes.length > 0) {
                try {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(tarBytes)) {
                        dockerClient.copyArchiveToContainerCmd(containerId)
                                .withTarInputStream(bais)
                                .withRemotePath("/tmp")
                                .exec();
                    }
                } catch (BadRequestException e) {
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (msg.contains("read-only") || msg.contains("readonly")) {
                        log.debug("Docker Engine archive copy skipped due to ReadonlyRootfs enforcement: {}", e.getMessage());
                    } else {
                        throw e;
                    }
                }
            }
            dockerClient.startContainerCmd(containerId).exec();

            // Timeout enforcer
            final String finalContainerId = containerId;
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(props.getTimeoutSeconds() * 1000L);
                    log.warn("Multi-file container {} timed out after {}s — stopping", finalContainerId, props.getTimeoutSeconds());
                    timedOut.set(true);
                    stopContainer(finalContainerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "sandbox-timeout-" + finalContainerId.substring(0, Math.min(12, finalContainerId.length())));
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            // Collect bounded output
            OutputCollector collector = new OutputCollector(props.getMaxOutputBytes());
            try (RawLogResultCallback logCallback = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new RawLogResultCallback(collector))) {

                int exitCode = dockerClient
                        .waitContainerCmd(containerId)
                        .exec(new WaitContainerResultCallback())
                        .awaitStatusCode(props.getTimeoutSeconds() + 2L, TimeUnit.SECONDS);

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
                    if (entryPointClass == null || entryPointClass.isBlank()) {
                        status = SandboxResult.Status.COMPILE_ONLY_SUCCESS;
                        if (stdout.isEmpty()) {
                            stdout = "Compilation successful. No main method executed.";
                        }
                    } else {
                        status = SandboxResult.Status.SUCCESS;
                    }
                } else if (exitCode == 1) {
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
                log.error("Error collecting multi-file container output for {}", containerId, e);
                throw new SandboxException("Failed to collect container output", e);
            }

        } catch (SandboxException | IllegalArgumentException e) {
            throw e;
        } catch (DockerException e) {
            log.error("Docker error during multi-file sandbox execution", e);
            throw new SandboxException("Docker engine error: " + sanitizeDockerError(e), e);
        } catch (Exception e) {
            log.error("Unexpected multi-file sandbox error", e);
            throw new SandboxException("Sandbox infrastructure failure", e);
        } finally {
            if (containerId != null) {
                removeContainer(containerId);
            }
        }
    }

    private void validateWorkspaceFiles(ExtractedWorkspace workspace) {
        Set<String> seenPaths = new HashSet<>();
        for (ExtractedFile file : workspace.files()) {
            String path = file.relativePath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Workspace contains a file with an empty or blank relative path");
            }
            if (path.contains("\0")) {
                throw new IllegalArgumentException("Workspace file path contains null byte: " + path);
            }
            if (path.contains("\n") || path.contains("\r")) {
                throw new IllegalArgumentException("Workspace file path contains newline character: " + path);
            }
            if (path.contains("\"") || path.contains("'")) {
                throw new IllegalArgumentException("Workspace file path contains unsupported quote characters: " + path);
            }
            if (path.contains("\\")) {
                throw new IllegalArgumentException("Workspace file path contains backslash: " + path);
            }
            if (path.startsWith("/")) {
                throw new IllegalArgumentException("Workspace file path must not be absolute: " + path);
            }
            for (String segment : path.split("/")) {
                if ("..".equals(segment.trim())) {
                    throw new IllegalArgumentException("Workspace file path contains directory traversal: " + path);
                }
            }
            for (char ch : path.toCharArray()) {
                if (Character.isISOControl(ch) || ch == ';' || ch == '`' || ch == '|' || ch == '&' || ch == '<' || ch == '>') {
                    throw new IllegalArgumentException("Workspace file path contains unsupported or unsafe character '" + ch + "': " + path);
                }
            }
            if (!seenPaths.add(path)) {
                throw new IllegalArgumentException("Workspace contains duplicate file path: " + path);
            }
        }
    }

    private byte[] createWorkspaceTar(ExtractedWorkspace workspace) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(baos)) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            // Pre-register directories to ensure explicit 0755 directory entries in TAR
            Set<String> createdDirs = new HashSet<>();
            TarArchiveEntry sandboxDir = new TarArchiveEntry("sandbox/");
            sandboxDir.setMode(0755);
            taos.putArchiveEntry(sandboxDir);
            taos.closeArchiveEntry();
            createdDirs.add("sandbox/");

            TarArchiveEntry baseSrcDir = new TarArchiveEntry("sandbox/src/");
            baseSrcDir.setMode(0755);
            taos.putArchiveEntry(baseSrcDir);
            taos.closeArchiveEntry();
            createdDirs.add("sandbox/src/");

            StringBuilder sourcesTxt = new StringBuilder();
            for (ExtractedFile file : workspace.files()) {
                String normalizedPath = file.relativePath().replace('\\', '/');

                // Explicitly register parent directories if nested
                int lastSlash = normalizedPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    String[] segments = normalizedPath.substring(0, lastSlash).split("/");
                    StringBuilder currentPath = new StringBuilder("sandbox/src/");
                    for (String seg : segments) {
                        currentPath.append(seg).append("/");
                        String dirName = currentPath.toString();
                        if (createdDirs.add(dirName)) {
                            TarArchiveEntry dirEntry = new TarArchiveEntry(dirName);
                            dirEntry.setMode(0755);
                            taos.putArchiveEntry(dirEntry);
                            taos.closeArchiveEntry();
                        }
                    }
                }

                String tarPath = "sandbox/src/" + normalizedPath;
                byte[] fileBytes = Files.readAllBytes(file.absolutePath());

                TarArchiveEntry entry = new TarArchiveEntry(tarPath);
                entry.setSize(fileBytes.length);
                entry.setMode(0644);
                taos.putArchiveEntry(entry);
                taos.write(fileBytes);
                taos.closeArchiveEntry();

                if (file.isJavaSource()) {
                    String containerPath = "/tmp/sandbox/src/" + normalizedPath;
                    sourcesTxt.append("\"").append(containerPath).append("\"\n");
                }
            }

            byte[] sourcesBytes = sourcesTxt.toString().getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry sourcesEntry = new TarArchiveEntry("sandbox/sources.txt");
            sourcesEntry.setSize(sourcesBytes.length);
            sourcesEntry.setMode(0644);
            taos.putArchiveEntry(sourcesEntry);
            taos.write(sourcesBytes);
            taos.closeArchiveEntry();
        }
        return baos.toByteArray();
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
                // Init process reaps orphan child processes and forwards signals
                .withInit(true)
                // Network isolation mode
                .withNetworkMode("none")
                // Prevent core dumps containing sensitive process memory
                .withUlimits(List.of(new Ulimit("core", 0L, 0L)))
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
            dockerClient.stopContainerCmd(containerId).withTimeout(1).exec();
        } catch (NotModifiedException ignored) {
            // Container already stopped — this is fine
        } catch (Exception e) {
            String shortId = containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "unknown";
            log.warn("Could not stop container {} cleanly: {}", shortId, e.getMessage());
            try {
                dockerClient.killContainerCmd(containerId).exec();
            } catch (Exception ignored) {
                // Ignore kill failure if container already exited
            }
        }
    }

    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            String shortId = containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "unknown";
            log.debug("Removed sandbox container: {}", shortId);
        } catch (Exception e) {
            String shortId = containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "unknown";
            log.warn("Could not remove container {}: {}", shortId, e.getMessage());
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
