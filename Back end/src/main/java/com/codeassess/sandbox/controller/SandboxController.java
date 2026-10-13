package com.codeassess.sandbox.controller;

import com.codeassess.sandbox.dto.request.SandboxExecuteRequest;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@code POST /api/student/sandbox/execute} — a stateless sandbox endpoint
 * that compiles and runs student-submitted Java code inside a hardened Docker container.
 *
 * <p>This endpoint deliberately does NOT:
 * <ul>
 *   <li>Create a {@code Submission} entity</li>
 *   <li>Publish a {@code SubmissionCreatedEvent}</li>
 *   <li>Trigger plagiarism analysis</li>
 *   <li>Equate execution success with a plagiarism verdict</li>
 * </ul>
 *
 * <p>It is restricted to authenticated {@code STUDENT} users only. Execution results
 * are returned inline; no state is written to the database.
 */
@RestController
@RequestMapping("/api/student/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final DockerSandboxService sandboxService;

    /**
     * Execute a Java program in an isolated Docker container.
     *
     * @param request contains the raw Java source code
     * @return 200 OK with a {@link SandboxResult}; the {@code status} field distinguishes
     *         SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, and INTERNAL_ERROR
     */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/execute")
    public ResponseEntity<SandboxResult> execute(
            @Valid @RequestBody SandboxExecuteRequest request) {

        SandboxResult result = sandboxService.execute(request.getSourceCode());
        return ResponseEntity.ok(result);
    }
}
