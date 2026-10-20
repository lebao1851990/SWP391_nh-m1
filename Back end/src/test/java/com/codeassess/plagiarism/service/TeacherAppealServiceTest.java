package com.codeassess.plagiarism.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TeacherAppealServiceTest {

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private GradeAuditLogRepository gradeAuditLogRepository;

    @InjectMocks
    private TeacherAppealService teacherAppealService;

    private User teacher;
    private User student;
    private Exam exam;
    private Submission submission;
    private Appeal appeal;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        teacher = User.builder().id("teacher-1").email("teacher@test.com").fullName("Teacher One").build();
        student = User.builder().id("student-1").email("student@test.com").fullName("Student One").build();

        CourseClass courseClass = CourseClass.builder().id("class-1").teacher(teacher).build();
        exam = Exam.builder().id("exam-1").title("Final Exam").courseClass(courseClass).build();

        submission = Submission.builder()
                .id("sub-1")
                .exam(exam)
                .student(student)
                .status("GRADED")
                .totalScore(BigDecimal.valueOf(60.0))
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
    public void getTeacherAppeals_ReturnsAssignedAppeals() {
        when(appealRepository.findByTeacherId("teacher-1")).thenReturn(Collections.singletonList(appeal));

        List<AppealResponseDto> result = teacherAppealService.getTeacherAppeals(teacher);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
        verify(appealRepository, times(1)).findByTeacherId("teacher-1");
    }

    @Test
    public void getTeacherAppeal_Success() {
        when(appealRepository.findByIdAndTeacherId(12345L, "teacher-1")).thenReturn(Optional.of(appeal));
        String opaqueId = OpaqueIdEncoder.encode(12345L);

        AppealResponseDto result = teacherAppealService.getTeacherAppeal(opaqueId, teacher);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(appealRepository, times(1)).findByIdAndTeacherId(12345L, "teacher-1");
    }

    @Test
    public void getTeacherAppeal_NotFound_ThrowsException() {
        when(appealRepository.findByIdAndTeacherId(999L, "teacher-1")).thenReturn(Optional.empty());
        String opaqueId = OpaqueIdEncoder.encode(999L);

        assertThrows(ResourceNotFoundException.class, () -> teacherAppealService.getTeacherAppeal(opaqueId, teacher));
    }

    @Test
    public void resolveAppeal_ThrowsAccessDeniedException_TeacherCannotResolve() {
        String opaqueId = OpaqueIdEncoder.encode(12345L);
        AppealResolveRequestDto dto = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Teacher tries to approve")
                .build();

        assertThrows(AccessDeniedException.class, () -> teacherAppealService.resolveAppeal(opaqueId, dto, teacher));
    }
}
