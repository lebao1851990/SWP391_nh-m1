package com.codeassess.exam.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminExamServiceTest {

    @Mock
    private ExamRepository examRepository;

    @Spy
    private ExamMapper examMapper = new ExamMapper();

    @Mock
    private CourseClassRepository courseClassRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private AdminExamService adminExamService;

    private CourseClass courseClass;
    private Exam exam;

    @BeforeEach
    void setUp() {
        courseClass = CourseClass.builder().id("class-1").code("PRO192").name("Java OOP").build();
        exam = Exam.builder()
                .id("exam-1")
                .title("Midterm Exam")
                .courseClass(courseClass)
                .classes(new HashSet<>(List.of(courseClass)))
                .examDate(LocalDateTime.now().plusDays(1))
                .duration(60)
                .status("upcoming")
                .build();
    }

    @Test
    @DisplayName("getAllExams returns all active exams")
    void testGetAllExams() {
        when(examRepository.findAllActive()).thenReturn(List.of(exam));

        List<ExamResponse> result = adminExamService.getAllExams();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("exam-1");
    }

    @Test
    @DisplayName("getExamById returns exam if not deleted")
    void testGetExamById_Success() {
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));

        ExamResponse result = adminExamService.getExamById("exam-1");

        assertThat(result.getId()).isEqualTo("exam-1");
        assertThat(result.getTitle()).isEqualTo("Midterm Exam");
    }

    @Test
    @DisplayName("deleteExam archives exam if submissions already exist")
    void testDeleteExam_ArchivesWhenSubmissionsExist() {
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-1")).thenReturn(0L);
        when(submissionRepository.countByExam_Id("exam-1")).thenReturn(5L);

        adminExamService.deleteExam("exam-1");

        assertThat(exam.getDeletedAt()).isNotNull();
        assertThat(exam.getStatus()).isEqualTo("archived");
        verify(examRepository).save(exam);
        verify(examRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteExam throws exception if active grading jobs exist")
    void testDeleteExam_BlocksWhenActiveGrading() {
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));
        when(submissionRepository.countActiveGradingSubmissionsByExamId("exam-1")).thenReturn(2L);

        assertThatThrownBy(() -> adminExamService.deleteExam("exam-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actively being graded");
    }

    @Test
    @DisplayName("createExam successfully creates exam for valid classes")
    void testCreateExam_Success() {
        CreateExamDto dto = new CreateExamDto();
        dto.setTitle("OOP Exam");
        dto.setClassIds(List.of("class-1"));
        dto.setDate(LocalDateTime.now().plusDays(2));
        dto.setDurationMinutes(90);

        when(courseClassRepository.findAllById(List.of("class-1"))).thenReturn(List.of(courseClass));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> {
            Exam e = inv.getArgument(0);
            e.setId("exam-new");
            return e;
        });

        ExamResponse response = adminExamService.createExam(dto);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("OOP Exam");
    }

    @Test
    @DisplayName("createExam throws IllegalArgumentException when classIds is empty")
    void testCreateExam_EmptyClassIds() {
        CreateExamDto dto = new CreateExamDto();
        dto.setTitle("Invalid Exam");
        dto.setClassIds(Collections.emptyList());

        assertThatThrownBy(() -> adminExamService.createExam(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one class");
    }

    @Test
    @DisplayName("updateExam updates fields and assigned classes")
    void testUpdateExam_Success() {
        UpdateExamDto dto = new UpdateExamDto();
        dto.setTitle("Updated OOP Exam");
        dto.setStatus("completed");

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));

        ExamResponse response = adminExamService.updateExam("exam-1", dto);

        assertThat(exam.getTitle()).isEqualTo("Updated OOP Exam");
        assertThat(exam.getStatus()).isEqualTo("completed");
    }
}

