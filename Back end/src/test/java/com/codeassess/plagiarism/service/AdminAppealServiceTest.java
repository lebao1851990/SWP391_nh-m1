package com.codeassess.plagiarism.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
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
}
