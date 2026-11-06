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
import com.codeassess.scoring.dto.request.RequestRevisionRequest;
import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.StudentSubmissionResult;
import com.codeassess.scoring.dto.response.TeacherGradingDetailResponse;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.submission.service.SubmissionStorageService;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GradingApprovalAndAppealWorkflowTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RuleScoreRepository ruleScoreRepository;

    @Mock
    private ScoringCriteriaRepository scoringCriteriaRepository;

    @Mock
    private GradeAuditLogRepository gradeAuditLogRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @Mock
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private SubmissionStorageService submissionStorageService;

    private TeacherGradingService teacherGradingService;
    private TeacherScoringService teacherScoringService;
    private StudentScoringService studentScoringService;
    private StudentAppealService studentAppealService;
    private AdminAppealService adminAppealService;
    private SubmissionMapper submissionMapper;

    private User teacher;
    private User student;
    private User admin;
    private Exam exam;
    private ExamQuestion question;
    private Submission submission;

    @BeforeEach
    void setUp() {
        submissionMapper = new SubmissionMapper();

        teacherGradingService = new TeacherGradingService(
                submissionRepository,
                userRepository,
                ruleScoreRepository,
                gradeAuditLogRepository,
                submissionTestResultRepository,
                studentQuestionScoreRepository
        );

        teacherScoringService = new TeacherScoringService(
                submissionRepository,
                ruleScoreRepository,
                scoringCriteriaRepository,
                gradeAuditLogRepository,
                userRepository,
                submissionTestResultRepository,
                studentQuestionScoreRepository
        );

        studentScoringService = new StudentScoringService(
                submissionRepository,
                ruleScoreRepository,
                submissionTestResultRepository
        );

        studentAppealService = new StudentAppealService(
                appealRepository,
                submissionRepository,
                gradeAuditLogRepository
        );

        adminAppealService = new AdminAppealService(
                appealRepository,
                submissionRepository,
                gradeAuditLogRepository,
                submissionStorageService
        );

        teacher = User.builder()
                .id("teacher-1")
                .role(Role.TEACHER)
                .fullName("Thầy Giáo Viên")
                .email("teacher@codeassess.edu.vn")
                .build();

        student = User.builder()
                .id("student-1")
                .role(Role.STUDENT)
                .fullName("Nguyễn Văn Học Sinh")
                .email("student@codeassess.edu.vn")
                .build();

        admin = User.builder()
                .id("admin-1")
                .role(Role.ADMIN)
                .fullName("Quản Trị Viên")
                .email("admin@codeassess.edu.vn")
                .build();

        exam = Exam.builder()
                .id("exam-1")
                .title("Kỳ thi Lập trình Nâng cao")
                .build();

        question = ExamQuestion.builder()
                .id("question-1")
                .exam(exam)
                .title("Bài 1: Xử lý mảng")
                .maxScore(BigDecimal.valueOf(10.00))
                .displayOrder(0)
                .build();

        submission = Submission.builder()
                .id("sub-123")
                .exam(exam)
                .question(question)
                .student(student)
                .status("COMPLETED")
                .autoScore(BigDecimal.valueOf(8.50))
                .totalScore(BigDecimal.valueOf(8.50))
                .reviewStatus("PENDING_REVIEW")
                .publicationStatus("UNPUBLISHED")
                .scoreVersion(1)
                .testCasesPassed(8)
                .testCasesTotal(10)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    // =========================================================================
    // SECTION 1: PROVISIONAL SCORE ISOLATION & STUDENT RESULT ACCESS
    // =========================================================================

    @Test
    @DisplayName("Scenario 1 & 10: Automatic grading finishes but student cannot access provisional score (Throws AccessDeniedException)")
    void studentCannotAccessUnpublishedGrade() {
        submission.setPublicationStatus("UNPUBLISHED");
        when(submissionRepository.findByIdAndStudent_Id("sub-123", "student-1"))
                .thenReturn(Optional.of(submission));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () ->
                studentScoringService.getStudentResult("sub-123", "student-1")
        );

        assertTrue(exception.getMessage().contains("chưa được công bố"));
    }

    @Test
    @DisplayName("Scenario 9: Student cannot access another student's grade")
    void studentCannotAccessOtherStudentGrade() {
        when(submissionRepository.findByIdAndStudent_Id("sub-123", "student-other"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                studentScoringService.getStudentResult("sub-123", "student-other")
        );
    }

    @Test
    @DisplayName("Scenario 11: Student can access grade once PUBLISHED")
    void studentCanAccessPublishedGrade() {
        submission.setPublicationStatus("PUBLISHED");
        submission.setApprovedScore(BigDecimal.valueOf(8.50));
        submission.setPublishedScore(BigDecimal.valueOf(8.50));
        when(submissionRepository.findByIdAndStudent_Id("sub-123", "student-1"))
                .thenReturn(Optional.of(submission));

        StudentSubmissionResult result = studentScoringService.getStudentResult("sub-123", "student-1");
        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(8.50), result.getTotalScore());
    }

    @Test
    @DisplayName("Provisional scores are masked in StudentSubmissionResponse when unpublished")
    void studentSubmissionResponseMasksUnpublishedScores() {
        submission.setPublicationStatus("UNPUBLISHED");
        submission.setAutoScore(BigDecimal.valueOf(9.00));
        submission.setTotalScore(BigDecimal.valueOf(9.00));
        submission.setInstructorFeedback("Internal notes");

        SubmissionDetailResponse studentDto = submissionMapper.toStudentDetailResponse(submission);
        assertNull(studentDto.getTotalScore());
        assertNull(studentDto.getAutoScore());
        assertNull(studentDto.getOverrideScore());
        assertNull(studentDto.getPublishedScore());
        assertNull(studentDto.getInstructorFeedback());
        assertEquals("UNPUBLISHED", studentDto.getPublicationStatus());
    }

    // =========================================================================
    // SECTION 2: TEACHER GRADING APPROVAL & PUBLICATION WORKFLOW
    // =========================================================================

    @Test
    @DisplayName("Scenario 2 & 3: Teacher access control check")
    void unauthorizedTeacherReceivesResourceNotFound() {
        when(submissionRepository.findByIdAndTeacherAccess("sub-123", "teacher-unauth"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teacherGradingService.approveGrade("sub-123", "teacher-unauth", new ApproveGradeRequest("Approved"))
        );
    }

    @Test
    @DisplayName("Scenario 4: Authorized Teacher approves a grade successfully")
    void teacherApprovesGradeSuccessfully() {
        when(submissionRepository.findByIdAndTeacherAccess("sub-123", "teacher-1"))
                .thenReturn(Optional.of(submission));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        ApproveGradeRequest request = new ApproveGradeRequest("Mã nguồn sạch, thuật toán tối ưu");
        TeacherGradingDetailResponse response = teacherGradingService.approveGrade("sub-123", "teacher-1", request);

        assertEquals("APPROVED", submission.getReviewStatus());
        assertEquals(BigDecimal.valueOf(8.50), submission.getApprovedScore());
        assertEquals(teacher, submission.getReviewedBy());
        assertNotNull(submission.getReviewedAt());
        assertEquals(2, submission.getScoreVersion());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "GRADE_APPROVED".equals(log.getActionType()) &&
                BigDecimal.valueOf(8.50).compareTo(log.getPostEffectiveScore()) == 0
        ));
    }

    @Test
    @DisplayName("Scenario 5: Teacher cannot approve a submission that is not GRADED/COMPLETED (e.g. PROCESSING)")
    void teacherCannotApproveProcessingSubmission() {
        submission.setStatus("PROCESSING");
        when(submissionRepository.findByIdAndTeacherAccess("sub-123", "teacher-1"))
                .thenReturn(Optional.of(submission));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                teacherGradingService.approveGrade("sub-123", "teacher-1", new ApproveGradeRequest("Notes"))
        );
        assertTrue(ex.getMessage().contains("Không thể phê duyệt bài nộp đang trong quá trình chấm điểm"));
    }

    @Test
    @DisplayName("Scenario 6: Score changed after approval invalidates previous approval")
    void scoreChangeInvalidatesPreviousApproval() {
        submission.setReviewStatus("APPROVED");
        submission.setApprovedScore(BigDecimal.valueOf(8.50));
        submission.setPublicationStatus("PUBLISHED");
        submission.setPublishedScore(BigDecimal.valueOf(8.50));
        submission.setScoreVersion(2);

        com.codeassess.classes.entity.CourseClass courseClass = new com.codeassess.classes.entity.CourseClass();
        courseClass.setTeacher(teacher);
        exam.setCourseClass(courseClass);

        when(submissionRepository.findByIdAndExam_CourseClass_Teacher_Id("sub-123", "teacher-1"))
                .thenReturn(Optional.of(submission));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        SaveScoringPayload payload = new SaveScoringPayload();
        payload.setOverrideTotalScore(BigDecimal.valueOf(9.50));
        payload.setOverrideRationale("Cộng điểm sáng tạo thuật toán");

        teacherScoringService.updateScoring("sub-123", "teacher-1", payload);

        assertEquals("PENDING_REVIEW", submission.getReviewStatus());
        assertNull(submission.getApprovedScore());
        assertEquals("UNPUBLISHED", submission.getPublicationStatus());
        assertNull(submission.getPublishedScore());
        assertEquals(3, submission.getScoreVersion());

        verify(studentQuestionScoreRepository, times(1)).unpublishQuestionScore(question.getId(), student.getId());
    }

    @Test
    @DisplayName("Scenario 7: Teacher can publish only an APPROVED grade")
    void teacherCanPublishOnlyApprovedGrade() {
        submission.setReviewStatus("PENDING_REVIEW");
        when(submissionRepository.findByIdAndTeacherAccess("sub-123", "teacher-1"))
                .thenReturn(Optional.of(submission));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                teacherGradingService.publishGrade("sub-123", "teacher-1")
        );
        assertTrue(ex.getMessage().contains("Chỉ có thể công bố bài thi đã được phê duyệt"));
    }

    @Test
    @DisplayName("Scenario 7 & 8: Teacher publishes approved grade and syncs to student_question_scores")
    void teacherPublishesApprovedGradeSuccessfully() {
        submission.setReviewStatus("APPROVED");
        submission.setApprovedScore(BigDecimal.valueOf(8.50));
        submission.setPublicationStatus("UNPUBLISHED");

        when(submissionRepository.findByIdAndTeacherAccess("sub-123", "teacher-1"))
                .thenReturn(Optional.of(submission));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        teacherGradingService.publishGrade("sub-123", "teacher-1");

        assertEquals("PUBLISHED", submission.getPublicationStatus());
        assertEquals(teacher, submission.getPublishedBy());
        assertEquals(BigDecimal.valueOf(8.50), submission.getPublishedScore());
        assertNotNull(submission.getPublishedAt());

        verify(studentQuestionScoreRepository, times(1)).publishQuestionScore(
                question.getId(), student.getId(), BigDecimal.valueOf(8.50)
        );

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "GRADE_PUBLISHED".equals(log.getActionType())
        ));
    }

    @Test
    @DisplayName("Teacher requests revision with valid reason")
    void teacherRequestsRevisionSuccessfully() {
        when(submissionRepository.findByIdAndTeacherAccess("sub-123", "teacher-1"))
                .thenReturn(Optional.of(submission));
        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        RequestRevisionRequest req = new RequestRevisionRequest("Thuật toán đệ quy bị tràn bộ nhớ trên test lớn, cần viết lại bằng vòng lặp");
        teacherGradingService.requestRevision("sub-123", "teacher-1", req);

        assertEquals("REVISION_REQUIRED", submission.getReviewStatus());
        assertEquals("UNPUBLISHED", submission.getPublicationStatus());
        assertNull(submission.getPublishedScore());
        assertEquals(req.getReason(), submission.getReviewNotes());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "GRADE_REVISION_REQUESTED".equals(log.getActionType())
        ));
    }

    // =========================================================================
    // SECTION 3: STUDENT APPEAL WORKFLOW
    // =========================================================================

    @Test
    @DisplayName("Scenario 12: Student cannot appeal an UNPUBLISHED grade")
    void studentCannotAppealUnpublishedGrade() {
        submission.setPublicationStatus("UNPUBLISHED");
        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub-123", "student-1"))
                .thenReturn(Optional.of(submission));

        AppealCreateRequestDto dto = AppealCreateRequestDto.builder()
                .submissionId("sub-123")
                .explanation("Em xin giải trình bài nộp của em đạt đầy đủ yêu cầu của đề bài.")
                .basis("logic_explanation")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                studentAppealService.createAppeal(dto, student)
        );
        assertTrue(ex.getMessage().contains("đã được công bố điểm chính thức"));
    }

    @Test
    @DisplayName("Scenario 13: Student can appeal a PUBLISHED grade")
    void studentCanAppealPublishedGrade() {
        submission.setPublicationStatus("PUBLISHED");
        submission.setPublishedScore(BigDecimal.valueOf(8.50));

        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub-123", "student-1"))
                .thenReturn(Optional.of(submission));
        when(appealRepository.existsBySubmission_IdAndStatusIn(eq("sub-123"), any()))
                .thenReturn(false);

        Appeal savedAppeal = Appeal.builder()
                .id(100L)
                .submission(submission)
                .student(student)
                .reason("Em xin giải trình bài nộp của em đạt đầy đủ yêu cầu của đề bài.")
                .basis("logic_explanation")
                .status("PENDING")
                .build();
        when(appealRepository.save(any(Appeal.class))).thenReturn(savedAppeal);

        AppealCreateRequestDto dto = AppealCreateRequestDto.builder()
                .submissionId("sub-123")
                .explanation("Em xin giải trình bài nộp của em đạt đầy đủ yêu cầu của đề bài.")
                .basis("logic_explanation")
                .build();

        AppealResponseDto response = studentAppealService.createAppeal(dto, student);
        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    @DisplayName("Scenario 14: Student cannot create duplicate active appeals (PENDING or UNDER_REVIEW)")
    void studentCannotCreateDuplicateActiveAppeals() {
        submission.setPublicationStatus("PUBLISHED");
        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub-123", "student-1"))
                .thenReturn(Optional.of(submission));
        when(appealRepository.existsBySubmission_IdAndStatusIn(eq("sub-123"), any()))
                .thenReturn(true);

        AppealCreateRequestDto dto = AppealCreateRequestDto.builder()
                .submissionId("sub-123")
                .explanation("Em xin giải trình bài nộp của em đạt đầy đủ yêu cầu của đề bài.")
                .build();

        ConflictException ex = assertThrows(ConflictException.class, () ->
                studentAppealService.createAppeal(dto, student)
        );
        assertTrue(ex.getMessage().contains("Đã có một đơn khiếu nại đang chờ xử lý hoặc đang được thẩm định"));
    }

    // =========================================================================
    // SECTION 4: ADMIN APPEAL RESOLUTION & AUDIT
    // =========================================================================

    @Test
    @DisplayName("Admin starts review transitions status PENDING -> UNDER_REVIEW with audit log")
    void adminStartsReviewSuccessfully() {
        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .student(student)
                .reason("Khiếu nại điểm")
                .status("PENDING")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        AppealResponseDto response = adminAppealService.startReview(opaqueId, admin);

        assertEquals("UNDER_REVIEW", response.getStatus());
        assertEquals("UNDER_REVIEW", appeal.getStatus());
        assertEquals(admin, appeal.getAdmin());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "APPEAL_UNDER_REVIEW".equals(log.getActionType())
        ));
    }

    @Test
    @DisplayName("Scenario 18 & 19: Admin resolution requires mandatory reviewerNotes")
    void adminResolutionRequiresNotes() {
        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("   ")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminAppealService.resolveAppeal(opaqueId, dto, admin)
        );
        assertTrue(ex.getMessage().contains("Giải trình của người duyệt là bắt buộc"));
    }

    @Test
    @DisplayName("Scenario 21: Admin approves appeal with REGRADE action")
    void adminApprovesWithRegradeAction() {
        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .resolutionAction("REGRADE")
                .reviewerNotes("Đồng ý cho chạy lại bộ test sau khi kiểm tra log sandbox")
                .build();

        AppealResponseDto response = adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertEquals("APPROVED", response.getStatus());
        assertEquals("PENDING_REVIEW", submission.getReviewStatus());
        assertEquals("UNPUBLISHED", submission.getPublicationStatus());
        assertNull(submission.getApprovedScore());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "APPEAL_APPROVED_REGRADE".equals(log.getActionType())
        ));
    }

    @Test
    @DisplayName("Scenario 21: Admin approves appeal with FORWARD_ACADEMIC_REVIEW action")
    void adminApprovesWithForwardAcademicReview() {
        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .resolutionAction("FORWARD_ACADEMIC_REVIEW")
                .reviewerNotes("Chuyển giảng viên đánh giá tính đúng đắn của thuật toán QuickSort tùy biến")
                .build();

        AppealResponseDto response = adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertEquals("APPROVED", response.getStatus());
        assertEquals("REVISION_REQUIRED", submission.getReviewStatus());
        assertEquals("UNPUBLISHED", submission.getPublicationStatus());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "APPEAL_FORWARDED_ACADEMIC_REVIEW".equals(log.getActionType())
        ));
    }

    @Test
    @DisplayName("Scenario 20: Admin score adjustment requires teacher approval before republishing")
    void adminScoreAdjustmentRequiresTeacherApprovalBeforePublishing() {
        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .adjustedScore(BigDecimal.valueOf(9.00))
                .reviewerNotes("Cộng 0.5 điểm do sai sót định dạng output đề bài")
                .build();

        adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertEquals(BigDecimal.valueOf(9.00), submission.getOverrideScore());
        // Must require teacher approval before republishing
        assertEquals("PENDING_REVIEW", submission.getReviewStatus());
        assertEquals("UNPUBLISHED", submission.getPublicationStatus());
        assertNull(submission.getApprovedScore());
        assertNull(submission.getPublishedScore());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "APPEAL_APPROVED".equals(log.getActionType()) &&
                BigDecimal.valueOf(9.00).compareTo(log.getPostEffectiveScore()) == 0
        ));
    }

    @Test
    @DisplayName("Scenario 22: Rejected appeal preserves published score and logs audit")
    void adminRejectsAppealPreservesScore() {
        submission.setPublicationStatus("PUBLISHED");
        submission.setPublishedScore(BigDecimal.valueOf(8.50));

        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("REJECTED")
                .reviewerNotes("Giải trình không có căn cứ; mã nguồn vi phạm giới hạn bộ nhớ theo đúng đề bài")
                .build();

        AppealResponseDto response = adminAppealService.resolveAppeal(opaqueId, dto, admin);

        assertEquals("REJECTED", response.getStatus());
        assertEquals("PUBLISHED", submission.getPublicationStatus());
        assertEquals(BigDecimal.valueOf(8.50), submission.getPublishedScore());

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "APPEAL_REJECTED".equals(log.getActionType())
        ));
    }

    @Test
    @DisplayName("Scenario 17: Admin secure ZIP download records audit trail")
    void adminSecureZipDownloadRecordsAuditTrail() {
        Appeal appeal = Appeal.builder()
                .id(42L)
                .submission(submission)
                .student(student)
                .status("UNDER_REVIEW")
                .build();

        String opaqueId = OpaqueIdEncoder.encode(42L);
        when(appealRepository.findById(42L)).thenReturn(Optional.of(appeal));
        when(submissionStorageService.hasZip("sub-123")).thenReturn(true);
        when(submissionStorageService.getZipBytes("sub-123")).thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04});

        byte[] downloaded = adminAppealService.getAppealSubmissionArchive(opaqueId, admin);
        assertNotNull(downloaded);
        assertTrue(downloaded.length > 0);

        verify(gradeAuditLogRepository, times(1)).save(argThat(log ->
                "APPEAL_ARCHIVE_DOWNLOADED".equals(log.getActionType()) &&
                log.getRationale().contains("Quản trị viên")
        ));
    }
}
