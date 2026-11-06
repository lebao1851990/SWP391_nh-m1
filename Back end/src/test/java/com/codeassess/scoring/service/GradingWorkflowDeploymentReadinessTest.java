package com.codeassess.scoring.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.plagiarism.service.AdminAppealService;
import com.codeassess.plagiarism.service.OpaqueIdEncoder;
import com.codeassess.plagiarism.service.StudentAppealService;
import com.codeassess.scoring.dto.request.ApproveGradeRequest;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.SubmissionStorageService;
import com.codeassess.submission.service.TeacherSubmissionService;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.testcase.service.AutomatedTestRunnerService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GradingWorkflowDeploymentReadinessTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private GradeAuditLogRepository gradeAuditLogRepository;

    @Mock
    private SubmissionStorageService submissionStorageService;

    @Mock
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Mock
    private AutomatedTestRunnerService automatedTestRunnerService;

    @Mock
    private RuleScoreRepository ruleScoreRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    private StudentAppealService studentAppealService;
    private AdminAppealService adminAppealService;
    private TeacherGradingService teacherGradingService;

    private User student;
    private User teacher;
    private User admin;
    private Exam exam;
    private ExamQuestion question;
    private Submission submission;

    @BeforeEach
    void setUp() {
        student = User.builder()
                .id("student-1")
                .fullName("Nguyễn Văn A")
                .email("student1@codeassess.edu.vn")
                .role(Role.STUDENT)
                .build();

        teacher = User.builder()
                .id("teacher-1")
                .fullName("TS. Trần Thị B")
                .email("teacher1@codeassess.edu.vn")
                .role(Role.TEACHER)
                .build();

        admin = User.builder()
                .id("admin-1")
                .fullName("Quản Trị Viên")
                .email("admin@codeassess.edu.vn")
                .role(Role.ADMIN)
                .build();

        com.codeassess.classes.entity.CourseClass courseClass = com.codeassess.classes.entity.CourseClass.builder()
                .id("class-1")
                .name("Lớp Lập trình Nâng cao")
                .teacher(teacher)
                .build();

        exam = Exam.builder()
                .id("exam-1")
                .title("Kỳ thi Lập trình Nâng cao 2026")
                .courseClass(courseClass)
                .build();

        question = ExamQuestion.builder()
                .id("q-1")
                .exam(exam)
                .title("Câu 1: Thuật toán QuickSort")
                .maxScore(BigDecimal.valueOf(10.00))
                .build();

        submission = Submission.builder()
                .id("sub-ready-1")
                .exam(exam)
                .question(question)
                .student(student)
                .status("GRADED")
                .autoScore(BigDecimal.valueOf(8.50))
                .totalScore(BigDecimal.valueOf(8.50))
                .reviewStatus("APPROVED")
                .publicationStatus("PUBLISHED")
                .approvedScore(BigDecimal.valueOf(8.50))
                .publishedScore(BigDecimal.valueOf(8.50))
                .scoreVersion(1)
                .archiveHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .zipStoragePath("./data/submissions/sub-ready-1.zip")
                .originalFilename("Solution.zip")
                .build();

        studentAppealService = new StudentAppealService(appealRepository, submissionRepository,
                gradeAuditLogRepository);
        adminAppealService = new AdminAppealService(appealRepository, submissionRepository, gradeAuditLogRepository,
                submissionStorageService);

        // Inject optional dependencies into AdminAppealService via reflection or field
        org.springframework.test.util.ReflectionTestUtils.setField(adminAppealService, "studentQuestionScoreRepository",
                studentQuestionScoreRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(adminAppealService, "automatedTestRunnerService",
                automatedTestRunnerService);

        teacherGradingService = new TeacherGradingService(
                submissionRepository,
                mock(com.codeassess.user.repository.UserRepository.class),
                ruleScoreRepository,
                gradeAuditLogRepository,
                submissionTestResultRepository,
                studentQuestionScoreRepository);
    }

    // =========================================================================
    // 1. APPEAL CONCURRENCY TESTS (SECTION 3)
    // =========================================================================

    @Test
    @DisplayName("Concurrency: Simultaneous appeal creation for same submission yields exactly 1 success and 1 ConflictException")
    void testConcurrentAppealCreation_SimultaneousRequests_OnlyOneActive() throws Exception {
        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId(submission.getId());
        req.setExplanation("Em xin khiếu nại testcase số 3 vì thuật toán đệ quy đã xử lý đúng điều kiện dừng.");
        req.setBasis("Em đã kiểm tra độc lập và output hoàn toàn trùng khớp với file mẫu.");

        java.util.concurrent.locks.ReentrantLock submissionLock = new java.util.concurrent.locks.ReentrantLock();
        when(submissionRepository.findByIdAndStudent_IdForUpdate(eq(submission.getId()), eq(student.getId())))
                .thenAnswer(inv -> {
                    submissionLock.lock();
                    return Optional.of(submission);
                });

        AtomicBoolean activeAppealExists = new AtomicBoolean(false);

        when(appealRepository.existsBySubmission_IdAndStatusIn(eq(submission.getId()), anyList()))
                .thenAnswer(inv -> activeAppealExists.get());

        when(appealRepository.save(any(Appeal.class)))
                .thenAnswer(inv -> {
                    Appeal a = inv.getArgument(0);
                    a.setId(101L);
                    activeAppealExists.set(true); // Thread 1 commits appeal
                    return a;
                });

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<Future<AppealResponseDto>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startGate.await(); // ensure both threads fire simultaneously
                try {
                    AppealResponseDto res = studentAppealService.createAppeal(req, student);
                    successCount.incrementAndGet();
                    return res;
                } catch (ConflictException ce) {
                    conflictCount.incrementAndGet();
                    throw ce;
                } finally {
                    if (submissionLock.isHeldByCurrentThread()) {
                        submissionLock.unlock();
                    }
                    endGate.countDown();
                }
            }));
        }

        // Release threads simultaneously
        startGate.countDown();
        endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one request must succeed and the other must receive ConflictException
        assertEquals(1, successCount.get(), "Exactly one appeal creation must succeed");
        assertEquals(1, conflictCount.get(), "Losing request must receive ConflictException");

        // Verify save was called exactly once
        verify(appealRepository, times(1)).save(any(Appeal.class));
    }

    @Test
    @DisplayName("Concurrency: Simultaneous Admin resolution requests on same appeal yields exactly 1 success and 1 ConflictException")
    void testConcurrentAdminResolution_SimultaneousRequests_OnlyOneWins() throws Exception {
        Appeal appeal = Appeal.builder()
                .id(202L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .reason("Khiếu nại điểm")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(appeal.getId());

        java.util.concurrent.locks.ReentrantLock dbRowLock = new java.util.concurrent.locks.ReentrantLock();
        when(appealRepository.findByIdForUpdate(appeal.getId()))
                .thenAnswer(inv -> {
                    dbRowLock.lock();
                    return Optional.of(appeal);
                });

        AtomicBoolean resolved = new AtomicBoolean(false);

        when(appealRepository.save(any(Appeal.class)))
                .thenAnswer(inv -> {
                    Appeal a = inv.getArgument(0);
                    resolved.set(true);
                    return a;
                });

        AppealResolveRequestDto req1 = new AppealResolveRequestDto();
        req1.setDecision("APPROVED");
        req1.setResolutionAction("REGRADE");
        req1.setReviewerNotes("Chấp thuận khiếu nại - Kích hoạt chấm lại.");

        AppealResolveRequestDto req2 = new AppealResolveRequestDto();
        req2.setDecision("REJECTED");
        req2.setReviewerNotes("Bác bỏ khiếu nại.");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Thread 1 attempts resolution
        executor.submit(() -> {
            try {
                startGate.await();
                adminAppealService.resolveAppeal(opaqueId, req1, admin);
                successCount.incrementAndGet();
            } catch (ConflictException ce) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                if (dbRowLock.isHeldByCurrentThread()) {
                    dbRowLock.unlock();
                }
                endGate.countDown();
            }
        });

        // Thread 2 attempts simultaneous resolution
        executor.submit(() -> {
            try {
                startGate.await();
                // If thread 1 already changed status to APPROVED, appeal status in memory
                // reflects it
                adminAppealService.resolveAppeal(opaqueId, req2, admin);
                successCount.incrementAndGet();
            } catch (ConflictException ce) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                if (dbRowLock.isHeldByCurrentThread()) {
                    dbRowLock.unlock();
                }
                endGate.countDown();
            }
        });

        startGate.countDown();
        endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 must succeed, the other receives ConflictException
        assertEquals(1, successCount.get(), "Exactly one admin resolution must succeed");
        assertEquals(1, conflictCount.get(), "Second admin resolution must be rejected with ConflictException");
    }

    // =========================================================================
    // 2. GRADE VERSIONING AND BULLMQ IDEMPOTENCY (SECTION 4)
    // =========================================================================

    @Test
    @DisplayName("Grade Versioning: Admin Regrade increments score_version, unpublishes, and preserves audit trail")
    void testRegrade_IncrementsScoreVersionAndUnpublishes() {
        Appeal appeal = Appeal.builder()
                .id(303L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .reason("Khiếu nại chấm lại")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(appeal.getId());
        when(appealRepository.findByIdForUpdate(appeal.getId())).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenReturn(appeal);

        AppealResolveRequestDto req = new AppealResolveRequestDto();
        req.setDecision("APPROVED");
        req.setResolutionAction("REGRADE");
        req.setReviewerNotes("Chấp thuận yêu cầu chấm lại bằng testcases mới.");

        assertEquals(1, submission.getScoreVersion());
        assertEquals("PUBLISHED", submission.getPublicationStatus());

        adminAppealService.resolveAppeal(opaqueId, req, admin);

        // Assert submission state after regrade trigger
        assertEquals(2, submission.getScoreVersion(), "Score version must increment to 2");
        assertEquals("UNPUBLISHED", submission.getPublicationStatus(), "Submission must be unpublished");
        assertEquals("PENDING_REVIEW", submission.getReviewStatus(), "Review status must reset to PENDING_REVIEW");
        assertNull(submission.getApprovedScore(), "Approved score must be cleared");
        assertNull(submission.getPublishedScore(), "Published score must be cleared");

        // Verify automated grading runner service was invoked with the submission
        verify(automatedTestRunnerService, times(1)).runAndGrade(eq(submission));

        // Verify GradeAuditLog created
        ArgumentCaptor<GradeAuditLog> auditCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(auditCaptor.capture());
        GradeAuditLog log = auditCaptor.getValue();
        assertEquals("APPEAL_APPROVED_REGRADE", log.getActionType());
        assertEquals(submission.getId(), log.getSubmission().getId());
    }

    @Test
    @DisplayName("Idempotency: Stale review cannot publish unapproved submission")
    void testStaleReview_CannotPublishUnapprovedSubmission() {
        // Submission has been reset to PENDING_REVIEW with score_version = 2
        submission.setReviewStatus("PENDING_REVIEW");
        submission.setPublicationStatus("UNPUBLISHED");
        submission.setApprovedScore(null);
        submission.setScoreVersion(2);

        when(submissionRepository.findByIdAndTeacherAccess(submission.getId(), teacher.getId())).thenReturn(Optional.of(submission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> teacherGradingService.publishGrade(submission.getId(), teacher.getId()));

        assertTrue(ex.getMessage().contains("Chỉ có thể công bố bài thi đã được phê duyệt"));
        assertEquals("UNPUBLISHED", submission.getPublicationStatus(), "Publication status must remain UNPUBLISHED");
    }

    // =========================================================================
    // 3. ZIP INTEGRITY AND IMMUTABILITY (SECTION 5)
    // =========================================================================

    @Test
    @DisplayName("ZIP Integrity: Original ZIP archive is immutable and re-verified via SHA-256 hash")
    void testZipArchive_ImmutabilityAndHashIntegrity() throws Exception {
        byte[] mockZipBytes = "PK\003\004MockJavaSourceCodeContent".getBytes(StandardCharsets.UTF_8);

        // Compute SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(mockZipBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        String calculatedHash = hexString.toString();

        submission.setArchiveHash(calculatedHash);

        when(submissionStorageService.hasZip(submission.getId())).thenReturn(true);
        when(submissionStorageService.getZipBytes(submission.getId())).thenReturn(mockZipBytes);

        Appeal appeal = Appeal.builder()
                .id(404L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(appeal.getId());
        when(appealRepository.findByIdForUpdate(appeal.getId())).thenReturn(Optional.of(appeal));

        // Admin downloads source archive for review
        byte[] downloadedBytes = adminAppealService.getAppealSubmissionArchive(opaqueId, admin);

        assertNotNull(downloadedBytes);
        assertArrayEquals(mockZipBytes, downloadedBytes);

        // Re-verify SHA-256 matches submission.archiveHash
        byte[] downloadedHashBytes = digest.digest(downloadedBytes);
        StringBuilder downloadedHex = new StringBuilder();
        for (byte b : downloadedHashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                downloadedHex.append('0');
            downloadedHex.append(hex);
        }
        assertEquals(submission.getArchiveHash(), downloadedHex.toString(), "ZIP SHA-256 hash must be identical");

        // Verify audit log recorded for archive download
        ArgumentCaptor<GradeAuditLog> auditCaptor = ArgumentCaptor.forClass(GradeAuditLog.class);
        verify(gradeAuditLogRepository).save(auditCaptor.capture());
        GradeAuditLog auditLog = auditCaptor.getValue();
        assertEquals("APPEAL_ARCHIVE_DOWNLOADED", auditLog.getActionType());
    }

    @Test
    @DisplayName("ZIP Security: Unrelated teacher cannot download student ZIP archive")
    void testZipSecurity_UnrelatedTeacherCannotDownloadArchive() {
        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submission.getId(), "teacher-stranger"))
                .thenReturn(Optional.empty());

        TeacherSubmissionService teacherSubmissionService = new TeacherSubmissionService(
                submissionRepository,
                new SubmissionMapper(),
                submissionStorageService
        );

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> teacherSubmissionService.getSubmissionZip(submission.getId(), "teacher-stranger"));

        assertTrue(ex.getMessage().contains("access denied") || ex.getMessage().contains("not found"));
    }

    // =========================================================================
    // 4. LEGACY BACKFILL SEMANTICS & HISTORICAL COMPATIBILITY (TASK-V22-SEMANTIC-FIX)
    // =========================================================================

    @Test
    @DisplayName("Legacy Backfill: Historical submission visible to student with NULL teacher attribution and legacy notes")
    void testHistoricalLegacyBackfillSemantics_StudentCanViewScoreWithoutTeacherAttribution() {
        Submission legacySub = Submission.builder()
                .id("sub-legacy-1")
                .exam(exam)
                .question(question)
                .student(student)
                .status("COMPLETED")
                .autoScore(BigDecimal.valueOf(10.00))
                .totalScore(BigDecimal.valueOf(10.00))
                .approvedScore(BigDecimal.valueOf(10.00))
                .publishedScore(BigDecimal.valueOf(10.00))
                .reviewStatus("LEGACY_AUTO")
                .publicationStatus("PUBLISHED")
                .reviewedBy(null) // Not falsely attributed to teacher
                .publishedBy(null)
                .reviewNotes("Legacy auto-backfill from V21 pre-approval system")
                .scoreVersion(1)
                .build();

        StudentScoringService studentScoringService = new StudentScoringService(
                submissionRepository, ruleScoreRepository, submissionTestResultRepository
        );

        when(submissionRepository.findByIdAndStudent_Id("sub-legacy-1", student.getId()))
                .thenReturn(Optional.of(legacySub));

        // 1. Student can view published score without 403
        var scoreResult = studentScoringService.getStudentResult("sub-legacy-1", student.getId());
        assertNotNull(scoreResult);
        assertEquals(BigDecimal.valueOf(10.00), scoreResult.getEffectiveScore());

        // 2. Teacher inspects grading detail: shows null teacher attribution but preserved score & legacy note
        when(submissionRepository.findByIdAndTeacherAccess("sub-legacy-1", teacher.getId()))
                .thenReturn(Optional.of(legacySub));

        var teacherDetail = teacherGradingService.getGradingDetail("sub-legacy-1", teacher.getId());
        assertNotNull(teacherDetail);
        assertNull(teacherDetail.getReviewedById(), "reviewedBy must be NULL to prevent false attribution");
        assertNull(teacherDetail.getReviewedByName(), "reviewedByName must be NULL");
        assertEquals("Legacy auto-backfill from V21 pre-approval system", teacherDetail.getReviewNotes());
        assertEquals("LEGACY_AUTO", teacherDetail.getReviewStatus());
        assertEquals("PUBLISHED", teacherDetail.getPublicationStatus());

        // 3. Teacher pending queue: legacy submission does NOT appear in PENDING_REVIEW queue
        when(submissionRepository.findByTeacherIdAndReviewStatus(teacher.getId(), "PENDING_REVIEW"))
                .thenReturn(Collections.emptyList());

        var pendingQueue = teacherGradingService.getTeacherGradingSubmissions(teacher.getId(), "PENDING_REVIEW");
        assertTrue(pendingQueue.isEmpty(), "Legacy approved submissions must not clutter the active pending review queue");
    }

    @Test
    @DisplayName("New Submissions: Strictly require teacher approval and mask score from student prior to publication")
    void testNewSubmissionRequiresTeacherApproval_StudentBlockedBeforePublication() {
        Submission newSub = Submission.builder()
                .id("sub-new-1")
                .exam(exam)
                .question(question)
                .student(student)
                .status("COMPLETED")
                .autoScore(BigDecimal.valueOf(9.00))
                .totalScore(BigDecimal.valueOf(9.00))
                .reviewStatus("PENDING_REVIEW")
                .publicationStatus("UNPUBLISHED")
                .scoreVersion(1)
                .build();

        StudentScoringService studentScoringService = new StudentScoringService(
                submissionRepository, ruleScoreRepository, submissionTestResultRepository
        );

        when(submissionRepository.findByIdAndStudent_Id("sub-new-1", student.getId()))
                .thenReturn(Optional.of(newSub));

        // 1. Student access to provisional score detail is strictly blocked with 403
        org.springframework.security.access.AccessDeniedException ex = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> studentScoringService.getStudentResult("sub-new-1", student.getId())
        );
        assertTrue(ex.getMessage().contains("chưa được công bố"));

        // 2. SubmissionMapper masks provisional scores
        SubmissionMapper mapper = new SubmissionMapper();
        var studentDto = mapper.toStudentDetailResponse(newSub);
        assertNull(studentDto.getTotalScore(), "Total score must be masked");
        assertNull(studentDto.getAutoScore(), "Auto score must be masked");
        assertNull(studentDto.getPublishedScore(), "Published score must be masked");
    }

    @Test
    @DisplayName("Special Submission 68dac054: Preserves admin override 0.00 and approved appeal without false teacher claim")
    void testSpecialSubmission68dac054_MaintainsOverrideScoreAndAppealAudit() {
        Submission sub68 = Submission.builder()
                .id("68dac054-4a65-4b37-84fc-6576928fd8d6")
                .exam(exam)
                .question(question)
                .student(student)
                .status("COMPLETED")
                .autoScore(BigDecimal.valueOf(7.50))
                .totalScore(BigDecimal.valueOf(0.00))
                .overrideScore(BigDecimal.valueOf(0.00))
                .approvedScore(BigDecimal.valueOf(0.00))
                .publishedScore(BigDecimal.valueOf(0.00))
                .reviewStatus("LEGACY_AUTO")
                .publicationStatus("PUBLISHED")
                .reviewedBy(null) // No false attribution to teacher
                .reviewNotes("Legacy appeal resolution #30001 by admin")
                .scoreVersion(1)
                .build();

        when(submissionRepository.findByIdAndTeacherAccess(sub68.getId(), teacher.getId()))
                .thenReturn(Optional.of(sub68));

        var detail = teacherGradingService.getGradingDetail(sub68.getId(), teacher.getId());
        assertEquals(BigDecimal.valueOf(0.00), detail.getEffectiveScore(), "Effective score must be 0.00 from override");
        assertEquals(BigDecimal.valueOf(0.00), detail.getPublishedScore());
        assertNull(detail.getReviewedById(), "reviewedBy must remain null");
        assertEquals("Legacy appeal resolution #30001 by admin", detail.getReviewNotes());
    }

    @Test
    @DisplayName("Legacy Score Edit: Teacher modifying score on LEGACY_AUTO resets it to PENDING_REVIEW and UNPUBLISHED")
    void testLegacyAutoScoreModification_ResetsToPendingReview() {
        Submission legacySub = Submission.builder()
                .id("sub-legacy-mod")
                .exam(exam)
                .question(question)
                .student(student)
                .status("COMPLETED")
                .autoScore(BigDecimal.valueOf(8.00))
                .totalScore(BigDecimal.valueOf(8.00))
                .approvedScore(BigDecimal.valueOf(8.00))
                .publishedScore(BigDecimal.valueOf(8.00))
                .reviewStatus("LEGACY_AUTO")
                .publicationStatus("PUBLISHED")
                .scoreVersion(1)
                .build();

        var mockUserRepo = mock(com.codeassess.user.repository.UserRepository.class);
        when(mockUserRepo.findById(teacher.getId())).thenReturn(Optional.of(teacher));

        TeacherScoringService teacherScoringService = new TeacherScoringService(
                submissionRepository,
                ruleScoreRepository,
                mock(com.codeassess.scoring.repository.ScoringCriteriaRepository.class),
                gradeAuditLogRepository,
                mockUserRepo,
                mock(com.codeassess.testcase.repository.SubmissionTestResultRepository.class),
                studentQuestionScoreRepository
        );

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-legacy-mod", teacher.getId()))
                .thenReturn(Optional.of(legacySub));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.codeassess.scoring.dto.request.SaveScoringPayload payload = new com.codeassess.scoring.dto.request.SaveScoringPayload();
        payload.setOverrideTotalScore(BigDecimal.valueOf(9.00));
        payload.setOverrideRationale("Điều chỉnh điểm theo phúc khảo cũ");

        teacherScoringService.updateScoring("sub-legacy-mod", teacher.getId(), payload);

        assertEquals("PENDING_REVIEW", legacySub.getReviewStatus(), "Must reset to PENDING_REVIEW");
        assertEquals("UNPUBLISHED", legacySub.getPublicationStatus(), "Must invalidate published status");
        assertNull(legacySub.getApprovedScore(), "Approved score must be cleared");
        assertNull(legacySub.getPublishedScore(), "Published score must be cleared");
        assertEquals(2, legacySub.getScoreVersion());
    }
}