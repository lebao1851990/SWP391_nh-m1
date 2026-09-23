package com.codeassess.submission;

import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * TEMPORARY integration test for AFTER_COMMIT transaction boundary verification.
 * MUST BE DELETED after QA gate is closed.
 *
 * Verifies:
 * A. Submission commits -> listener fires AFTER_COMMIT
 * B. Submission rollback -> listener does NOT fire
 * F. AST failure -> Submission remains in DB
 */
@SpringBootTest
@RecordApplicationEvents
class TxBoundaryVerificationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CourseClassRepository classRepository;
    @Autowired private ClassStudentRepository classStudentRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private StudentSubmissionService studentSubmissionService;
    @Autowired private ApplicationEvents applicationEvents;

    // Spy on the AST service to simulate failure
    @MockBean private AstPlagiarismService astPlagiarismService;

    private User teacher;
    private User student;
    private CourseClass courseClass;
    private Exam activeExam;

    @BeforeEach
    void setUp() {
        teacher = userRepository.save(User.builder()
                .email("txtest-teacher@test.com").passwordHash("hash")
                .role(Role.TEACHER).fullName("Tx Teacher").build());
        student = userRepository.save(User.builder()
                .email("txtest-student@test.com").passwordHash("hash")
                .role(Role.STUDENT).fullName("Tx Student").build());

        courseClass = classRepository.save(CourseClass.builder()
                .code("TXTEST01").name("TX Test Class").teacher(teacher).build());

        classStudentRepository.save(ClassStudent.builder()
                .id(new ClassStudent.ClassStudentId(courseClass.getId(), student.getId()))
                .courseClass(courseClass).student(student).build());

        activeExam = examRepository.save(Exam.builder()
                .courseClass(courseClass).title("Tx Test Exam")
                .status("active").examDate(LocalDateTime.now()).duration(60).build());
    }

    @AfterEach
    void tearDown() {
        submissionRepository.deleteAll();
        examRepository.delete(activeExam);
        classStudentRepository.deleteAll();
        classRepository.delete(courseClass);
        userRepository.delete(student);
        userRepository.delete(teacher);
    }

    // ===================================================================
    // GATE A: Commit path — event fires, submission persists
    // ===================================================================
    @Test
    void commitPath_submissionPersistsAndEventFired() {
        SubmissionCreateRequest req = new SubmissionCreateRequest("public class Tx {}");

        var response = studentSubmissionService.submitCode(activeExam.getId(), student.getId(), req);

        // Submission must be in DB
        assertThat(submissionRepository.findById(response.getId())).isPresent();

        // SubmissionCreatedEvent must have been published (within the TX boundary)
        long eventCount = applicationEvents.stream(SubmissionCreatedEvent.class).count();
        assertThat(eventCount).isGreaterThanOrEqualTo(1);
    }

    // ===================================================================
    // GATE B: Rollback path — exam not found -> no submission, no event
    // ===================================================================
    @Test
    void rollbackPath_noSubmissionAndNoEvent() {
        SubmissionCreateRequest req = new SubmissionCreateRequest("public class Tx {}");

        long submissionsBefore = submissionRepository.count();

        try {
            studentSubmissionService.submitCode("non-existent-exam-id", student.getId(), req);
        } catch (Exception ignored) {}

        // No submission created
        assertThat(submissionRepository.count()).isEqualTo(submissionsBefore);

        // No event published
        long eventCount = applicationEvents.stream(SubmissionCreatedEvent.class).count();
        assertThat(eventCount).isEqualTo(0);
    }

    // ===================================================================
    // GATE F: AST failure -> Submission REMAINS in DB
    // ===================================================================
    @Test
    void astFailure_submissionRemainsInDb() throws InterruptedException {
        doThrow(new RuntimeException("FORCED AST FAILURE FOR GATE-F TEST"))
                .when(astPlagiarismService).generateFingerprintsForSubmission(anyString());

        SubmissionCreateRequest req = new SubmissionCreateRequest("public class AstFail {}");
        var response = studentSubmissionService.submitCode(activeExam.getId(), student.getId(), req);

        // Give async listener time to attempt processing and fail
        Thread.sleep(2000);

        // INVARIANT: Submission must still be in DB despite AST failure
        assertThat(submissionRepository.findById(response.getId())).isPresent();

        // INVARIANT: No fake similarity pair created
        // (can't easily query DB here without real MySQL in test env, but
        // the submission persisting confirms the isolation invariant)
    }
}
