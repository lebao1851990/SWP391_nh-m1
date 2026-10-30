package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class StudentAppealServiceTest {

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private StudentAppealService studentAppealService;

    private User student;
    private Submission submission;
    private AppealCreateRequestDto requestDto;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        student = User.builder().id("student1").build();
        
        Exam exam = Exam.builder().title("Midterm").build();
        submission = Submission.builder().id("sub1").student(student).exam(exam).status("GRADED").build();
        
        requestDto = new AppealCreateRequestDto();
        requestDto.setSubmissionId("sub1");
        requestDto.setBasis("Valid basis");
        requestDto.setExplanation("My code is original.");
    }

    @Test
    public void createAppeal_Success() {
        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub1", "student1"))
                .thenReturn(Optional.of(submission));
        
        when(appealRepository.existsBySubmission_IdAndStatus("sub1", "PENDING"))
                .thenReturn(false);
                
        Appeal savedAppeal = Appeal.builder()
                .id(99L)
                .submission(submission)
                .student(student)
                .reason("My code is original.")
                .basis("Valid basis")
                .status("PENDING")
                .build();
                
        when(appealRepository.save(any(Appeal.class))).thenReturn(savedAppeal);
        
        AppealResponseDto response = studentAppealService.createAppeal(requestDto, student);
        
        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals("Valid basis", response.getBasis());
        assertEquals("My code is original.", response.getExplanation());
        assertEquals("Midterm", response.getExamName());
        assertFalse(response.getId().contains("99")); // Opaque ID doesn't trivially leak numeric ID
        assertTrue(response.getId().startsWith("apl_"));
        
        verify(submissionRepository, times(1)).findByIdAndStudent_IdForUpdate("sub1", "student1");
        verify(appealRepository, times(1)).existsBySubmission_IdAndStatus("sub1", "PENDING");
        verify(appealRepository, times(1)).save(any(Appeal.class));
    }

    @Test
    public void createAppeal_SubmissionNotFoundOrNotOwned() {
        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub1", "student1"))
                .thenReturn(Optional.empty());
                
        assertThrows(ResourceNotFoundException.class, () -> studentAppealService.createAppeal(requestDto, student));
        
        verify(appealRepository, never()).save(any(Appeal.class));
    }

    @Test
    public void createAppeal_DuplicatePendingConflict() {
        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub1", "student1"))
                .thenReturn(Optional.of(submission));
                
        when(appealRepository.existsBySubmission_IdAndStatus("sub1", "PENDING"))
                .thenReturn(true);
                
        assertThrows(ConflictException.class, () -> studentAppealService.createAppeal(requestDto, student));
        
        verify(appealRepository, never()).save(any(Appeal.class));
    }

    @Test
    public void createAppeal_ExplanationTooShort_ThrowsIllegalArgumentException() {
        requestDto.setExplanation("Short text"); // 10 chars < 20

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                studentAppealService.createAppeal(requestDto, student));

        assertTrue(ex.getMessage().contains("20"));
        verify(appealRepository, never()).save(any(Appeal.class));
    }

    @Test
    public void createAppeal_ExplanationTooLong_ThrowsIllegalArgumentException() {
        String longText = "a".repeat(1001); // 1001 chars > 1000
        requestDto.setExplanation(longText);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                studentAppealService.createAppeal(requestDto, student));

        assertTrue(ex.getMessage().contains("1000"));
        verify(appealRepository, never()).save(any(Appeal.class));
    }

    @Test
    public void createAppeal_SubmissionNotGraded_ThrowsIllegalArgumentException() {
        submission.setStatus("PENDING");
        when(submissionRepository.findByIdAndStudent_IdForUpdate("sub1", "student1"))
                .thenReturn(Optional.of(submission));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                studentAppealService.createAppeal(requestDto, student));

        assertTrue(ex.getMessage().contains("đã được chấm điểm"));
        verify(appealRepository, never()).save(any(Appeal.class));
    }
}
