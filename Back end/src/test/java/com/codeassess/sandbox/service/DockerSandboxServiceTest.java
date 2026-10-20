package com.codeassess.sandbox.service;

import com.codeassess.sandbox.config.SandboxProperties;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService.OutputCollector;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DockerSandboxService}.
 *
 * <p>All Docker API interactions are mocked. These tests verify:
 * <ul>
 *   <li>Input validation (blank source, oversized UTF-8 source)</li>
 *   <li>Status mapping (SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT)</li>
 *   <li>Output bounding (OutputCollector truncation)</li>
 *   <li>Concurrency limit enforcement</li>
 *   <li>Container cleanup on every exit path</li>
 *   <li>Docker infrastructure error handling</li>
 * </ul>
 *
 * <p><strong>These tests do NOT verify real Docker Engine controls</strong>
 * (network isolation, capability drop, non-root user, memory/CPU limits).
 * Those controls require a real Docker Engine and are documented in
 * {@code DockerSandboxIntegrationTest}.
 */
class DockerSandboxServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    private DockerClient dockerClient;
    private SandboxProperties props;
    private DockerSandboxService service;

    // Mock command objects returned by the DockerClient fluent API
    private CreateContainerCmd createCmd;
    private StartContainerCmd startCmd;
    private LogContainerCmd logCmd;
    private WaitContainerCmd waitCmd;
    private StopContainerCmd stopCmd;
    private RemoveContainerCmd removeCmd;
    private CopyArchiveToContainerCmd copyArchiveCmd;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        props = new SandboxProperties();
        props.setTimeoutSeconds(10);
        props.setMaxConcurrentExecutions(4);
        props.setMemoryBytes(512L * 1024 * 1024);
        props.setCpuPeriodMicros(100_000L);
        props.setCpuQuotaMicros(50_000L);
        props.setPidsLimit(50L);
        props.setMaxSourceBytes(65_536);
        props.setMaxOutputBytes(65_536);
        props.setTmpfsSizeBytes(32L * 1024 * 1024);
        props.setImage("eclipse-temurin:21-jdk-alpine");

        service = new DockerSandboxService(dockerClient, props);

        // Wire common mock chain
        createCmd = mock(CreateContainerCmd.class, RETURNS_SELF);
        startCmd  = mock(StartContainerCmd.class);
        logCmd    = mock(LogContainerCmd.class, RETURNS_SELF);
        waitCmd   = mock(WaitContainerCmd.class);
        stopCmd   = mock(StopContainerCmd.class, RETURNS_SELF);
        removeCmd = mock(RemoveContainerCmd.class, RETURNS_SELF);

        copyArchiveCmd = mock(CopyArchiveToContainerCmd.class, RETURNS_SELF);

        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startCmd);
        when(dockerClient.copyArchiveToContainerCmd(anyString())).thenReturn(copyArchiveCmd);
        when(dockerClient.logContainerCmd(anyString())).thenReturn(logCmd);
        when(dockerClient.waitContainerCmd(anyString())).thenReturn(waitCmd);
        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeCmd);

        CreateContainerResponse containerResp = mock(CreateContainerResponse.class);
        when(containerResp.getId()).thenReturn("abc123def456abc123def456abc123def456abc1");
        when(createCmd.exec()).thenReturn(containerResp);

        // Default: container exits 0
        WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
        when(waitCmd.exec(any())).thenReturn(waitCallback);
        when(waitCallback.awaitStatusCode(anyLong(), any())).thenReturn(0);

        // logContainerCmd returns a mock stream that completes immediately
        when(logCmd.exec(any())).thenAnswer(inv -> {
            // do nothing — no frames emitted
            return inv.getArgument(0);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. Input validation
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void execute_blankSource_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.execute("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void execute_nullSource_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.execute(null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void execute_oversizedUtf8Source_throwsIllegalArgument() {
        // 65537 bytes of ASCII = 65537 UTF-8 bytes → over the 65536 limit
        String oversized = "A".repeat(65_537);
        assertThatThrownBy(() -> service.execute(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65536 bytes");

        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void execute_exactlyAtLimit_doesNotThrow() {
        // 65536 bytes is exactly at the limit — should proceed to Docker
        String atLimit = "A".repeat(65_536);
        // We don't care about the result — just that validation passes
        assertThatCode(() -> service.execute(atLimit)).doesNotThrowAnyException();
    }

    @Test
    void execute_multibyteSizeEnforcedInBytes_notChars() {
        // A string of 32,769 × U+0400 (Cyrillic А) = 65,538 UTF-8 bytes.
        // That is 32,769 chars but 65,538 bytes — must be rejected.
        String multibyte = "\u0400".repeat(32_769); // each is 2 bytes in UTF-8
        assertThatThrownBy(() -> service.execute(multibyte))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytes");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. Status mapping
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void execute_exitCode0_returnsSuccess() {
        setExitCode(0);
        SandboxResult result = service.execute("public class Main {}");
        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.SUCCESS);
        assertThat(result.getExitCode()).isEqualTo(0);
    }

    @Test
    void execute_exitCode1_returnsCompileError() {
        setExitCode(1);
        SandboxResult result = service.execute("this is not valid java");
        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.COMPILE_ERROR);
        assertThat(result.getExitCode()).isEqualTo(1);
    }

    @Test
    void execute_exitCode2_returnsRuntimeError() {
        setExitCode(2);
        SandboxResult result = service.execute("public class Main { public static void main(String[] a){throw new RuntimeException();}}");
        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.RUNTIME_ERROR);
        assertThat(result.getExitCode()).isEqualTo(2);
    }

    @Test
    void execute_exitCode137_returnsRuntimeError() {
        // 137 = SIGKILL from OOM killer
        setExitCode(137);
        SandboxResult result = service.execute("public class Main {}");
        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.RUNTIME_ERROR);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. Container cleanup
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void execute_success_containerAlwaysRemoved() {
        setExitCode(0);
        service.execute("public class Main {}");
        verify(removeCmd).exec();
    }

    @Test
    void execute_compileError_containerAlwaysRemoved() {
        setExitCode(1);
        service.execute("not java");
        verify(removeCmd).exec();
    }

    @Test
    void execute_dockerExceptionOnStart_containerStillRemoved() {
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startCmd);
        doThrow(new DockerException("start failed", 500)).when(startCmd).exec();

        assertThatThrownBy(() -> service.execute("public class Main {}"))
                .isInstanceOf(SandboxException.class);

        // The container was created before start failed — it must still be removed
        verify(removeCmd).exec();
    }

    @Test
    void execute_dockerExceptionOnCreate_throwsSandboxException() {
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.exec()).thenThrow(new DockerException("image not found", 404));

        assertThatThrownBy(() -> service.execute("public class Main {}"))
                .isInstanceOf(SandboxException.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. Docker unavailable
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void execute_dockerUnavailable_throwsSandboxException() {
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.exec()).thenThrow(new DockerException("Connection refused", 500));

        assertThatThrownBy(() -> service.execute("public class Main {}"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Docker engine error");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. Concurrency limit
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void execute_concurrencyLimitReached_throwsSandboxException() {
        // Set limit to 0 to force immediate rejection
        props.setMaxConcurrentExecutions(0);
        // Re-create the service so the semaphore picks up the new limit
        DockerSandboxService svc = new DockerSandboxService(dockerClient, props);

        assertThatThrownBy(() -> svc.execute("public class Main {}"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("capacity");

        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. OutputCollector — unit tests (inner class, package-private)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void outputCollector_withinLimit_isNotTruncated() {
        OutputCollector col = new OutputCollector(100);
        col.appendStdout("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        assertThat(col.getStdout()).isEqualTo("hello");
        assertThat(col.isTruncated()).isFalse();
    }

    @Test
    void outputCollector_exceedsLimit_truncatesAndSetsFlag() {
        OutputCollector col = new OutputCollector(5);
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8); // 11 bytes
        col.appendStdout(data, 0, data.length);

        assertThat(col.getStdout()).isEqualTo("hello");
        assertThat(col.isTruncated()).isTrue();
    }

    @Test
    void outputCollector_stderrTruncated_setsFlag() {
        OutputCollector col = new OutputCollector(3);
        col.appendStderr("error message".getBytes(StandardCharsets.UTF_8), 0, 13);
        assertThat(col.getStderr()).isEqualTo("err");
        assertThat(col.isTruncated()).isTrue();
    }

    @Test
    void outputCollector_multipleAppends_stopWritingAfterLimit() {
        OutputCollector col = new OutputCollector(10);
        col.appendStdout("12345".getBytes(StandardCharsets.UTF_8), 0, 5);
        col.appendStdout("67890".getBytes(StandardCharsets.UTF_8), 0, 5);
        col.appendStdout("EXTRA".getBytes(StandardCharsets.UTF_8), 0, 5);
        assertThat(col.getStdout()).isEqualTo("1234567890");
        assertThat(col.isTruncated()).isTrue();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 7. Frame routing (RawLogResultCallback)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void rawLogCallback_routesStdoutAndStderrCorrectly() {
        OutputCollector col = new OutputCollector(1024);
        DockerSandboxService.RawLogResultCallback cb = new DockerSandboxService.RawLogResultCallback(col);

        cb.onNext(new Frame(StreamType.STDOUT, "hello".getBytes(StandardCharsets.UTF_8)));
        cb.onNext(new Frame(StreamType.STDERR, "error".getBytes(StandardCharsets.UTF_8)));

        assertThat(col.getStdout()).isEqualTo("hello");
        assertThat(col.getStderr()).isEqualTo("error");
    }

    @Test
    void rawLogCallback_nullFrame_ignoredSafely() {
        OutputCollector col = new OutputCollector(1024);
        DockerSandboxService.RawLogResultCallback cb = new DockerSandboxService.RawLogResultCallback(col);
        assertThatCode(() -> cb.onNext(null)).doesNotThrowAnyException();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 8. Public class name extraction
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void extractMainClassName_detectsPublicClass() {
        String src = "package com.example;\npublic class Solution {\n  public static void main(String[] a){}\n}";
        assertThat(DockerSandboxService.extractMainClassName(src)).isEqualTo("Solution");
    }

    @Test
    void extractMainClassName_detectsPublicFinalClass() {
        String src = "public final class Calculator {\n  public static void main(String[] a){}\n}";
        assertThat(DockerSandboxService.extractMainClassName(src)).isEqualTo("Calculator");
    }

    @Test
    void extractMainClassName_nonPublicClass_defaultsToMain() {
        String src = "class Helper {\n  public static void main(String[] a){}\n}";
        assertThat(DockerSandboxService.extractMainClassName(src)).isEqualTo("Main");
    }

    @Test
    void extractMainClassName_nullOrEmpty_defaultsToMain() {
        assertThat(DockerSandboxService.extractMainClassName(null)).isEqualTo("Main");
        assertThat(DockerSandboxService.extractMainClassName("   ")).isEqualTo("Main");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 9. Container Security Isolation Controls Verification
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void execute_appliesFullSecurityIsolationControls() {
        org.mockito.ArgumentCaptor<com.github.dockerjava.api.model.HostConfig> hostConfigCaptor =
                org.mockito.ArgumentCaptor.forClass(com.github.dockerjava.api.model.HostConfig.class);
        org.mockito.ArgumentCaptor<String> userCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Boolean> netDisabledCaptor =
                org.mockito.ArgumentCaptor.forClass(Boolean.class);

        service.execute("public class Solution { public static void main(String[] a){} }");

        verify(createCmd).withHostConfig(hostConfigCaptor.capture());
        verify(createCmd).withUser(userCaptor.capture());
        verify(createCmd).withNetworkDisabled(netDisabledCaptor.capture());

        com.github.dockerjava.api.model.HostConfig hc = hostConfigCaptor.getValue();
        assertThat(hc).isNotNull();
        assertThat(hc.getInit()).isTrue();
        assertThat(hc.getNetworkMode()).isEqualTo("none");
        assertThat(hc.getReadonlyRootfs()).isTrue();
        assertThat(hc.getCapDrop()).contains(com.github.dockerjava.api.model.Capability.ALL);
        assertThat(hc.getSecurityOpts()).contains("no-new-privileges:true");
        assertThat(hc.getTmpFs()).containsKey("/tmp");
        assertThat(hc.getUlimits()).anyMatch(u -> "core".equals(u.getName()) && u.getHard() == 0L);
        assertThat(userCaptor.getValue()).isEqualTo("65534:65534");
        assertThat(netDisabledCaptor.getValue()).isTrue();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 10. Multi-file workspace execution (executeWorkspace)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void executeWorkspace_validMultiFileWithEntryPoint_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path mainFile = tempDir.resolve("Main.java");
        Files.writeString(mainFile, "public class Main { public static void main(String[] args) {} }");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Main.java", mainFile, Files.size(mainFile), true)),
                Files.size(mainFile),
                "mock-sha256"
        );

        setExitCode(0);
        SandboxResult result = service.executeWorkspace(workspace, "Main");

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.SUCCESS);
        verify(dockerClient).copyArchiveToContainerCmd(anyString());
        verify(copyArchiveCmd).withTarInputStream(any());
        verify(copyArchiveCmd).exec();
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_noEntryPoint_returnsCompileOnlySuccess(@TempDir Path tempDir) throws IOException {
        Path helperFile = tempDir.resolve("Helper.java");
        Files.writeString(helperFile, "public class Helper {}");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Helper.java", helperFile, Files.size(helperFile), true)),
                Files.size(helperFile),
                "mock-sha256"
        );

        setExitCode(0);
        SandboxResult result = service.executeWorkspace(workspace, null);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.COMPILE_ONLY_SUCCESS);
        verify(dockerClient).copyArchiveToContainerCmd(anyString());
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_compileError_returnsCompileError(@TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("Bad.java");
        Files.writeString(badFile, "class Bad { syntax error");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Bad.java", badFile, Files.size(badFile), true)),
                Files.size(badFile),
                "mock-sha256"
        );

        setExitCode(1);
        SandboxResult result = service.executeWorkspace(workspace, "Bad");

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.COMPILE_ERROR);
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_runtimeError_returnsRuntimeError(@TempDir Path tempDir) throws IOException {
        Path mainFile = tempDir.resolve("Main.java");
        Files.writeString(mainFile, "public class Main { public static void main(String[] args) { throw new RuntimeException(); } }");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Main.java", mainFile, Files.size(mainFile), true)),
                Files.size(mainFile),
                "mock-sha256"
        );

        setExitCode(42);
        SandboxResult result = service.executeWorkspace(workspace, "Main");

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.RUNTIME_ERROR);
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_invalidEntryPointName_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path mainFile = tempDir.resolve("Main.java");
        Files.writeString(mainFile, "public class Main {}");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Main.java", mainFile, Files.size(mainFile), true)),
                Files.size(mainFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main; rm -rf /"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid entry point class name");

        // Verify container was never created
        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void executeWorkspace_workspaceExceedsTmpfsCapacity_returnsCapacityExceeded(@TempDir Path tempDir) {
        Path bigFile = tempDir.resolve("Big.java");
        // Workspace totalBytes > 16 MiB
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Big.java", bigFile, 17L * 1024 * 1024, true)),
                17L * 1024 * 1024,
                "mock-sha256"
        );

        SandboxResult result = service.executeWorkspace(workspace, "Big");

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.CAPACITY_EXCEEDED);
        assertThat(result.getStderr()).contains("exceeds maximum sandbox tmpfs threshold");
        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void executeWorkspace_archiveExceedsInlineTransferLimit_returnsCapacityExceeded(@TempDir Path tempDir) throws IOException {
        Path bigFile = tempDir.resolve("Big.java");
        // Write 95 KiB file content (exceeds MAX_INLINE_TAR_BYTES = 90 KiB)
        byte[] payload = new byte[95 * 1024];
        Files.write(bigFile, payload);

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Big.java", bigFile, payload.length, true)),
                payload.length,
                "mock-sha256"
        );

        SandboxResult result = service.executeWorkspace(workspace, "Big");

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.CAPACITY_EXCEEDED);
        assertThat(result.getStderr()).contains("exceeds the inline container transfer limit");
        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void executeWorkspace_scriptContainsFailClosedExtractionCommand(@TempDir Path tempDir) throws IOException {
        Path mainFile = tempDir.resolve("Main.java");
        Files.writeString(mainFile, "public class Main {}");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Main.java", mainFile, Files.size(mainFile), true)),
                Files.size(mainFile),
                "mock-sha256"
        );

        setExitCode(0);
        service.executeWorkspace(workspace, "Main");

        org.mockito.ArgumentCaptor<String> scriptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(createCmd).withCmd(eq("sh"), eq("-c"), scriptCaptor.capture());
        String executedScript = scriptCaptor.getValue();
        assertThat(executedScript)
                .startsWith("set -e -o pipefail 2>/dev/null || set -e; if ! ( echo '")
                .contains("| base64 -d | tar -xf - -C /tmp ); then echo 'Failed to unpack workspace archive' >&2; exit 43; fi;");
    }

    @Test
    void executeWorkspace_dockerCopyArchiveFails_cleansUpAndThrowsSandboxException(@TempDir Path tempDir) throws IOException {
        Path mainFile = tempDir.resolve("Main.java");
        Files.writeString(mainFile, "public class Main {}");
        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("Main.java", mainFile, Files.size(mainFile), true)),
                Files.size(mainFile),
                "mock-sha256"
        );

        doThrow(new DockerException("copy failed", 500)).when(copyArchiveCmd).exec();

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Docker engine error");

        // Even though copy failed, container was created and MUST be removed
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_fileWithSpacesAndSpecialChars_handledSafely(@TempDir Path tempDir) throws IOException {
        Path subDir = Files.createDirectories(tempDir.resolve("pkg name"));
        Path sourceFile = subDir.resolve("My Class.java");
        Files.writeString(sourceFile, "package pkg; public class MyClass {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("pkg name/My Class.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        setExitCode(0);
        SandboxResult result = service.executeWorkspace(workspace, null);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.COMPILE_ONLY_SUCCESS);
        verify(copyArchiveCmd).exec();
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_unicodeFilename_acceptedAndHandledSafely(@TempDir Path tempDir) throws IOException {
        Path subDir = Files.createDirectories(tempDir.resolve("tiếngviệt"));
        Path sourceFile = subDir.resolve("BàiTập.java");
        Files.writeString(sourceFile, "package tiếngviệt; public class BàiTập {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("tiếngviệt/BàiTập.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        setExitCode(0);
        SandboxResult result = service.executeWorkspace(workspace, null);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.COMPILE_ONLY_SUCCESS);
        verify(copyArchiveCmd).exec();
        verify(removeCmd).exec();
    }

    @Test
    void executeWorkspace_filenameWithQuotes_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("pkg/\"Main\".java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported quote characters");
    }

    @Test
    void executeWorkspace_filenameWithBackslash_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("pkg\\Main.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains backslash");
    }

    @Test
    void executeWorkspace_filenameWithNewline_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("pkg/Main\n.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newline character");
    }

    @Test
    void executeWorkspace_filenameWithNullByte_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("pkg/Main\0.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null byte");
    }

    @Test
    void executeWorkspace_filenameWithShellMetacharacters_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("pkg/Main;rm -rf.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported or unsafe character");
    }

    @Test
    void executeWorkspace_filenameWithDirectoryTraversal_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(new ExtractedFile("../outside/Main.java", sourceFile, Files.size(sourceFile), true)),
                Files.size(sourceFile),
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directory traversal");
    }

    @Test
    void executeWorkspace_duplicateRelativePaths_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Main.java");
        Files.writeString(sourceFile, "public class Main {}");

        ExtractedWorkspace workspace = new ExtractedWorkspace(
                tempDir,
                List.of(
                        new ExtractedFile("pkg/Main.java", sourceFile, Files.size(sourceFile), true),
                        new ExtractedFile("pkg/Main.java", sourceFile, Files.size(sourceFile), true)
                ),
                Files.size(sourceFile) * 2,
                "mock-sha256"
        );

        assertThatThrownBy(() -> service.executeWorkspace(workspace, "Main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate file path");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setExitCode(int code) {
        WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
        when(waitCmd.exec(any())).thenReturn(waitCallback);
        when(waitCallback.awaitStatusCode(anyLong(), any())).thenReturn(code);
    }
}
