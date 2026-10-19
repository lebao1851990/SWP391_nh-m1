package com.codeassess.sandbox.controller;

import com.codeassess.sandbox.dto.request.SandboxExecuteRequest;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import com.codeassess.common.exception.GlobalExceptionHandler;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.security.SecurityConfig;
import com.codeassess.config.CorsConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link SandboxController}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Anonymous requests → 401</li>
 *   <li>TEACHER / ADMIN roles → 403</li>
 *   <li>STUDENT role → 200 with result body</li>
 *   <li>Blank sourceCode → 400</li>
 *   <li>Oversized source → 400 (checked in service, mapped by GlobalExceptionHandler)</li>
 *   <li>DockerSandboxService unavailable → 503</li>
 *   <li>COMPILE_ERROR result → 200 with status=COMPILE_ERROR</li>
 *   <li>TIMEOUT result → 200 with status=TIMEOUT</li>
 *   <li>Response does NOT create a Submission (DockerSandboxService is mocked; no DB call)</li>
 * </ul>
 *
 * <p>DockerSandboxService is mocked — no real Docker interaction.
 */
@WebMvcTest(SandboxController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class SandboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DockerSandboxService sandboxService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String ENDPOINT = "/api/student/sandbox/execute";

    private User studentUser;
    private User teacherUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        studentUser = User.builder()
                .id("student-001").email("s@test.com").passwordHash("h").role(Role.STUDENT).fullName("S").build();
        teacherUser = User.builder()
                .id("teacher-001").email("t@test.com").passwordHash("h").role(Role.TEACHER).fullName("T").build();
        adminUser   = User.builder()
                .id("admin-001").email("a@test.com").passwordHash("h").role(Role.ADMIN).fullName("A").build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void execute_anonymous_returns401() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main {}")))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Role enforcement
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void execute_teacher_returns403() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .with(user(teacherUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main {}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void execute_admin_returns403() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .with(user(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main {}")))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Valid student execution
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void execute_validStudent_success_returns200() throws Exception {
        Mockito.when(sandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .stdout("Hello, World!\n")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(312L)
                .truncated(false)
                .build());

        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main { public static void main(String[] a){System.out.println(\"Hello, World!\");}}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.stdout").value("Hello, World!\n"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void execute_compileError_returns200WithCompileErrorStatus() throws Exception {
        Mockito.when(sandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.COMPILE_ERROR)
                .stdout("")
                .stderr("Main.java:1: error: reached end of file while parsing\n")
                .exitCode(1)
                .executionTimeMs(210L)
                .truncated(false)
                .build());

        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("class Main {")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.exitCode").value(1));
    }

    @Test
    void execute_timeout_returns200WithTimeoutStatus() throws Exception {
        Mockito.when(sandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.TIMEOUT)
                .stdout("")
                .stderr("Execution timed out after 10 seconds.")
                .exitCode(null)
                .executionTimeMs(10_015L)
                .truncated(false)
                .build());

        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main { public static void main(String[] a){ while(true){} }}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TIMEOUT"))
                .andExpect(jsonPath("$.exitCode").doesNotExist());
    }

    @Test
    void execute_runtimeError_returns200WithRuntimeErrorStatus() throws Exception {
        Mockito.when(sandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.RUNTIME_ERROR)
                .stdout("")
                .stderr("Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n")
                .exitCode(1)
                .executionTimeMs(400L)
                .truncated(false)
                .build());

        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main { public static void main(String[] a){ int x=1/0; }}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNTIME_ERROR"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Input validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void execute_blankSourceCode_returns400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceCode\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void execute_missingSourceCode_returns400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void execute_oversizedSource_returns400() throws Exception {
        Mockito.when(sandboxService.execute(anyString()))
                .thenThrow(new IllegalArgumentException("sourceCode exceeds the maximum allowed size of 65536 bytes (UTF-8)"));

        String oversized = "A".repeat(65_537);
        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Infrastructure failure
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void execute_dockerUnavailable_returns503() throws Exception {
        Mockito.when(sandboxService.execute(anyString()))
                .thenThrow(new SandboxException("Docker engine error: Connection refused"));

        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main {}")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SANDBOX_UNAVAILABLE"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Security invariant — no submission created, no student ID injected
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void execute_doesNotExposeAnotherStudentsId() throws Exception {
        // The response contains no studentId field — the API contract does not leak it.
        Mockito.when(sandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .stdout("ok").stderr("").exitCode(0).executionTimeMs(1).truncated(false).build());

        mockMvc.perform(post(ENDPOINT)
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("public class Main {}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.submissionId").doesNotExist());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────

    private String body(String sourceCode) throws Exception {
        return objectMapper.writeValueAsString(new SandboxExecuteRequest(sourceCode));
    }
}
