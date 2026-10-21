b# CODEASSESS — STAGE 2B-3C.1: UTF-8 BYTE-ACCURATE OUTPUT BOUNDING

## Objective

Fix the remaining output-persistence risk identified by the Stage 2B-3C Verification Audit before approving Stage 4.

### Scope

Inspect and modify only the necessary code and tests, primarily:
- `SubmissionPersistenceService.java`
- `StudentSubmissionServiceTest.java` or the existing appropriate output-persistence test class
- Relevant sandbox/output configuration if required

### Requirements

1. Replace character-count-based truncation with UTF-8 byte-accurate truncation for `stdout`, `stderr`, and `compile_output`.
2. Ensure the final persisted string, including any truncation marker, never exceeds 65,535 bytes when encoded as UTF-8.
3. Never cut a UTF-8 multibyte character or UTF-16 surrogate pair in the middle.
4. Handle null, empty, ASCII, Vietnamese, CJK, and supplementary Unicode/emoji input.
5. If truncation occurs, preserve a clear truncation marker when the byte budget allows it.
6. Avoid creating an unnecessarily large temporary byte array if the existing output collector already provides a bounded result.
7. Preserve existing API behavior, database schema, submission statuses, transaction boundaries, and single-file submission compatibility.
8. Do not modify or execute Flyway migrations.

### Tests

Add focused tests verifying:
- ASCII output below and above the byte limit.
- Vietnamese/CJK output whose character count is below the limit but encoded UTF-8 size exceeds it.
- Emoji and supplementary Unicode characters near the truncation boundary.
- The truncation marker is included without exceeding the limit.
- The result is valid Unicode and re-encoding it as UTF-8 never exceeds 65,535 bytes.
- Null and empty values retain the intended behavior.

Use assertions on `result.getBytes(StandardCharsets.UTF_8).length`, not only `String.length()`.

### Validation and Safety

- Run the new focused tests.
- Run `StudentSubmissionServiceTest` and `TxBoundaryVerificationTest`.
- Run the relevant broader test suite if practical.
- Report actual Surefire test counts and results.
- Do not modify `plagiarism_system`.
- Do not commit, push, deploy, or begin Stage 4.

### Deliverable

Provide the changed-file list, implementation explanation, test results, and any remaining risk. Stop after validation.package com.codeassess.admin.controller;

import com.codeassess.admin.dto.response.AdminDashboardMetricsDto;
import com.codeassess.admin.service.AdminMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public AdminDashboardMetricsDto getMetrics() {
        return adminMetricsService.getMetrics();
    }
}
