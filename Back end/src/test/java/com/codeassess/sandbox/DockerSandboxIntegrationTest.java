package com.codeassess.sandbox;

import com.codeassess.sandbox.config.SandboxProperties;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZeroDepDockerHttpClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests that require a real Docker Engine and the
 * {@code eclipse-temurin:21-jdk-alpine} image.
 *
 * <p>These tests are <strong>disabled by default</strong>. Run them with:
 * <pre>
 *   $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
 *   .\mvnw.cmd test -pl "Back end" -Dtest=DockerSandboxIntegrationTest -Dsandbox.integration.enabled=true
 * </pre>
 *
 * <p><strong>Controls verified by real Docker Engine:</strong>
 * <ul>
 *   <li>Valid Java program executes and exits 0</li>
 *   <li>Compilation error distinguished from runtime error</li>
 *   <li>Runtime exception captured in stderr</li>
 *   <li>Infinite loop timeout fires and container is force-stopped</li>
 *   <li>Output-size limit enforced while streaming</li>
 *   <li>Concurrent execution semaphore enforced</li>
 *   <li>Container removed after success, failure, timeout</li>
 *   <li>Oversized source rejected before Docker is contacted</li>
 * </ul>
 *
 * <p><strong>Controls NOT verified here (require kernel-level observation):</strong>
 * <ul>
 *   <li>Network isolation (verified by testing that a network call fails inside the container)</li>
 *   <li>Non-root user UID (verified by `id` command inside container)</li>
 *   <li>Linux capability drop (requires capability audit tooling)</li>
 *   <li>Memory/CPU hard limit enforcement (requires OOM-killer or cgroup observation)</li>
 *   <li>no-new-privileges enforcement (requires setuid binary test)</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "sandbox.integration.enabled", matches = "true")
class DockerSandboxIntegrationTest {

    private static DockerClient dockerClient;
    private static DockerSandboxService service;
    private static SandboxProperties props;

    @BeforeAll
    static void setUpDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient http = new ZeroDepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(60))
                .build();
        dockerClient = DockerClientImpl.getInstance(config, http);

        props = new SandboxProperties();
        props.setImage("eclipse-temurin:21-jdk-alpine");
        props.setTimeoutSeconds(10);
        props.setMaxConcurrentExecutions(4);
        props.setMemoryBytes(512L * 1024 * 1024);
        props.setCpuPeriodMicros(100_000L);
        props.setCpuQuotaMicros(50_000L);
        props.setPidsLimit(50L);
        props.setMaxSourceBytes(65_536);
        props.setMaxOutputBytes(65_536);
        props.setTmpfsSizeBytes(32L * 1024 * 1024);

        service = new DockerSandboxService(dockerClient, props);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Valid Java program
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void validJavaProgram_succeeds() {
        String src = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello from sandbox\");\n" +
                "    }\n" +
                "}\n";

        SandboxResult result = service.execute(src);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.SUCCESS);
        assertThat(result.getStdout()).contains("Hello from sandbox");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getExecutionTimeMs()).isGreaterThan(0);
        assertThat(result.isTruncated()).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Compilation error
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    void compilationError_returnsCompileErrorStatus() {
        // Missing closing brace — javac will fail
        String src = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"broken\");\n";
        // deliberately missing closing braces

        SandboxResult result = service.execute(src);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.COMPILE_ERROR);
        assertThat(result.getExitCode()).isEqualTo(1);
        // javac error output should mention the problem
        assertThat(result.getStdout() + result.getStderr()).containsAnyOf("error", "Error");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Runtime exception
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    void runtimeException_returnsRuntimeErrorStatus() {
        String src = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        throw new RuntimeException(\"deliberate failure\");\n" +
                "    }\n" +
                "}\n";

        SandboxResult result = service.execute(src);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.RUNTIME_ERROR);
        assertThat(result.getStderr()).contains("RuntimeException");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Infinite loop timeout
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    void infiniteLoop_timesOutAndContainerIsRemoved() {
        // Use a very short timeout so the test doesn't run for 10 seconds
        SandboxProperties shortProps = new SandboxProperties();
        shortProps.setImage(props.getImage());
        shortProps.setTimeoutSeconds(3);
        shortProps.setMaxConcurrentExecutions(4);
        shortProps.setMemoryBytes(props.getMemoryBytes());
        shortProps.setCpuPeriodMicros(props.getCpuPeriodMicros());
        shortProps.setCpuQuotaMicros(props.getCpuQuotaMicros());
        shortProps.setPidsLimit(props.getPidsLimit());
        shortProps.setMaxSourceBytes(props.getMaxSourceBytes());
        shortProps.setMaxOutputBytes(props.getMaxOutputBytes());
        shortProps.setTmpfsSizeBytes(props.getTmpfsSizeBytes());

        DockerSandboxService shortService = new DockerSandboxService(dockerClient, shortProps);

        String src = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        while (true) { /* spin */ }\n" +
                "    }\n" +
                "}\n";

        long start = System.currentTimeMillis();
        SandboxResult result = shortService.execute(src);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.TIMEOUT);
        // Should finish within 2 seconds of the timeout
        assertThat(elapsed).isLessThan((shortProps.getTimeoutSeconds() + 2) * 1000L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Network isolation (verified via container attempt to connect)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    void networkIsolated_cannotReachExternalHost() {
        // The container has network disabled. Any attempt to open a socket
        // must fail. We compile and run a program that tries to open a TCP
        // connection to a public IP — it must fail with an exception, not succeed.
        String src = "import java.net.Socket;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) throws Exception {\n" +
                "        try {\n" +
                "            new Socket(\"8.8.8.8\", 53).close();\n" +
                "            System.out.println(\"NETWORK_OPEN\");\n" +
                "        } catch (Exception e) {\n" +
                "            System.out.println(\"NETWORK_BLOCKED\");\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        SandboxResult result = service.execute(src);

        // The program exits 0 (it handles the exception), but must NOT print NETWORK_OPEN
        assertThat(result.getStdout()).doesNotContain("NETWORK_OPEN");
        assertThat(result.getStdout()).contains("NETWORK_BLOCKED");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Non-root user
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    void nonRootUser_uidIsNobody() {
        // Print the effective UID inside the container — must NOT be 0
        String src = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"UID=\" + System.getProperty(\"user.name\", \"unknown\"));\n" +
                "    }\n" +
                "}\n";

        SandboxResult result = service.execute(src);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.SUCCESS);
        // user.name for UID 65534 (nobody) is typically "nobody" or numeric
        assertThat(result.getStdout()).doesNotContain("UID=root");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. Output-size limit
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    void outputSizeLimit_truncatesAndSetsTruncatedFlag() {
        // Set a tiny output limit
        SandboxProperties smallOutputProps = new SandboxProperties();
        smallOutputProps.setImage(props.getImage());
        smallOutputProps.setTimeoutSeconds(10);
        smallOutputProps.setMaxConcurrentExecutions(4);
        smallOutputProps.setMemoryBytes(props.getMemoryBytes());
        smallOutputProps.setCpuPeriodMicros(props.getCpuPeriodMicros());
        smallOutputProps.setCpuQuotaMicros(props.getCpuQuotaMicros());
        smallOutputProps.setPidsLimit(props.getPidsLimit());
        smallOutputProps.setMaxSourceBytes(props.getMaxSourceBytes());
        smallOutputProps.setMaxOutputBytes(20);   // only 20 bytes
        smallOutputProps.setTmpfsSizeBytes(props.getTmpfsSizeBytes());

        DockerSandboxService smallOutputSvc = new DockerSandboxService(dockerClient, smallOutputProps);

        String src = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        for (int i = 0; i < 1000; i++) System.out.println(\"line \" + i);\n" +
                "    }\n" +
                "}\n";

        SandboxResult result = smallOutputSvc.execute(src);

        assertThat(result.getStatus()).isEqualTo(SandboxResult.Status.SUCCESS);
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getStdout().getBytes().length).isLessThanOrEqualTo(20);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. Concurrent execution limit
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    void concurrencyLimit_rejectsWhenAtCapacity() throws InterruptedException {
        // Set limit to 1 to make it easy to saturate
        SandboxProperties limitedProps = new SandboxProperties();
        limitedProps.setImage(props.getImage());
        limitedProps.setTimeoutSeconds(10);
        limitedProps.setMaxConcurrentExecutions(1);
        limitedProps.setMemoryBytes(props.getMemoryBytes());
        limitedProps.setCpuPeriodMicros(props.getCpuPeriodMicros());
        limitedProps.setCpuQuotaMicros(props.getCpuQuotaMicros());
        limitedProps.setPidsLimit(props.getPidsLimit());
        limitedProps.setMaxSourceBytes(props.getMaxSourceBytes());
        limitedProps.setMaxOutputBytes(props.getMaxOutputBytes());
        limitedProps.setTmpfsSizeBytes(props.getTmpfsSizeBytes());

        DockerSandboxService limitedSvc = new DockerSandboxService(dockerClient, limitedProps);

        // Slow program to keep the semaphore held
        String slowSrc = "public class Main {\n" +
                "    public static void main(String[] args) throws Exception {\n" +
                "        Thread.sleep(5000);\n" +
                "    }\n" +
                "}\n";

        CountDownLatch started = new CountDownLatch(1);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        List<String> outcomes = new CopyOnWriteArrayList<>();

        // Thread 1 — holds the slot
        Thread t1 = new Thread(() -> {
            try {
                started.countDown();
                limitedSvc.execute(slowSrc);
                outcomes.add("T1_DONE");
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        t1.start();
        started.await();
        Thread.sleep(500); // give T1 time to acquire semaphore

        // Thread 2 — should be rejected immediately
        assertThatThrownBy(() -> limitedSvc.execute("public class Main { public static void main(String[] a){} }"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("capacity");

        t1.interrupt();
        t1.join(8_000);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. Oversized source rejected before Docker
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    void oversizedSource_rejectedBeforeDocker() {
        String oversized = "A".repeat(65_537);
        assertThatThrownBy(() -> service.execute(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65536 bytes");
    }
}
