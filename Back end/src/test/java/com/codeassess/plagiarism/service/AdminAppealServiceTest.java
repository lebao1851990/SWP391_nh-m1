package com.codeassess.plagiarism.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AdminAppealServiceTest {

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private GradeAuditLogRepository gradeAuditLogRepository;

    @Mock
    private com.codeassess.submission.service.SubmissionStorageService submissionStorageService;

    @InjectMocks
    private AdminAppealService adminAppealService;

    private User admin;
    private User student;
    private Exam exam;
    private Submission submission;
    private Appeal appeal;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        admin = User.builder().id("admin-1").email("admin@test.com").fullName("System Admin").role(Role.ADMIN).build();
        student = User.builder().id("student-1").email("student@test.com").fullName("Student One").role(Role.STUDENT).build();

        CourseClass courseClass = CourseClass.builder().id("class-1").build();
        exam = Exam.builder().id("exam-1").title("Final Exam").courseClass(courseClass).build();

        submission = Submission.builder()
                .id("sub-1")
                .exam(exam)
                .student(student)
                .status("GRADED")
                .totalScore(BigDecimal.valueOf(6.0))
                .overrideScore(null)
                .build();

        appeal = Appeal.builder()
                .id(12345L)
                .submission(submission)
                .student(student)
                .basis("Grading error")
                .reason("My code passes all requirements")
                .status("PENDING")
                .build();
    }

    @Test
    public void getAdminAppeals_ReturnsAllList() {
        when(appealRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.singletonList(appeal));

        List<AppealResponseDto> result = adminAppealService.getAdminAppeals(null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
        verify(appealRepository, times(1)).findAllByOrderByCreatedAtDesc();
    }

    @Test
    public void getAdminAppeals_FilteredByStatus() {
        when(appealRepository.findByStatusOrderByCreatedAtDesc("PENDING")).thenReturn(Collections.singletonList(appeal));

        List<AppealResponseDto> result = adminAppealService.getAdminAppeals("PENDING");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(appealRepository, times(1)).findByStatusOrderByCreatedAtDesc("PENDING");
    }

    @Test
    public void resolveAppeal_ApprovedWithAdjustedScore_Success() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Admin approved after code inspection")
                .adjustedScore(BigDecimal.valueOf(9.0))
                .build();

        String opaqueId = OpaqueIdEncoder.encode(12345L);

        AppealResponseDto result = adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertEquals(BigDecimal.valueOf(9.0), submission.getOverrideScore());
        assertEquals("Admin approved after code inspection", result.getReviewerNotes());

        verify(submissionRepository, times(1)).save(submission);
        verify(gradeAuditLogRepository, times(1)).save(any());
        verify(appealRepository, times(1)).save(appeal);
    }

    @Test
    public void resolveAppeal_Rejected_ScoreUnchanged() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("REJECTED")
                .reviewerNotes("Insufficient justification")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(12345L);

        AppealResponseDto result = adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertNotNull(result);
        assertEquals("REJECTED", result.getStatus());
        assertNull(submission.getOverrideScore());

        verify(submissionRepository, never()).save(any());
        verify(gradeAuditLogRepository, times(1)).save(any());
        verify(appealRepository, times(1)).save(appeal);
    }

    @Test
    public void resolveAppeal_AlreadyResolved_ThrowsConflict() {
        appeal.setStatus("APPROVED");
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Try again")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(12345L);

        assertThrows(ConflictException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @Test
    public void resolveAppeal_InvalidScore_ThrowsIllegalArgument() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Invalid score")
                .adjustedScore(BigDecimal.valueOf(15.0))
                .build();

        String opaqueId = OpaqueIdEncoder.encode(12345L);

        assertThrows(IllegalArgumentException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @Test
    public void resolveAppeal_NotFound_ThrowsNotFound() {
        when(appealRepository.findById(99999L)).thenReturn(Optional.empty());

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Not found")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(99999L);

        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @Test
    public void getAppealSubmissionArchive_Success() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        byte[] expectedBytes = new byte[]{1, 2, 3};
        when(submissionStorageService.hasZip("sub-1")).thenReturn(true);
        when(submissionStorageService.getZipBytes("sub-1")).thenReturn(expectedBytes);

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        byte[] result = adminAppealService.getAppealSubmissionArchive(opaqueId, admin);

        assertNotNull(result);
        assertArrayEquals(expectedBytes, result);
        verify(gradeAuditLogRepository, times(1)).save(any());
    }

    @Test
    public void getAppealSubmissionArchive_FallbackToSourceCode() {
        submission.setSourceCode("public class Test {}");
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(submissionStorageService.hasZip("sub-1")).thenReturn(false);
        byte[] generatedZip = new byte[]{4, 5, 6};
        when(submissionStorageService.createZipFromSourceCode(any(), eq("public class Test {}"))).thenReturn(generatedZip);

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        byte[] result = adminAppealService.getAppealSubmissionArchive(opaqueId, admin);

        assertNotNull(result);
        assertArrayEquals(generatedZip, result);
    }

    @Test
    public void getAdminAppeal_Success() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        AppealResponseDto result = adminAppealService.getAdminAppeal(opaqueId);

        assertNotNull(result);
        assertEquals(opaqueId, result.getId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("Grading error", result.getBasis());
    }

    @Test
    public void getAdminAppeal_InvalidOpaqueId_ThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.getAdminAppeal("invalid-hash"));
    }

    @Test
    public void getAdminAppeal_NotFound_ThrowsResourceNotFound() {
        when(appealRepository.findById(99999L)).thenReturn(Optional.empty());
        String opaqueId = OpaqueIdEncoder.encode(99999L);

        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.getAdminAppeal(opaqueId));
    }

    @Test
    public void resolveAppeal_InvalidOpaqueId_ThrowsResourceNotFound() {
        AppealResolveRequestDto dto = AppealResolveRequestDto.builder().decision("APPROVED").reviewerNotes("Notes").build();
        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.resolveAppeal("bad-id", dto, admin));
    }

    @Test
    public void resolveAppeal_MissingReviewerNotes_ThrowsIllegalArgument() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        String opaqueId = OpaqueIdEncoder.encode(12345L);

        AppealResolveRequestDto dto1 = AppealResolveRequestDto.builder().decision("APPROVED").reviewerNotes(null).build();
        assertThrows(IllegalArgumentException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto1, admin));

        AppealResolveRequestDto dto2 = AppealResolveRequestDto.builder().decision("APPROVED").reviewerNotes("   ").build();
        assertThrows(IllegalArgumentException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto2, admin));
    }

    @Test
    public void resolveAppeal_InvalidDecision_ThrowsIllegalArgument() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        String opaqueId = OpaqueIdEncoder.encode(12345L);

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder().decision("MAYBE").reviewerNotes("Unclear").build();
        assertThrows(IllegalArgumentException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @Test
    public void resolveAppeal_Approved_WithoutAdjustedScore_KeepsScoreAndAudits() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Approved without score change")
                .adjustedScore(null)
                .build();

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        AppealResponseDto result = adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertNull(submission.getOverrideScore());
        verify(submissionRepository, never()).save(any());
        verify(gradeAuditLogRepository, times(1)).save(any());
    }

    @Test
    public void resolveAppeal_Approved_TransitionsPendingSubmissionToGraded() {
        submission.setStatus("PENDING");
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Score upgraded from pending")
                .adjustedScore(BigDecimal.valueOf(8.0))
                .build();

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertEquals("GRADED", submission.getStatus());
        assertEquals(BigDecimal.valueOf(8.0), submission.getOverrideScore());
        verify(submissionRepository, times(1)).save(submission);
    }

    @Test
    public void resolveAppeal_NegativeScore_ThrowsIllegalArgument() {
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        String opaqueId = OpaqueIdEncoder.encode(12345L);

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Negative score test")
                .adjustedScore(BigDecimal.valueOf(-2.5))
                .build();

        assertThrows(IllegalArgumentException.class, () -> adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @Test
    public void getAppealSubmissionArchive_InvalidOpaqueId_ThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.getAppealSubmissionArchive("bad-hash", admin));
    }

    @Test
    public void getAppealSubmissionArchive_MissingSubmission_ThrowsResourceNotFound() {
        appeal.setSubmission(null);
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        String opaqueId = OpaqueIdEncoder.encode(12345L);

        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.getAppealSubmissionArchive(opaqueId, admin));
    }

    @Test
    public void getAppealSubmissionArchive_NoZipAndNoSource_ThrowsResourceNotFound() {
        submission.setSourceCode(null);
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(submissionStorageService.hasZip("sub-1")).thenReturn(false);
        String opaqueId = OpaqueIdEncoder.encode(12345L);

        assertThrows(ResourceNotFoundException.class, () -> adminAppealService.getAppealSubmissionArchive(opaqueId, admin));
    }

    @Test
    public void getAppealSubmissionArchive_AuditsWithEmailWhenFullNameNull() {
        User adminNoName = User.builder().id("admin-2").email("admin2@fpt.edu.vn").fullName(null).build();
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(submissionStorageService.hasZip("sub-1")).thenReturn(true);
        when(submissionStorageService.getZipBytes("sub-1")).thenReturn(new byte[]{1, 2, 3});

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        byte[] bytes = adminAppealService.getAppealSubmissionArchive(opaqueId, adminNoName);

        assertNotNull(bytes);
        verify(gradeAuditLogRepository, times(1)).save(any());
    }

    @Test
    public void getSubmissionArchiveFilename_AllBranches() {
        // 1. Invalid opaqueId
        assertEquals("submission.zip", adminAppealService.getSubmissionArchiveFilename("bad-id"));

        // 2. Appeal not found
        when(appealRepository.findById(99999L)).thenReturn(Optional.empty());
        assertEquals("submission.zip", adminAppealService.getSubmissionArchiveFilename(OpaqueIdEncoder.encode(99999L)));

        // 3. Submission has originalFilename ending with .zip
        submission.setOriginalFilename("assignment_1.zip");
        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        assertEquals("assignment_1.zip", adminAppealService.getSubmissionArchiveFilename(OpaqueIdEncoder.encode(12345L)));

        // 4. Submission has originalFilename without .zip
        submission.setOriginalFilename("assignment_1");
        assertEquals("assignment_1.zip", adminAppealService.getSubmissionArchiveFilename(OpaqueIdEncoder.encode(12345L)));

        // 5. Submission has null originalFilename
        submission.setOriginalFilename(null);
        assertEquals("submission-sub-1.zip", adminAppealService.getSubmissionArchiveFilename(OpaqueIdEncoder.encode(12345L)));
    }

    @Test
    public void toDto_PopulatesReviewerFromAuditLogAndFileSizeFromSourceCode() {
        appeal.setStatus("APPROVED");
        submission.setSourceCode("public class Main { public static void main(String[] args) {} }");

        User reviewer = User.builder().fullName("Chief Reviewer").email("chief@fpt.edu.vn").build();
        GradeAuditLog auditLog = GradeAuditLog.builder().actor(reviewer).build();

        when(appealRepository.findById(12345L)).thenReturn(Optional.of(appeal));
        when(gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc("sub-1"))
                .thenReturn(List.of(auditLog));
        when(submissionStorageService.getZipSizeBytes("sub-1")).thenReturn(null);

        String opaqueId = OpaqueIdEncoder.encode(12345L);
        AppealResponseDto dto = adminAppealService.getAdminAppeal(opaqueId);

        assertNotNull(dto);
        assertEquals("Chief Reviewer", dto.getReviewerName());
        assertEquals("chief@fpt.edu.vn", dto.getReviewerEmail());
        assertNotNull(dto.getFileSize());
        assertTrue(Boolean.TRUE.equals(dto.getHasArchive()));
    }
}
