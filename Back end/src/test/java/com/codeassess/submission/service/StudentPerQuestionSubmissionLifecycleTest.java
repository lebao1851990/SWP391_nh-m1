package com.codeassess.submission.service;

import com.codeassess.common.exception.ExamTimeExpiredException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.exam.service.StudentExamService;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.exception.ZipValidationException;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TASK-STUDENT-PER-QUESTION-EXAM-UI-AND-SUBMISSION-REFACTOR-01: Per-Question Submission Lifecycle Tests")
class StudentPerQuestionSubmissionLifecycleTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ZipExtractionService zipExtractionService;

    @Mock
    private SourceHasherService sourceHasherService;

    @Mock
    private EntryPointDiscoveryService entryPointDiscoveryService;

    @Mock
    private DockerSandboxService dockerSandboxService;

    @Mock
    private AstPlagiarismService astPlagiarismService;

    @Mock
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    private ZipSubmissionProperties zipProperties = new ZipSubmissionProperties();
    private SubmissionMapper submissionMapper = new SubmissionMapper();
    private ExamMapper examMapper = new ExamMapper();

    private SubmissionPersistenceService submissionPersistenceService;
    private StudentSubmissionService studentSubmissionService;
    private StudentExamService studentExamService;

    private User student1;
    private User student2;
    private Exam activeExam;
    private Exam upcomingExam;
    private Exam completedExam;

    private ExamQuestion q1;
    private ExamQuestion q2;
    private ExamQuestion qOther;

    private final Map<String, Submission> submissionDb = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        submissionPersistenceService = spy(new SubmissionPersistenceService(
                submissionRepository,
                astPlagiarismService,
                eventPublisher,
                examRepository,
                examQuestionRepository,
                studentQuestionScoreRepository
        ));

        studentSubmissionService = new StudentSubmissionService(
                submissionRepository,
                submissionMapper,
                examRepository,
                userRepository,
                eventPublisher,
                zipExtractionService,
                sourceHasherService,
                entryPointDiscoveryService,
                dockerSandboxService,
                astPlagiarismService,
                zipProperties,
                submissionPersistenceService
        );
        ReflectionTestUtils.setField(studentSubmissionService, "examQuestionRepository", examQuestionRepository);

        studentExamService = new StudentExamService(
                examRepository,
                examMapper,
                null,
                examQuestionRepository,
                null
        );

        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);

        student1 = User.builder().id("student-01").email("student1@fpt.edu.vn").role(Role.STUDENT).build();
        student2 = User.builder().id("student-02").email("student2@fpt.edu.vn").role(Role.STUDENT).build();

        activeExam = Exam.builder()
                .id("exam-active-01")
                .title("Kỳ thi Lập trình Nâng cao")
                .status("active")
                .examDate(now.minusMinutes(15))
                .duration(90)
                .build();

        upcomingExam = Exam.builder()
                .id("exam-upcoming-01")
                .title("Kỳ thi Sắp tới")
                .status("upcoming")
                .examDate(now.plusHours(2))
                .duration(90)
                .problemStatement("Secret exam problem statement")
                .build();

        completedExam = Exam.builder()
                .id("exam-completed-01")
                .title("Kỳ thi Đã kết thúc")
                .status("completed")
                .examDate(now.minusHours(3))
                .duration(60)
                .build();

        q1 = ExamQuestion.builder()
                .id("q-spiral-01")
                .exam(activeExam)
                .title("Duyệt ma trận xoắn ốc")
                .description("In ma trận xoắn ốc kích thước N x M")
                .maxScore(BigDecimal.valueOf(5.00))
                .displayOrder(0)
                .status("PUBLISHED")
                .build();

        q2 = ExamQuestion.builder()
                .id("q-palindrome-02")
                .exam(activeExam)
                .title("Chuỗi đối xứng dài nhất")
                .description("Tìm chuỗi palindrome dài nhất")
                .maxScore(BigDecimal.valueOf(5.00))
                .displayOrder(1)
                .status("PUBLISHED")
                .build();

        Exam otherExam = Exam.builder().id("exam-other-99").status("active").build();
        qOther = ExamQuestion.builder()
                .id("q-foreign-99")
                .exam(otherExam)
                .title("Câu hỏi thuộc kỳ thi khác")
                .description("Nội dung kỳ thi khác")
                .status("PUBLISHED")
                .build();

        submissionDb.clear();
        lenient().when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
            Submission sub = inv.getArgument(0);
            if (sub.getId() == null) {
                sub.setId("sub-" + UUID.randomUUID());
            }
            submissionDb.put(sub.getId(), sub);
            return sub;
        });

        lenient().when(submissionRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(submissionDb.get(inv.getArgument(0))));

        // Setup extracted workspace mock
        Path tempDir = Files.createTempDirectory("test-workspace");
        Path mainFile = tempDir.resolve("Solution.java");
        Files.writeString(mainFile, "public class Solution {}");
        List<com.codeassess.submission.dto.ExtractedFile> files = List.of(
                new com.codeassess.submission.dto.ExtractedFile("Solution.java", mainFile, Files.size(mainFile), true)
        );
        ExtractedWorkspace mockWs = new ExtractedWorkspace(tempDir, files, 2048L, "archive-sha256-hex");

        lenient().when(zipExtractionService.extract(any(byte[].class))).thenReturn(mockWs);
        lenient().when(sourceHasherService.calculateArchiveSha256(any(byte[].class))).thenReturn("sha256-archive");
        lenient().when(sourceHasherService.calculateSourceTreeSha256(anyList())).thenReturn("sha256-tree");
        lenient().when(entryPointDiscoveryService.discoverEntryPoint(any())).thenReturn(EntryPointResult.found("Solution", List.of("Solution")));
        lenient().when(dockerSandboxService.executeWorkspace(any(), any())).thenReturn(
                SandboxResult.builder().status(SandboxResult.Status.SUCCESS).exitCode(0).build()
        );
    }

    private MockMultipartFile createZip(String filename) {
        return new MockMultipartFile("file", filename, "application/zip", ("PK\u0005\u0006" + "\u0000".repeat(18)).getBytes());
    }

    @Test
    @DisplayName("1. Student can retrieve an ACTIVE exam with questions")
    void test01_studentCanRetrieveActiveExam() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01"))
                .thenReturn(Optional.of(activeExam));
        when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-active-01"))
                .thenReturn(List.of(q1, q2));

        ExamResponse res = studentExamService.getStudentExamById("exam-active-01", "student-01");

        assertThat(res.getStatus()).isEqualTo("active");
        assertThat(res.getQuestions()).hasSize(2);
        assertThat(res.getQuestions().get(0).getTitle()).isEqualTo("Duyệt ma trận xoắn ốc");
        assertThat(res.getQuestions().get(1).getTitle()).isEqualTo("Chuỗi đối xứng dài nhất");
    }

    @Test
    @DisplayName("2. Student cannot retrieve protected question content for UPCOMING exam")
    void test02_studentCannotRetrieveProtectedQuestionContentForUpcomingExam() {
        when(examRepository.findByIdAndStudentId("exam-upcoming-01", "student-01"))
                .thenReturn(Optional.of(upcomingExam));

        ExamResponse res = studentExamService.getStudentExamById("exam-upcoming-01", "student-01");

        assertThat(res.getStatus()).isEqualTo("upcoming");
        assertThat(res.getQuestions()).isEmpty();
        assertThat(res.getTotalQuestions()).isEqualTo(0);
        assertThat(res.getProblemStatement()).isNull();
    }

    @Test
    @DisplayName("3. Student cannot submit to UPCOMING exam")
    void test03_studentCannotSubmitToUpcomingExam() {
        when(examRepository.findByIdAndStudentId("exam-upcoming-01", "student-01"))
                .thenReturn(Optional.of(upcomingExam));

        assertThatThrownBy(() -> studentSubmissionService.submitZip("exam-upcoming-01", "q1", "student-01", createZip("sol.zip")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exam has not started yet");
    }

    @Test
    @DisplayName("4. Student can submit Question 1 independently")
    void test04_studentCanSubmitQuestion1Independently() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(examRepository.findByIdForShare("exam-active-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-spiral-01", "exam-active-01")).thenReturn(Optional.of(q1));

        SubmissionDetailResponse res = studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", createZip("q1_sol.zip")
        );

        assertThat(res).isNotNull();
        assertThat(res.getQuestionId()).isEqualTo("q-spiral-01");
        assertThat(res.getQuestionTitle()).isEqualTo("Duyệt ma trận xoắn ốc");
        assertThat(submissionDb).hasSize(1);
    }

    @Test
    @DisplayName("5. Student can submit Question 2 without changing Question 1's submission")
    void test05_studentCanSubmitQuestion2WithoutChangingQuestion1() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(examRepository.findByIdForShare("exam-active-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-spiral-01", "exam-active-01")).thenReturn(Optional.of(q1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-palindrome-02", "exam-active-01")).thenReturn(Optional.of(q2));

        // Submit Q1
        SubmissionDetailResponse resQ1 = studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", createZip("q1.zip")
        );

        // Submit Q2
        SubmissionDetailResponse resQ2 = studentSubmissionService.submitZip(
                "exam-active-01", "q-palindrome-02", "student-01", createZip("q2.zip")
        );

        assertThat(resQ1.getId()).isNotEqualTo(resQ2.getId());
        assertThat(resQ1.getQuestionId()).isEqualTo("q-spiral-01");
        assertThat(resQ2.getQuestionId()).isEqualTo("q-palindrome-02");
        assertThat(submissionDb).hasSize(2);

        Submission sub1 = submissionDb.get(resQ1.getId());
        Submission sub2 = submissionDb.get(resQ2.getId());
        assertThat(sub1.getQuestion().getId()).isEqualTo("q-spiral-01");
        assertThat(sub2.getQuestion().getId()).isEqualTo("q-palindrome-02");
    }

    @Test
    @DisplayName("6. Question ID belonging to another exam is rejected")
    void test06_questionIdBelongingToAnotherExamIsRejected() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        // foreign question does not belong to activeExam
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-foreign-99", "exam-active-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-active-01", "q-foreign-99", "student-01", createZip("q.zip")
        )).isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Question not found or does not belong to this exam");
    }

    @Test
    @DisplayName("7. Invalid question ID is rejected")
    void test07_invalidQuestionIdIsRejected() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("non-existent-id", "exam-active-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-active-01", "non-existent-id", "student-01", createZip("q.zip")
        )).isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Question not found or does not belong to this exam");
    }

    @Test
    @DisplayName("8. Submission after deadline is rejected")
    void test08_submissionAfterDeadlineIsRejected() {
        LocalDateTime past = LocalDateTime.now(Exam.APP_ZONE).minusHours(3);
        Exam expiredExam = Exam.builder()
                .id("exam-expired-01")
                .status("active")
                .examDate(past)
                .duration(60) // ended 2 hours ago
                .build();

        when(examRepository.findByIdAndStudentId("exam-expired-01", "student-01")).thenReturn(Optional.of(expiredExam));

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-expired-01", "q-spiral-01", "student-01", createZip("late.zip")
        )).isInstanceOf(ExamTimeExpiredException.class)
          .hasMessageContaining("Exam submission window has expired");
    }

    @Test
    @DisplayName("9. COMPLETED exam rejects new submissions")
    void test09_completedExamRejectsNewSubmissions() {
        when(examRepository.findByIdAndStudentId("exam-completed-01", "student-01")).thenReturn(Optional.of(completedExam));

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-completed-01", "q-spiral-01", "student-01", createZip("q.zip")
        )).isInstanceOf(Exception.class); // Either ExamTimeExpiredException or IllegalStateException
    }

    @Test
    @DisplayName("10. Student cannot submit on behalf of another student / unassigned class")
    void test10_studentCannotSubmitUnassignedExam() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "unassigned-student"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "unassigned-student", createZip("q.zip")
        )).isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Exam not found or you are not enrolled in this class");
    }

    @Test
    @DisplayName("11. Invalid ZIP is rejected")
    void test11_invalidZipIsRejected() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-spiral-01", "exam-active-01")).thenReturn(Optional.of(q1));

        MockMultipartFile notZip = new MockMultipartFile("file", "solution.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", notZip
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Only ZIP archive submissions");
    }

    @Test
    @DisplayName("12. File exceeding configured limit is rejected")
    void test12_fileExceedingLimitIsRejected() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-spiral-01", "exam-active-01")).thenReturn(Optional.of(q1));

        long maxBytes = zipProperties.getMaxCompressedBytes();
        byte[] oversizedBytes = new byte[(int) maxBytes + 1024];

        MockMultipartFile bigZip = new MockMultipartFile("file", "big.zip", "application/zip", oversizedBytes);

        assertThatThrownBy(() -> studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", bigZip
        )).isInstanceOf(ZipValidationException.class)
          .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    @DisplayName("13. Existing submission history is preserved")
    void test13_existingSubmissionHistoryIsPreserved() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(examRepository.findByIdForShare("exam-active-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-spiral-01", "exam-active-01")).thenReturn(Optional.of(q1));

        // Submit 1st attempt
        SubmissionDetailResponse att1 = studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", createZip("att1.zip")
        );

        // Submit 2nd attempt
        SubmissionDetailResponse att2 = studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", createZip("att2.zip")
        );

        assertThat(att1.getId()).isNotEqualTo(att2.getId());
        assertThat(submissionDb).hasSize(2);
        assertThat(submissionDb.containsKey(att1.getId())).isTrue();
        assertThat(submissionDb.containsKey(att2.getId())).isTrue();
    }

    @Test
    @DisplayName("14. Resubmission follows existing policy (intake sequence upsert)")
    void test14_resubmissionFollowsExistingPolicy() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(examRepository.findByIdForShare("exam-active-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-spiral-01", "exam-active-01")).thenReturn(Optional.of(q1));

        studentSubmissionService.submitZip(
                "exam-active-01", "q-spiral-01", "student-01", createZip("v1.zip")
        );

        verify(studentQuestionScoreRepository, times(1)).upsertIntakeSequence(
                anyString(), eq("exam-active-01"), eq("q-spiral-01"), eq("student-01"), anyString()
        );
    }

    @Test
    @DisplayName("15. Submission and grading are associated with correct question")
    void test15_submissionAssociatedWithCorrectQuestion() {
        when(examRepository.findByIdAndStudentId("exam-active-01", "student-01")).thenReturn(Optional.of(activeExam));
        when(examRepository.findByIdForShare("exam-active-01")).thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-01")).thenReturn(Optional.of(student1));
        when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-palindrome-02", "exam-active-01")).thenReturn(Optional.of(q2));

        SubmissionDetailResponse res = studentSubmissionService.submitZip(
                "exam-active-01", "q-palindrome-02", "student-01", createZip("q2_sol.zip")
        );

        Submission sub = submissionDb.get(res.getId());
        assertThat(sub.getQuestion()).isNotNull();
        assertThat(sub.getQuestion().getId()).isEqualTo("q-palindrome-02");
        assertThat(sub.getQuestion().getTitle()).isEqualTo("Chuỗi đối xứng dài nhất");
    }
}
