package com.codeassess.exam.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.scoring.entity.ExamRubricCriteria;
import com.codeassess.scoring.repository.ExamRubricCriteriaRepository;
import com.codeassess.submission.dto.response.TeacherSubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherExamServiceTest {

    @Mock
    private ExamRepository examRepository;

    @Spy
    private ExamMapper examMapper = new ExamMapper();

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private CourseClassRepository courseClassRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimilarityPairRepository similarityPairRepository;

    @Mock
    private ExamRubricCriteriaRepository examRubricCriteriaRepository;

    @Mock
    private com.codeassess.exam.repository.ExamQuestionRepository examQuestionRepository;

    @Mock
    private com.codeassess.testcase.repository.TestCaseRepository testCaseRepository;

    private TeacherExamService teacherExamService;

    private User teacher;
    private CourseClass courseClass;
    private Exam exam;

    @BeforeEach
    void setUp() {
        teacher = User.builder().id("teacher-1").email("teacher@fpt.edu.vn").role(Role.TEACHER).build();
        courseClass = CourseClass.builder().id("class-1").code("CSD201").name("Data Structures").teacher(teacher).build();
        exam = Exam.builder()
                .id("exam-1")
                .title("Midterm Exam")
                .courseClass(courseClass)
                .classes(new HashSet<>(List.of(courseClass)))
                .examDate(LocalDateTime.now().plusDays(2))
                .duration(90)
                .status("upcoming")
                .build();

        teacherExamService = new TeacherExamService(
                examRepository, examMapper, classStudentRepository, courseClassRepository, submissionRepository, userRepository
        );
        ReflectionTestUtils.setField(teacherExamService, "similarityPairRepository", similarityPairRepository);
        ReflectionTestUtils.setField(teacherExamService, "examRubricCriteriaRepository", examRubricCriteriaRepository);
    }

    // =========================================================================
    // 1. getTeacherExams & populateCounts
    // =========================================================================
    @Nested
    @DisplayName("getTeacherExams Tests")
    class GetTeacherExamsTests {

        @Test
        @DisplayName("getTeacherExams returns teacher exams with enrolled counts using classIds")
        void testGetTeacherExams() {
            when(examRepository.findByCourseClass_Teacher_Id("teacher-1")).thenReturn(List.of(exam));
            when(classStudentRepository.countDistinctStudentsByClassIds(any())).thenReturn(35L);
            when(submissionRepository.countByExam_Id("exam-1")).thenReturn(12L);

            List<ExamResponse> result = teacherExamService.getTeacherExams("teacher-1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("Midterm Exam");
            assertThat(result.get(0).getEnrolledCount()).isEqualTo(35);
            assertThat(result.get(0).getSubmittedCount()).isEqualTo(12);
        }

        @Test
        @DisplayName("getTeacherExams populates count with single classId when classIds is null")
        void testGetTeacherExams_withSingleClassId_populatesCountUsingSingleClassRepo() {
            Exam singleClassExam = Exam.builder()
                    .id("exam-single")
                    .title("Single Class Exam")
                    .courseClass(courseClass)
                    .examDate(LocalDateTime.now().plusDays(1))
                    .duration(60)
                    .status("active")
                    .build();

            ExamResponse customResponse = ExamResponse.builder()
                    .id("exam-single")
                    .title("Single Class Exam")
                    .classId("class-1")
                    .classIds(null)
                    .build();

            doReturn(customResponse).when(examMapper).toResponse(singleClassExam);
            when(examRepository.findByCourseClass_Teacher_Id("teacher-1")).thenReturn(List.of(singleClassExam));
            when(classStudentRepository.countByCourseClass_Id("class-1")).thenReturn(20L);
            when(submissionRepository.countByExam_Id("exam-single")).thenReturn(5L);

            List<ExamResponse> result = teacherExamService.getTeacherExams("teacher-1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEnrolledCount()).isEqualTo(20);
            assertThat(result.get(0).getSubmittedCount()).isEqualTo(5);
            verify(classStudentRepository).countByCourseClass_Id("class-1");
            verify(classStudentRepository, never()).countDistinctStudentsByClassIds(any());
        }

        @Test
        @DisplayName("getTeacherExams returns empty list when teacher has no exams")
        void testGetTeacherExams_emptyList() {
            when(examRepository.findByCourseClass_Teacher_Id("teacher-1")).thenReturn(Collections.emptyList());

            List<ExamResponse> result = teacherExamService.getTeacherExams("teacher-1");

            assertThat(result).isEmpty();
            verifyNoInteractions(classStudentRepository);
            verifyNoInteractions(submissionRepository);
        }
    }

    // =========================================================================
    // 2. getTeacherExamById
    // =========================================================================
    @Nested
    @DisplayName("getTeacherExamById Tests")
    class GetTeacherExamByIdTests {

        @Test
        @DisplayName("getTeacherExamById returns exam details with rubric criteria when configured")
        void testGetTeacherExamById_success_withRubricCriteria() {
            ExamRubricCriteria criteria1 = ExamRubricCriteria.builder()
                    .id("crit-1")
                    .name("Code Quality")
                    .description("Clean code and formatting")
                    .maxScore(BigDecimal.valueOf(4.0))
                    .build();
            ExamRubricCriteria criteria2 = ExamRubricCriteria.builder()
                    .id("crit-2")
                    .name("Functionality")
                    .description("All tests passing")
                    .maxScore(BigDecimal.valueOf(6.0))
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(classStudentRepository.countDistinctStudentsByClassIds(any())).thenReturn(40L);
            when(submissionRepository.countByExam_Id("exam-1")).thenReturn(8L);
            when(examRubricCriteriaRepository.findByExam_IdOrderByDisplayOrderAsc("exam-1")).thenReturn(List.of(criteria1, criteria2));

            ExamResponse response = teacherExamService.getTeacherExamById("exam-1", "teacher-1");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("exam-1");
            assertThat(response.getTitle()).isEqualTo("Midterm Exam");
            assertThat(response.getEnrolledCount()).isEqualTo(40);
            assertThat(response.getSubmittedCount()).isEqualTo(8);
            assertThat(response.getRubricCriteria()).hasSize(2);
            assertThat(response.getRubricCriteria().get(0).getName()).isEqualTo("Code Quality");
            assertThat(response.getRubricCriteria().get(0).getMaxScore()).isEqualByComparingTo("4.0");
            assertThat(response.getRubricCriteria().get(1).getName()).isEqualTo("Functionality");
        }

        @Test
        @DisplayName("getTeacherExamById succeeds when rubric repository is null")
        void testGetTeacherExamById_success_withoutRubricRepository() {
            ReflectionTestUtils.setField(teacherExamService, "examRubricCriteriaRepository", null);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(classStudentRepository.countDistinctStudentsByClassIds(any())).thenReturn(30L);

            ExamResponse response = teacherExamService.getTeacherExamById("exam-1", "teacher-1");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("exam-1");
            assertThat(response.getRubricCriteria()).isNull();
        }

        @Test
        @DisplayName("getTeacherExamById throws ResourceNotFoundException when exam not found or not owned")
        void testGetTeacherExamById_notOwned_throwsResourceNotFoundException() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("unknown-exam", "teacher-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.getTeacherExamById("unknown-exam", "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Exam not found or access denied");

            verifyNoInteractions(classStudentRepository);
            verifyNoInteractions(submissionRepository);
            verifyNoInteractions(examRubricCriteriaRepository);
        }
    }

    // =========================================================================
    // 3. getExamSubmissions & Plagiarism Status Mapping
    // =========================================================================
    @Nested
    @DisplayName("getExamSubmissions Tests")
    class GetExamSubmissionsTests {

        @Test
        @DisplayName("getExamSubmissions calculates plagiarism statuses and max similarity (FLAGGED)")
        void testGetExamSubmissions_WithPlagiarismStatus() {
            User studentA = User.builder().id("student-a").fullName("Alice").email("alice@fpt.edu.vn").build();
            User studentB = User.builder().id("student-b").fullName("Bob").email("bob@fpt.edu.vn").build();

            Submission subA = Submission.builder().id("sub-a").exam(exam).student(studentA).status("GRADED").totalScore(BigDecimal.valueOf(8.5)).build();
            Submission subB = Submission.builder().id("sub-b").exam(exam).student(studentB).status("GRADED").totalScore(BigDecimal.valueOf(9.0)).build();

            SimilarityPair pair = SimilarityPair.builder()
                    .id(1L)
                    .exam(exam)
                    .submissionA(subA)
                    .submissionB(subB)
                    .similarityRate(BigDecimal.valueOf(82.50))
                    .isFlagged(true)
                    .isActive(true)
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(List.of(pair));
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(subA, subB));

            List<TeacherSubmissionListResponse> submissions = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(submissions).hasSize(2);
            assertThat(submissions.get(0).getPlagiarismStatus()).isEqualTo("FLAGGED");
            assertThat(submissions.get(0).getMaxSimilarityRate()).isEqualByComparingTo("82.50");
        }

        @Test
        @DisplayName("getExamSubmissions throws ResourceNotFoundException when exam not found or not owned")
        void testGetExamSubmissions_notOwned_throwsResourceNotFoundException() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-other")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.getExamSubmissions("exam-1", "teacher-other"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Exam not found or access denied");

            verify(submissionRepository, never()).findByExam_Id(any());
        }

        @Test
        @DisplayName("getExamSubmissions sorts and joins multiple course codes with comma")
        void testGetExamSubmissions_multiClassCourseCode_sortedJoined() {
            CourseClass classA = CourseClass.builder().id("class-a").code("SE101").teacher(teacher).build();
            CourseClass classB = CourseClass.builder().id("class-b").code("CSD201").teacher(teacher).build();
            Exam multiClassExam = Exam.builder()
                    .id("exam-multi")
                    .title("Multi-class Exam")
                    .classes(new HashSet<>(List.of(classA, classB)))
                    .build();

            Submission sub = Submission.builder()
                    .id("sub-1")
                    .exam(multiClassExam)
                    .status("SUBMITTED")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-multi", "teacher-1")).thenReturn(Optional.of(multiClassExam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-multi")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-multi")).thenReturn(List.of(sub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-multi", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getCourseCode()).isEqualTo("CSD201, SE101");
        }

        @Test
        @DisplayName("getExamSubmissions uses single courseClass code when classes set is null or empty")
        void testGetExamSubmissions_singleCourseClass_codeFormatted() {
            Exam singleExam = Exam.builder()
                    .id("exam-single")
                    .title("Single Exam")
                    .courseClass(courseClass)
                    .classes(null)
                    .build();

            Submission sub = Submission.builder().id("sub-1").exam(singleExam).status("SUBMITTED").build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-single", "teacher-1")).thenReturn(Optional.of(singleExam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-single")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-single")).thenReturn(List.of(sub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-single", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getCourseCode()).isEqualTo("CSD201");
        }

        @Test
        @DisplayName("getExamSubmissions returns empty courseCode when both classes and courseClass are null")
        void testGetExamSubmissions_noClasses_courseCodeEmpty() {
            Exam emptyClassExam = Exam.builder()
                    .id("exam-empty")
                    .title("Empty Exam")
                    .courseClass(null)
                    .classes(null)
                    .build();

            Submission sub = Submission.builder().id("sub-1").exam(emptyClassExam).status("SUBMITTED").build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-empty", "teacher-1")).thenReturn(Optional.of(emptyClassExam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-empty")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-empty")).thenReturn(List.of(sub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-empty", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getCourseCode()).isEqualTo("");
        }

        @Test
        @DisplayName("getExamSubmissions marks SAFE when maxSim exists and isFlagged is false")
        void testGetExamSubmissions_plagiarismStatus_Safe() {
            Submission sub1 = Submission.builder().id("sub-1").exam(exam).status("GRADED").build();
            Submission sub2 = Submission.builder().id("sub-2").exam(exam).status("GRADED").build();

            SimilarityPair pair = SimilarityPair.builder()
                    .submissionA(sub2)
                    .submissionB(sub1) // sub1 is submissionB
                    .similarityRate(BigDecimal.valueOf(45.50))
                    .isFlagged(false)
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(List.of(pair));
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(sub1));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getPlagiarismStatus()).isEqualTo("SAFE");
            assertThat(list.get(0).getMaxSimilarityRate()).isEqualByComparingTo("45.50");
        }

        @Test
        @DisplayName("getExamSubmissions marks NOT_CHECKED when no similarity pairs match submission")
        void testGetExamSubmissions_plagiarismStatus_NotChecked() {
            Submission sub1 = Submission.builder().id("sub-1").exam(exam).status("SUBMITTED").build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(sub1));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getPlagiarismStatus()).isEqualTo("NOT_CHECKED");
            assertThat(list.get(0).getMaxSimilarityRate()).isNull();
        }

        @Test
        @DisplayName("getExamSubmissions marks FLAGGED when multiple pairs exist and at least one is flagged")
        void testGetExamSubmissions_plagiarismStatus_FlaggedTakesPrecedence() {
            Submission subTarget = Submission.builder().id("sub-target").exam(exam).status("GRADED").build();
            Submission subOther1 = Submission.builder().id("sub-other1").exam(exam).status("GRADED").build();
            Submission subOther2 = Submission.builder().id("sub-other2").exam(exam).status("GRADED").build();

            SimilarityPair pair1 = SimilarityPair.builder()
                    .submissionA(subTarget)
                    .submissionB(subOther1)
                    .similarityRate(BigDecimal.valueOf(30.00))
                    .isFlagged(false)
                    .build();

            SimilarityPair pair2 = SimilarityPair.builder()
                    .submissionA(subTarget)
                    .submissionB(subOther2)
                    .similarityRate(BigDecimal.valueOf(78.50))
                    .isFlagged(true)
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(List.of(pair1, pair2));
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(subTarget));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getPlagiarismStatus()).isEqualTo("FLAGGED");
            assertThat(list.get(0).getMaxSimilarityRate()).isEqualByComparingTo("78.50");
        }

        @Test
        @DisplayName("getExamSubmissions uses evaluatedAt for COMPLETED or EVALUATED status")
        void testGetExamSubmissions_submittedAt_evaluatedAtUsedForCompletedOrEvaluated() {
            LocalDateTime createdTime = LocalDateTime.of(2026, 10, 29, 8, 0);
            LocalDateTime evaluatedTime = LocalDateTime.of(2026, 10, 29, 10, 0);

            Submission completedSub = Submission.builder()
                    .id("sub-comp")
                    .status("COMPLETED")
                    .createdAt(createdTime)
                    .evaluatedAt(evaluatedTime)
                    .build();

            Submission evaluatedSub = Submission.builder()
                    .id("sub-eval")
                    .status("EVALUATED")
                    .createdAt(createdTime)
                    .evaluatedAt(evaluatedTime)
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(completedSub, evaluatedSub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(2);
            assertThat(list.get(0).getSubmittedAt()).isEqualTo(evaluatedTime);
            assertThat(list.get(1).getSubmittedAt()).isEqualTo(evaluatedTime);
        }

        @Test
        @DisplayName("getExamSubmissions falls back to createdAt when evaluatedAt is null or status is pending")
        void testGetExamSubmissions_submittedAt_fallbackToCreatedAt() {
            LocalDateTime createdTime = LocalDateTime.of(2026, 10, 29, 8, 0);
            LocalDateTime evaluatedTime = LocalDateTime.of(2026, 10, 29, 10, 0);

            Submission compNullEvalSub = Submission.builder()
                    .id("sub-1")
                    .status("COMPLETED")
                    .createdAt(createdTime)
                    .evaluatedAt(null)
                    .build();

            Submission submittedStatusSub = Submission.builder()
                    .id("sub-2")
                    .status("SUBMITTED")
                    .createdAt(createdTime)
                    .evaluatedAt(evaluatedTime) // status not completed/evaluated -> must fallback to createdAt
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(compNullEvalSub, submittedStatusSub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(2);
            assertThat(list.get(0).getSubmittedAt()).isEqualTo(createdTime);
            assertThat(list.get(1).getSubmittedAt()).isEqualTo(createdTime);
        }

        @Test
        @DisplayName("getExamSubmissions handles null similarityPairRepository safely")
        void testGetExamSubmissions_whenSimilarityPairRepositoryNull_returnsWithoutNpe() {
            ReflectionTestUtils.setField(teacherExamService, "similarityPairRepository", null);
            Submission sub = Submission.builder().id("sub-nosim").exam(exam).status("SUBMITTED").build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(sub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getPlagiarismStatus()).isEqualTo("NOT_CHECKED");
            assertThat(list.get(0).getMaxSimilarityRate()).isNull();
        }

        @Test
        @DisplayName("getExamSubmissions handles null student safely without NPE")
        void testGetExamSubmissions_nullStudentHandling() {
            Submission nullStudentSub = Submission.builder()
                    .id("sub-null-stud")
                    .student(null)
                    .status("SUBMITTED")
                    .build();

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(similarityPairRepository.findByExam_IdAndIsActiveTrue("exam-1")).thenReturn(Collections.emptyList());
            when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(nullStudentSub));

            List<TeacherSubmissionListResponse> list = teacherExamService.getExamSubmissions("exam-1", "teacher-1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getStudentName()).isNull();
            assertThat(list.get(0).getStudentEmail()).isNull();
        }
    }

    // =========================================================================
    // 4. createExam
    // =========================================================================
    @Nested
    @DisplayName("createExam Tests")
    class CreateExamTests {

        @Test
        @DisplayName("createExam successfully persists exam for assigned classIds")
        void testCreateExam_Success() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Final Practical Exam");
            dto.setClassIds(List.of("class-1"));
            dto.setDate(LocalDateTime.now().plusDays(5));
            dto.setDurationMinutes(120);

            when(courseClassRepository.findAllById(List.of("class-1"))).thenReturn(List.of(courseClass));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> {
                Exam e = inv.getArgument(0);
                e.setId("exam-new");
                return e;
            });

            ExamResponse response = teacherExamService.createExam(dto, "teacher-1");

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Final Practical Exam");
        }

        @Test
        @DisplayName("createExam successfully resolves single classId")
        void testCreateExam_success_withSingleClassId() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Quiz 1");
            dto.setClassId("class-1");
            dto.setDate(LocalDateTime.now().plusDays(1));
            dto.setDurationMinutes(45);

            when(courseClassRepository.findAllById(List.of("class-1"))).thenReturn(List.of(courseClass));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> {
                Exam e = inv.getArgument(0);
                e.setId("exam-quiz");
                return e;
            });

            ExamResponse response = teacherExamService.createExam(dto, "teacher-1");

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Quiz 1");
            verify(courseClassRepository).findAllById(List.of("class-1"));
        }

        @Test
        @DisplayName("createExam throws IllegalArgumentException when class list is empty")
        void testCreateExam_emptyClassList_throwsIllegalArgumentException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("No Class Exam");
            dto.setClassIds(Collections.emptyList());
            dto.setClassId(null);
            dto.setNewClassCode(null);

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("At least one class must be selected for the exam");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("createExam throws ResourceNotFoundException when one or more classes not found")
        void testCreateExam_classNotFound_throwsResourceNotFoundException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Exam Missing Class");
            dto.setClassIds(List.of("class-1", "class-missing"));

            when(courseClassRepository.findAllById(any())).thenReturn(List.of(courseClass)); // Only returns 1 of 2

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("One or more classes not found or access denied");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("createExam throws ResourceNotFoundException if class belongs to another teacher")
        void testCreateExam_ClassNotOwned() {
            User otherTeacher = User.builder().id("other-teacher").build();
            CourseClass otherClass = CourseClass.builder().id("class-2").teacher(otherTeacher).build();

            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Final Exam");
            dto.setClassIds(List.of("class-2"));

            when(courseClassRepository.findAllById(List.of("class-2"))).thenReturn(List.of(otherClass));

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("access denied");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("createExam with newClassCode auto-creates class with default semester and name")
        void testCreateExam_quickCreate_newClass_successWithDefaults() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Practical Exam 2");
            dto.setNewClassCode("  csd202  ");
            dto.setDate(LocalDateTime.now().plusDays(3));
            dto.setDurationMinutes(90);

            when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
            when(courseClassRepository.findByCode("CSD202")).thenReturn(Optional.empty());

            CourseClass savedClass = CourseClass.builder()
                    .id("class-new")
                    .code("CSD202")
                    .name("CSD202")
                    .semester("Fall 2026")
                    .teacher(teacher)
                    .build();

            when(courseClassRepository.save(any(CourseClass.class))).thenReturn(savedClass);
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> {
                Exam e = inv.getArgument(0);
                e.setId("exam-csd202");
                return e;
            });

            ExamResponse response = teacherExamService.createExam(dto, "teacher-1");

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Practical Exam 2");

            ArgumentCaptor<CourseClass> classCaptor = ArgumentCaptor.forClass(CourseClass.class);
            verify(courseClassRepository).save(classCaptor.capture());
            assertThat(classCaptor.getValue().getCode()).isEqualTo("CSD202");
            assertThat(classCaptor.getValue().getName()).isEqualTo("CSD202");
            assertThat(classCaptor.getValue().getSemester()).isEqualTo("Fall 2026");
            assertThat(classCaptor.getValue().getTeacher()).isEqualTo(teacher);
        }

        @Test
        @DisplayName("createExam with newClassCode uses explicit semester and className when provided")
        void testCreateExam_quickCreate_explicitSemesterAndClassName() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Advanced Algorithms");
            dto.setNewClassCode("csd301");
            dto.setNewClassName("Algorithms & Optimization");
            dto.setSemester("Spring 2027");

            when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
            when(courseClassRepository.findByCode("CSD301")).thenReturn(Optional.empty());

            CourseClass savedClass = CourseClass.builder()
                    .id("class-csd301")
                    .code("CSD301")
                    .name("Algorithms & Optimization")
                    .semester("Spring 2027")
                    .teacher(teacher)
                    .build();

            when(courseClassRepository.save(any(CourseClass.class))).thenReturn(savedClass);
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            ExamResponse response = teacherExamService.createExam(dto, "teacher-1");

            assertThat(response).isNotNull();
            ArgumentCaptor<CourseClass> classCaptor = ArgumentCaptor.forClass(CourseClass.class);
            verify(courseClassRepository).save(classCaptor.capture());
            assertThat(classCaptor.getValue().getCode()).isEqualTo("CSD301");
            assertThat(classCaptor.getValue().getName()).isEqualTo("Algorithms & Optimization");
            assertThat(classCaptor.getValue().getSemester()).isEqualTo("Spring 2027");
        }

        @Test
        @DisplayName("createExam with newClassCode reuses existing class owned by same teacher")
        void testCreateExam_quickCreate_existingClassOwnedBySameTeacher() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Midterm Retake");
            dto.setNewClassCode("CSD201");

            when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
            when(courseClassRepository.findByCode("CSD201")).thenReturn(Optional.of(courseClass));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            ExamResponse response = teacherExamService.createExam(dto, "teacher-1");

            assertThat(response).isNotNull();
            verify(courseClassRepository, never()).save(any());
        }

        @Test
        @DisplayName("createExam with newClassCode throws exception if existing class owned by another teacher")
        void testCreateExam_quickCreate_existingClassBelongsToOtherTeacher_throwsException() {
            User otherTeacher = User.builder().id("teacher-other").build();
            CourseClass otherClass = CourseClass.builder().id("class-other").code("CSD201").teacher(otherTeacher).build();

            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Exam Clash");
            dto.setNewClassCode("CSD201");

            when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
            when(courseClassRepository.findByCode("CSD201")).thenReturn(Optional.of(otherClass));

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Class already belongs to another teacher: CSD201");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("createExam with newClassCode throws exception when teacher user not found")
        void testCreateExam_quickCreate_teacherNotFound_throwsResourceNotFoundException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setNewClassCode("CSD201");

            when(userRepository.findById("teacher-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Teacher not found with id: teacher-1");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("createExam with newClassCode throws IllegalStateException when userRepository is null")
        void testCreateExam_quickCreate_userRepositoryNull_throwsIllegalStateException() {
            TeacherExamService serviceWithoutUserRepo = new TeacherExamService(
                    examRepository, examMapper, classStudentRepository, courseClassRepository, submissionRepository
            );

            CreateExamDto dto = new CreateExamDto();
            dto.setNewClassCode("CSD201");

            assertThatThrownBy(() -> serviceWithoutUserRepo.createExam(dto, "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("UserRepository not initialized");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: createExam with empty or whitespace title throws IllegalArgumentException")
        void testCreateExam_WhitespaceTitle_ThrowsIllegalArgumentException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("   ");
            dto.setClassIds(List.of("class-1"));
            dto.setDate(LocalDateTime.now().plusDays(2));
            dto.setDurationMinutes(60);

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tiêu đề bài thi không được để trống");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: createExam with title exceeding 255 chars throws IllegalArgumentException")
        void testCreateExam_TitleExceeds255_ThrowsIllegalArgumentException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("A".repeat(256));
            dto.setClassIds(List.of("class-1"));
            dto.setDate(LocalDateTime.now().plusDays(2));
            dto.setDurationMinutes(60);

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vượt quá 255 ký tự");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: createExam with invalid duration throws IllegalArgumentException")
        void testCreateExam_InvalidDuration_ThrowsIllegalArgumentException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Duration Test Exam");
            dto.setClassIds(List.of("class-1"));
            dto.setDate(LocalDateTime.now().plusDays(2));
            dto.setDurationMinutes(0);

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Thời lượng bài thi phải từ 1 đến 1440 phút");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: createExam with invalid programming language throws IllegalArgumentException")
        void testCreateExam_InvalidProgrammingLanguage_ThrowsIllegalArgumentException() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Invalid Lang Exam");
            dto.setClassIds(List.of("class-1"));
            dto.setDate(LocalDateTime.now().plusDays(2));
            dto.setDurationMinutes(60);
            dto.setProgrammingLanguage("Ruby");

            assertThatThrownBy(() -> teacherExamService.createExam(dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ngôn ngữ lập trình không hợp lệ");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: createExam with valid programming language and draft status succeeds")
        void testCreateExam_ValidProgrammingLanguageAndStatus_Succeeds() {
            CreateExamDto dto = new CreateExamDto();
            dto.setTitle("Python Final Exam");
            dto.setClassIds(List.of("class-1"));
            dto.setDate(LocalDateTime.now().plusDays(2));
            dto.setDurationMinutes(90);
            dto.setProgrammingLanguage("Python");
            dto.setStatus("draft");

            when(courseClassRepository.findAllById(List.of("class-1"))).thenReturn(List.of(courseClass));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> {
                Exam e = inv.getArgument(0);
                e.setId("exam-py");
                return e;
            });

            ExamResponse response = teacherExamService.createExam(dto, "teacher-1");

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Python Final Exam");
        }
    }

    // =========================================================================
    // 5. updateExam & publishExam
    // =========================================================================
    @Nested
    @DisplayName("updateExam and publishExam Tests")
    class UpdateAndPublishExamTests {

        @Test
        @DisplayName("updateExam updates title and publication status (future date -> upcoming)")
        void testUpdateExam_Success() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setTitle("Updated Title");
            dto.setStatus("published");

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            ExamResponse response = teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(response.getTitle()).isEqualTo("Updated Title");
            assertThat(exam.getStatus()).isEqualTo("upcoming"); // Future date + published -> upcoming
        }

        @Test
        @DisplayName("updateExam throws ResourceNotFoundException when exam not found or not owned")
        void testUpdateExam_notOwned_throwsResourceNotFoundException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setTitle("New Title");

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-other")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-other"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Exam not found or access denied");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateExam with published status and past/null date sets status to active")
        void testUpdateExam_statusPublished_pastDate_setsActive() {
            exam.setExamDate(LocalDateTime.now().minusDays(1)); // past date

            UpdateExamDto dto = new UpdateExamDto();
            dto.setStatus("published");

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            ExamResponse response = teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(exam.getStatus()).isEqualTo("active");
        }

        @Test
        @DisplayName("updateExam with custom status sets status directly in lowercase")
        void testUpdateExam_otherStatus_setsDirectly() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setStatus("DRAFT");

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(exam.getStatus()).isEqualTo("draft");
        }

        @Test
        @DisplayName("updateExam updates date and duration")
        void testUpdateExam_updateDateAndDuration() {
            LocalDateTime newDate = LocalDateTime.now().plusDays(10);
            UpdateExamDto dto = new UpdateExamDto();
            dto.setDate(newDate);
            dto.setDurationMinutes(180);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(exam.getExamDate()).isEqualTo(newDate);
            assertThat(exam.getDuration()).isEqualTo(180);
        }

        @Test
        @DisplayName("updateExam updates assigned classes when classes valid and owned")
        void testUpdateExam_updateClasses_success() {
            CourseClass newClass = CourseClass.builder().id("class-2").code("CSD301").teacher(teacher).build();
            UpdateExamDto dto = new UpdateExamDto();
            dto.setClassIds(List.of("class-2"));

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(courseClassRepository.findAllById(List.of("class-2"))).thenReturn(List.of(newClass));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(exam.getCourseClass()).isEqualTo(newClass);
            assertThat(exam.getClasses()).contains(newClass);
        }

        @Test
        @DisplayName("updateExam updates assigned classes when single classId provided")
        void testUpdateExam_updateClasses_singleClassId_success() {
            CourseClass newClass = CourseClass.builder().id("class-single").code("PRN211").teacher(teacher).build();
            UpdateExamDto dto = new UpdateExamDto();
            dto.setClassId("class-single");
            dto.setClassIds(null);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(courseClassRepository.findAllById(List.of("class-single"))).thenReturn(List.of(newClass));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(exam.getCourseClass()).isEqualTo(newClass);
            assertThat(exam.getClasses()).contains(newClass);
            verify(courseClassRepository).findAllById(List.of("class-single"));
        }

        @Test
        @DisplayName("updateExam throws IllegalArgumentException when updated class list is empty")
        void testUpdateExam_updateClasses_emptyList_throwsIllegalArgumentException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setClassIds(Collections.emptyList());

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Exam must have at least one assigned class");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateExam throws ResourceNotFoundException when one of updated classes not found")
        void testUpdateExam_updateClasses_notAllFound_throwsResourceNotFoundException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setClassIds(List.of("class-1", "class-missing"));

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(courseClassRepository.findAllById(any())).thenReturn(List.of(courseClass));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("One or more classes not found or access denied");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateExam throws ResourceNotFoundException when updated class owned by other teacher")
        void testUpdateExam_updateClasses_classNotOwned_throwsResourceNotFoundException() {
            User otherTeacher = User.builder().id("other-teacher").build();
            CourseClass otherClass = CourseClass.builder().id("class-other").teacher(otherTeacher).build();

            UpdateExamDto dto = new UpdateExamDto();
            dto.setClassIds(List.of("class-other"));

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(courseClassRepository.findAllById(List.of("class-other"))).thenReturn(List.of(otherClass));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Class not found or access denied: class-other");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("publishExam sets upcoming when examDate is in the future")
        void testPublishExam_futureDate_setsUpcoming() {
            exam.setExamDate(LocalDateTime.now().plusDays(2));

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            ExamResponse response = teacherExamService.publishExam("exam-1", "teacher-1");

            assertThat(exam.getStatus()).isEqualTo("upcoming");
            verify(examRepository).save(exam);
        }

        @Test
        @DisplayName("publishExam sets active when examDate is in past or null")
        void testPublishExam_pastOrNullDate_setsActive() {
            exam.setExamDate(LocalDateTime.now().minusHours(1));

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            ExamResponse response = teacherExamService.publishExam("exam-1", "teacher-1");

            assertThat(exam.getStatus()).isEqualTo("active");
            verify(examRepository).save(exam);
        }


        @Test
        @DisplayName("FE-VAL-01B: publishExam without questions throws IllegalStateException")
        void testPublishExam_NoQuestions_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chưa có câu hỏi nào");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01B: publishExam with question sum not 10.00 throws IllegalStateException")
        void testPublishExam_QuestionSumNot10_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Q1").description("Problem 1").maxScore(BigDecimal.valueOf(8.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("phải bằng chính xác 10.00 điểm");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01B: publishExam with question missing public test case throws IllegalStateException")
        void testPublishExam_MissingPublicTestCase_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Q1").description("Problem 1").maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            // Only hidden test cases
            com.codeassess.testcase.entity.TestCase tc1 = com.codeassess.testcase.entity.TestCase.builder()
                    .id("tc-1").name("TC Hidden").expectedOutput("42").points(BigDecimal.valueOf(10.00)).isHidden(true).build();
            when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tối thiểu 1 test case công khai");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01B: publishExam with missing expectedOutput in test case throws IllegalStateException")
        void testPublishExam_MissingExpectedOutput_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Q1").description("Problem 1").maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            com.codeassess.testcase.entity.TestCase tc1 = com.codeassess.testcase.entity.TestCase.builder()
                    .id("tc-1").name("TC Public").expectedOutput("   ").points(BigDecimal.valueOf(10.00)).isHidden(false).build();
            when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chưa có kết quả mong đợi (Expected Output)");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01B: publishExam with whitespace-only inputData throws IllegalStateException")
        void testPublishExam_WhitespaceInput_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Q1").description("Problem 1").maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            com.codeassess.testcase.entity.TestCase tc1 = com.codeassess.testcase.entity.TestCase.builder()
                    .id("tc-1").name("TC Public").inputData(" \t\n ").expectedOutput("42").points(BigDecimal.valueOf(10.00)).isHidden(false).build();
            when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dữ liệu đầu vào chỉ chứa khoảng trắng");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01B: publishExam with complete valid questions and test cases succeeds and serializes with locks")
        void testPublishExam_ValidCompleteExam_PublishesSuccessfully() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            exam.setStatus("draft");
            exam.setExamDate(LocalDateTime.now().plusDays(2));
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Two Sum").description("Problem description").maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));
            when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(q1));

            com.codeassess.testcase.entity.TestCase tc1 = com.codeassess.testcase.entity.TestCase.builder()
                    .id("tc-1").name("Sample 1").inputData("2 7 11 15\n9").expectedOutput("0 1").points(BigDecimal.valueOf(5.00)).isHidden(false).build();
            com.codeassess.testcase.entity.TestCase tc2 = com.codeassess.testcase.entity.TestCase.builder()
                    .id("tc-2").name("Hidden 1").inputData("3 2 4\n6").expectedOutput("1 2").points(BigDecimal.valueOf(5.00)).isHidden(true).build();
            when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1, tc2));

            ExamResponse response = teacherExamService.publishExam("exam-1", "teacher-1");

            assertThat(response).isNotNull();
            assertThat(exam.getStatus()).isEqualTo("upcoming");
            verify(examRepository).findByIdForUpdate("exam-1");
            verify(examQuestionRepository).findByIdAndExam_IdForUpdate("q-1", "exam-1");
            verify(examRepository).save(exam);
        }

        @Test
        @DisplayName("FE-VAL-01B: publishExam validation failure leaves exam status unchanged as draft")
        void testPublishExam_ValidationFailure_LeavesExamStatusUnchanged() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            exam.setStatus("draft");
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Q1").description("Problem 1").maxScore(BigDecimal.valueOf(7.50)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("phải bằng chính xác 10.00 điểm");

            assertThat(exam.getStatus()).isEqualTo("draft");
            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("publishExam throws ResourceNotFoundException when exam not found")
        void testPublishExam_notOwned_throwsResourceNotFoundException() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-other")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-other"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Exam not found or access denied");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: updateExam with whitespace-only title throws IllegalArgumentException")
        void testUpdateExam_WhitespaceTitle_ThrowsIllegalArgumentException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setTitle("   ");

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tiêu đề bài thi không được để trống");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: updateExam with title exceeding 255 chars throws IllegalArgumentException")
        void testUpdateExam_TitleExceeds255_ThrowsIllegalArgumentException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setTitle("X".repeat(256));

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vượt quá 255 ký tự");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: updateExam with invalid duration throws IllegalArgumentException")
        void testUpdateExam_InvalidDuration_ThrowsIllegalArgumentException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setDurationMinutes(0);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Thời lượng bài thi phải từ 1 đến 1440 phút");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: updateExam with invalid programming language throws IllegalArgumentException")
        void testUpdateExam_InvalidProgrammingLanguage_ThrowsIllegalArgumentException() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setProgrammingLanguage("Rust");

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ngôn ngữ lập trình không hợp lệ");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: updateExam with status published runs full publish validation and blocks API bypass")
        void testUpdateExam_StatusPublishedDirectBypass_TriggersPublishValidation() {
            UpdateExamDto dto = new UpdateExamDto();
            dto.setStatus("published");

            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> teacherExamService.updateExam("exam-1", dto, "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chưa có câu hỏi nào");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: publishExam with question title exceeding 255 chars throws IllegalStateException")
        void testPublishExam_QuestionTitleExceeds255_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("T".repeat(256)).description("Valid Problem Statement").maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("vượt quá giới hạn 255 ký tự");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: publishExam with whitespace problem statement throws IllegalStateException")
        void testPublishExam_QuestionProblemStatementWhitespace_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Valid Title").description("     ").maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chưa có nội dung đề bài");

            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("FE-VAL-01C: publishExam with valid rich Markdown problem statement succeeds and preserves content")
        void testPublishExam_ValidMarkdownProblemStatement_PreservedAndPublishesSuccessfully() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            exam.setStatus("draft");
            exam.setExamDate(LocalDateTime.now().plusDays(2));
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            String richMarkdown = "# Bài toán Ba Lan ngược (RPN)\n\nCho biểu thức dạng Postfix...\n\n```java\npublic int evalRPN(String[] tokens) {\n    return 0;\n}\n```";
            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Evaluate RPN").description(richMarkdown).maxScore(BigDecimal.valueOf(10.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));
            when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-1", "exam-1")).thenReturn(Optional.of(q1));

            com.codeassess.testcase.entity.TestCase tc1 = com.codeassess.testcase.entity.TestCase.builder()
                    .id("tc-1").name("Sample TC").inputData("2 1 + 3 *").expectedOutput("9").points(BigDecimal.valueOf(10.00)).isHidden(false).build();
            when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-1")).thenReturn(List.of(tc1));

            ExamResponse response = teacherExamService.publishExam("exam-1", "teacher-1");

            assertThat(response).isNotNull();
            assertThat(exam.getStatus()).isEqualTo("upcoming");
            assertThat(q1.getDescription()).isEqualTo(richMarkdown); // Markdown verified strictly unchanged
            verify(examRepository).save(exam);
        }

        @Test
        @DisplayName("FE-VAL-01C: publishExam with question maxScore scale > 2 throws IllegalStateException")
        void testPublishExam_QuestionMaxScoreExcessDecimals_ThrowsIllegalStateException() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));

            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Excess Decimals").description("Description").maxScore(new BigDecimal("10.000")).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("vượt quá 2 chữ số thập phân");

            verify(examRepository, never()).save(any());
        }
        @Test
        @DisplayName("TASK-FE-VALIDATION-01D: updateExam allows saving draft with incomplete question/testcase content")
        void testSaveDraft_incompleteQuestionAllowed_preservesDraftStatus() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            exam.setStatus("draft");
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            // Draft update does not query or validate question content
            exam.setStatus("draft");

            UpdateExamDto dto = UpdateExamDto.builder()
                    .title("Draft Exam With Incomplete Questions")
                    .status("draft")
                    .build();

            ExamResponse response = teacherExamService.updateExam("exam-1", dto, "teacher-1");

            assertThat(response).isNotNull();
            assertThat(exam.getStatus()).isEqualTo("draft");
            assertThat(exam.getTitle()).isEqualTo("Draft Exam With Incomplete Questions");
            verify(examRepository).save(exam);
        }

        @Test
        @DisplayName("TASK-FE-VALIDATION-01D: publishExam leaves exam status unchanged when validation fails")
        void testPublishExam_failedValidationLeavesExamStatusUnchanged() {
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "examQuestionRepository", examQuestionRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(teacherExamService, "testCaseRepository", testCaseRepository);

            exam.setStatus("draft");
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(examRepository.findByIdForUpdate("exam-1")).thenReturn(Optional.of(exam));

            // Question sum is 7.00 != 10.00
            com.codeassess.exam.entity.ExamQuestion q1 = com.codeassess.exam.entity.ExamQuestion.builder()
                    .id("q-1").title("Q1").description("Problem 1").maxScore(BigDecimal.valueOf(7.00)).build();
            when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1")).thenReturn(List.of(q1));

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tổng điểm các câu hỏi phải bằng chính xác 10.00 điểm");

            assertThat(exam.getStatus()).isEqualTo("draft"); // Status must remain draft!
            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("TASK-FE-VALIDATION-01D: publishExam throws ResourceNotFoundException for unauthorized teacher")
        void testPublishExam_unauthorizedTeacher_throwsResourceNotFoundException() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-other")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.publishExam("exam-1", "teacher-other"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Exam not found or access denied");

            verify(examRepository, never()).save(any());
        }
    }


    // =========================================================================
    // 6. deleteTeacherExam (Case A, B, C)
    // =========================================================================
    @Nested
    @DisplayName("deleteTeacherExam Tests")
    class DeleteTeacherExamTests {

        @Test
        @DisplayName("deleteTeacherExam throws ResourceNotFoundException when exam not found or access denied")
        void testDeleteTeacherExam_notOwned_throwsResourceNotFoundException() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-other")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teacherExamService.deleteTeacherExam("exam-1", "teacher-other"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Exam not found or access denied");

            verifyNoInteractions(submissionRepository);
            verify(examRepository, never()).delete(any());
            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("Case C: deleteTeacherExam throws IllegalStateException when active grading jobs exist")
        void testDeleteTeacherExam_CaseC_activeGradingJobs_throwsIllegalStateException() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-1")).thenReturn(2L);

            assertThatThrownBy(() -> teacherExamService.deleteTeacherExam("exam-1", "teacher-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot delete exam while submissions are actively being graded. Please wait for grading to complete.");

            verify(submissionRepository, never()).countByExam_Id(any());
            verify(examRepository, never()).delete(any());
            verify(examRepository, never()).save(any());
        }

        @Test
        @DisplayName("Case B: deleteTeacherExam soft deletes exam when historical submissions exist")
        void testDeleteTeacherExam_CaseB_historicalSubmissions_softDelete() {
            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-1")).thenReturn(0L);
            when(submissionRepository.countByExam_Id("exam-1")).thenReturn(5L);

            teacherExamService.deleteTeacherExam("exam-1", "teacher-1");

            assertThat(exam.getStatus()).isEqualTo("archived");
            assertThat(exam.getDeletedAt()).isNotNull();

            verify(examRepository).save(exam);
            verify(examRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Case A: deleteTeacherExam hard deletes exam and clears classes when no submissions exist")
        void testDeleteTeacherExam_CaseA_noSubmissions_hardDelete() {
            Set<CourseClass> mutableClasses = new HashSet<>(List.of(courseClass));
            exam.setClasses(mutableClasses);

            when(examRepository.findByIdAndCourseClass_Teacher_Id("exam-1", "teacher-1")).thenReturn(Optional.of(exam));
            when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-1")).thenReturn(0L);
            when(submissionRepository.countByExam_Id("exam-1")).thenReturn(0L);

            teacherExamService.deleteTeacherExam("exam-1", "teacher-1");

            assertThat(exam.getClasses()).isEmpty();
            verify(examRepository).delete(exam);
            verify(examRepository, never()).save(any());
        }
    }
}
