package com.codeassess.exam.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ExamTimeExpiredException;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TASK-EXAM-CONFIGURATION-AND-STATUS-LIFECYCLE-REFACTOR-01: Runtime Status & Lifecycle Rules Tests")
class ExamRuntimeStatusLifecycleTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private CourseClassRepository courseClassRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    private ExamMapper examMapper = new ExamMapper();

    private TeacherExamService teacherExamService;
    private StudentExamService studentExamService;

    private User teacher;
    private CourseClass courseClass1;
    private CourseClass courseClass2;
    private Exam baseExam;

    @BeforeEach
    void setUp() {
        teacherExamService = new TeacherExamService(
                examRepository,
                examMapper,
                null,
                courseClassRepository,
                submissionRepository
        );

        studentExamService = new StudentExamService(
                examRepository,
                examMapper,
                null,
                examQuestionRepository,
                null
        );

        teacher = User.builder().id("teacher-01").email("teacher@fpt.edu.vn").build();

        courseClass1 = CourseClass.builder()
                .id("class-01")
                .name("CS101-A")
                .code("CS101-A")
                .teacher(teacher)
                .build();

        courseClass2 = CourseClass.builder()
                .id("class-02")
                .name("CS101-B")
                .code("CS101-B")
                .teacher(teacher)
                .build();

        baseExam = Exam.builder()
                .id("exam-test-01")
                .title("Kỳ thi lập trình")
                .courseClass(courseClass1)
                .classes(new HashSet<>(Set.of(courseClass1, courseClass2)))
                .duration(90)
                .status("published")
                .build();
    }

    @Test
    @DisplayName("1. Draft remains DRAFT regardless of schedule")
    void test1_draftRemainsDraft() {
        Exam draftExam = Exam.builder()
                .id("draft-01")
                .status("draft")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).minusHours(1))
                .duration(60)
                .build();

        assertThat(draftExam.getRuntimeStatus()).isEqualTo("draft");
        assertThat(draftExam.isConcluded()).isFalse();

        ExamResponse res = examMapper.toResponse(draftExam);
        assertThat(res.getStatus()).isEqualTo("draft");
    }

    @Test
    @DisplayName("2. Published future exam is UPCOMING")
    void test2_publishedFutureExamIsUpcoming() {
        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        LocalDateTime startTime = now.plusHours(2);

        Exam futureExam = Exam.builder()
                .id("future-01")
                .status("published")
                .examDate(startTime)
                .duration(90)
                .build();

        assertThat(futureExam.getRuntimeStatus(now)).isEqualTo("upcoming");
        assertThat(futureExam.isConcluded(now)).isFalse();

        ExamResponse res = examMapper.toResponse(futureExam);
        assertThat(res.getStatus()).isEqualTo("upcoming");
    }

    @Test
    @DisplayName("3. Exact scheduled start time becomes ACTIVE")
    void test3_exactScheduledStartTimeBecomesActive() {
        LocalDateTime startTime = LocalDateTime.of(2026, 11, 5, 14, 0, 0);
        Exam exam = Exam.builder()
                .id("exact-start-01")
                .status("published")
                .examDate(startTime)
                .duration(90)
                .build();

        // 1 second before start: UPCOMING
        assertThat(exam.getRuntimeStatus(startTime.minusSeconds(1))).isEqualTo("upcoming");

        // Exact start time: ACTIVE
        assertThat(exam.getRuntimeStatus(startTime)).isEqualTo("active");
        assertThat(exam.isConcluded(startTime)).isFalse();
    }

    @Test
    @DisplayName("4. Exam remains ACTIVE during its duration")
    void test4_examRemainsActiveDuringDuration() {
        LocalDateTime startTime = LocalDateTime.of(2026, 11, 5, 14, 0, 0);
        int durationMinutes = 90;
        LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

        Exam exam = Exam.builder()
                .id("active-exam-01")
                .status("published")
                .examDate(startTime)
                .duration(durationMinutes)
                .build();

        // Midway (45 mins in): ACTIVE
        LocalDateTime midTime = startTime.plusMinutes(45);
        assertThat(exam.getRuntimeStatus(midTime)).isEqualTo("active");

        // 1 second before end: ACTIVE
        assertThat(exam.getRuntimeStatus(endTime.minusSeconds(1))).isEqualTo("active");
        assertThat(exam.isConcluded(endTime.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("5. Exact end time becomes COMPLETED")
    void test5_exactEndTimeBecomesCompleted() {
        LocalDateTime startTime = LocalDateTime.of(2026, 11, 5, 14, 0, 0);
        int durationMinutes = 90;
        LocalDateTime endTime = startTime.plusMinutes(durationMinutes); // 15:30:00

        Exam exam = Exam.builder()
                .id("end-exam-01")
                .status("published")
                .examDate(startTime)
                .duration(durationMinutes)
                .build();

        // Exact end time: COMPLETED
        assertThat(exam.getRuntimeStatus(endTime)).isEqualTo("completed");
        assertThat(exam.isConcluded(endTime)).isTrue();

        // After end time: COMPLETED
        assertThat(exam.getRuntimeStatus(endTime.plusMinutes(10))).isEqualTo("completed");
        assertThat(exam.isConcluded(endTime.plusMinutes(10))).isTrue();
    }

    @Test
    @DisplayName("6. Teacher cannot force ACTIVE before start")
    void test6_teacherCannotForceActiveBeforeStart() {
        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        LocalDateTime futureStart = now.plusHours(3);

        baseExam.setExamDate(futureStart);
        baseExam.setStatus("upcoming");

        when(examRepository.findByIdAndCourseClass_Teacher_Id(baseExam.getId(), "teacher-01"))
                .thenReturn(Optional.of(baseExam));

        UpdateExamDto dto = UpdateExamDto.builder()
                .status("active")
                .build();

        assertThatThrownBy(() -> teacherExamService.updateExam(baseExam.getId(), dto, "teacher-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE trước thời gian bắt đầu");
    }

    @Test
    @DisplayName("7. Teacher cannot force COMPLETED before end")
    void test7_teacherCannotForceCompletedBeforeEnd() {
        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        LocalDateTime start = now.minusMinutes(10); // currently active

        baseExam.setExamDate(start);
        baseExam.setDuration(90);
        baseExam.setStatus("active");

        when(examRepository.findByIdAndCourseClass_Teacher_Id(baseExam.getId(), "teacher-01"))
                .thenReturn(Optional.of(baseExam));

        UpdateExamDto dto = UpdateExamDto.builder()
                .status("completed")
                .build();

        assertThatThrownBy(() -> teacherExamService.updateExam(baseExam.getId(), dto, "teacher-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED trước khi hết thời gian làm bài");
    }

    @Test
    @DisplayName("8. Student cannot enter UPCOMING exam (questions hidden before start)")
    void test8_studentCannotEnterUpcomingExam() {
        LocalDateTime futureStart = LocalDateTime.now(Exam.APP_ZONE).plusHours(2);
        baseExam.setExamDate(futureStart);
        baseExam.setDuration(90);
        baseExam.setStatus("upcoming");
        baseExam.setProblemStatement("Secret problem statement");

        when(examRepository.findByIdAndStudentId(baseExam.getId(), "student-01"))
                .thenReturn(Optional.of(baseExam));

        ExamResponse res = studentExamService.getStudentExamById(baseExam.getId(), "student-01");

        assertThat(res.getStatus()).isEqualTo("upcoming");
        assertThat(res.getQuestions()).isEmpty();
        assertThat(res.getTotalQuestions()).isEqualTo(0);
        assertThat(res.getProblemStatement()).isNull();
    }

    @Test
    @DisplayName("9. Student cannot submit before start (UPCOMING)")
    void test9_studentCannotSubmitBeforeStart() {
        LocalDateTime futureStart = LocalDateTime.now(Exam.APP_ZONE).plusHours(1);
        Exam upcomingExam = Exam.builder()
                .id("exam-up-01")
                .examDate(futureStart)
                .duration(60)
                .status("upcoming")
                .build();

        assertThat(upcomingExam.getRuntimeStatus()).isEqualTo("upcoming");
    }

    @Test
    @DisplayName("10. Student cannot submit after deadline (COMPLETED)")
    void test10_studentCannotSubmitAfterDeadline() {
        LocalDateTime pastStart = LocalDateTime.now(Exam.APP_ZONE).minusHours(3);
        Exam completedExam = Exam.builder()
                .id("exam-comp-01")
                .examDate(pastStart)
                .duration(60) // ended 2 hours ago
                .status("active") // stale persisted status in DB!
                .build();

        // Even with stale "active" in DB, runtime status derives "completed"
        assertThat(completedExam.getRuntimeStatus()).isEqualTo("completed");
        assertThat(completedExam.isConcluded()).isTrue();
    }

    @Test
    @DisplayName("11. Teacher cannot modify schedule during ACTIVE exam")
    void test11_teacherCannotModifyScheduleDuringActiveExam() {
        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        LocalDateTime start = now.minusMinutes(20);

        baseExam.setExamDate(start);
        baseExam.setDuration(90);
        baseExam.setStatus("active");

        when(examRepository.findByIdAndCourseClass_Teacher_Id(baseExam.getId(), "teacher-01"))
                .thenReturn(Optional.of(baseExam));

        // Attempt to modify start date
        UpdateExamDto changeDateDto = UpdateExamDto.builder()
                .date(start.plusMinutes(10))
                .build();

        assertThatThrownBy(() -> teacherExamService.updateExam(baseExam.getId(), changeDateDto, "teacher-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thời gian bắt đầu khi bài thi đang diễn ra");

        // Attempt to modify duration
        UpdateExamDto changeDurationDto = UpdateExamDto.builder()
                .durationMinutes(120)
                .build();

        assertThatThrownBy(() -> teacherExamService.updateExam(baseExam.getId(), changeDurationDto, "teacher-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thời lượng khi bài thi đang diễn ra");
    }

    @Test
    @DisplayName("12. Teacher cannot remove a class with existing submissions")
    void test12_teacherCannotRemoveClassWithExistingSubmissions() {
        LocalDateTime futureStart = LocalDateTime.now(Exam.APP_ZONE).plusDays(1);
        baseExam.setExamDate(futureStart);
        baseExam.setStatus("upcoming");

        when(examRepository.findByIdAndCourseClass_Teacher_Id(baseExam.getId(), "teacher-01"))
                .thenReturn(Optional.of(baseExam));

        // class-02 has 3 submissions
        when(submissionRepository.countSubmissionsByExamIdAndClassId(baseExam.getId(), "class-02"))
                .thenReturn(3L);
        when(courseClassRepository.findById("class-02")).thenReturn(Optional.of(courseClass2));
        lenient().when(courseClassRepository.findAllById(List.of("class-01"))).thenReturn(List.of(courseClass1));

        // Teacher tries to keep only class-01 (removing class-02)
        UpdateExamDto removeClassDto = UpdateExamDto.builder()
                .classIds(List.of("class-01"))
                .build();

        assertThatThrownBy(() -> teacherExamService.updateExam(baseExam.getId(), removeClassDto, "teacher-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể xóa lớp học 'CS101-B' khỏi bài thi vì sinh viên trong lớp đã có bài nộp");
    }

    @Test
    @DisplayName("13. Teacher and student pages receive consistent runtime status")
    void test13_teacherAndStudentConsistentRuntimeStatus() {
        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        LocalDateTime pastStart = now.minusHours(2);

        // Stale persisted status in DB is "active"
        Exam examWithStaleDbStatus = Exam.builder()
                .id("exam-sync-01")
                .title("Đề thi cuối kỳ")
                .status("active")
                .examDate(pastStart)
                .duration(60) // ended 1 hour ago
                .classes(new HashSet<>(Set.of(courseClass1)))
                .build();

        ExamResponse response = examMapper.toResponse(examWithStaleDbStatus);

        // Both teacher and student API responses receive the canonical derived status
        assertThat(response.getStatus()).isEqualTo("completed");
        assertThat(response.getIsExpired()).isTrue();
    }
}
