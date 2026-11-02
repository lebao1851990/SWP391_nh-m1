package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.dto.StudentPlagiarismDetailDto;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.mapper.PlagiarismMapper;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StudentPlagiarismServiceTest {

    @Mock
    private SimilarityPairRepository similarityPairRepository;

    @Mock
    private PlagiarismEvidenceRepository evidenceRepository;

    @Spy
    private PlagiarismMapper mapper = new PlagiarismMapper();

    @InjectMocks
    private StudentPlagiarismService studentPlagiarismService;

    private User studentA;
    private User studentB;
    private User studentUnauthorized;
    private Submission submissionA;
    private Submission submissionB;
    private SimilarityPair pair;
    private List<PlagiarismEvidence> evidences;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        studentA = User.builder().id("std-uuid-111").email("studentA@test.edu").fullName("Student Alpha").build();
        studentB = User.builder().id("std-uuid-222").email("studentB@test.edu").fullName("Student Beta").build();
        studentUnauthorized = User.builder().id("std-uuid-999").email("unauth@test.edu").fullName("Student Unauth").build();

        Exam exam = Exam.builder().id("exam-01").title("PRO192 Practical Exam").build();

        submissionA = Submission.builder()
                .id("sub-uuid-aaa-1111")
                .student(studentA)
                .exam(exam)
                .status("GRADED")
                .build();

        submissionB = Submission.builder()
                .id("sub-uuid-bbb-2222")
                .student(studentB)
                .exam(exam)
                .status("GRADED")
                .build();

        pair = SimilarityPair.builder()
                .id(1L)
                .exam(exam)
                .submissionA(submissionA)
                .submissionB(submissionB)
                .similarityRate(BigDecimal.valueOf(89.29))
                .isFlagged(true)
                .build();

        evidences = Collections.emptyList();
    }

    @Test
    public void getPlagiarismDetail_SelectsCorrectSubmissionForStudentA() {
        when(similarityPairRepository.findByIdAndStudentId(1L, "std-uuid-111"))
                .thenReturn(Optional.of(pair));
        when(evidenceRepository.findBySimilarityPair_Id(1L))
                .thenReturn(evidences);

        StudentPlagiarismDetailDto dto = studentPlagiarismService.getPlagiarismDetail(1L, studentA);

        assertNotNull(dto);
        assertEquals("1", dto.getRecordId(), "recordId must still be pair ID for route consistency");
        assertEquals("sub-uuid-aaa-1111", dto.getSubmissionId(), "submissionId MUST be student A's own submission UUID");
        assertNotEquals("sub-uuid-bbb-2222", dto.getSubmissionId(), "submissionId MUST NOT leak student B's submission UUID");
        assertEquals(BigDecimal.valueOf(89.29), dto.getSimilarityRate());
        assertTrue(dto.isFlagged());
    }

    @Test
    public void getPlagiarismDetail_SelectsCorrectSubmissionForStudentB() {
        when(similarityPairRepository.findByIdAndStudentId(1L, "std-uuid-222"))
                .thenReturn(Optional.of(pair));
        when(evidenceRepository.findBySimilarityPair_Id(1L))
                .thenReturn(evidences);

        StudentPlagiarismDetailDto dto = studentPlagiarismService.getPlagiarismDetail(1L, studentB);

        assertNotNull(dto);
        assertEquals("1", dto.getRecordId());
        assertEquals("sub-uuid-bbb-2222", dto.getSubmissionId(), "submissionId MUST be student B's own submission UUID");
        assertNotEquals("sub-uuid-aaa-1111", dto.getSubmissionId(), "submissionId MUST NOT leak student A's submission UUID");
    }

    @Test
    public void getPlagiarismDetail_UnauthorizedStudent_ThrowsNotFound() {
        when(similarityPairRepository.findByIdAndStudentId(1L, "std-uuid-999"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            studentPlagiarismService.getPlagiarismDetail(1L, studentUnauthorized);
        });

        verify(evidenceRepository, never()).findBySimilarityPair_Id(anyLong());
    }

    @Test
    public void mapper_NullStudentId_ReturnsNullSubmissionIdSafely() {
        StudentPlagiarismDetailDto dto = mapper.toStudentDetailDto(pair, evidences, null);

        assertNotNull(dto);
        assertEquals("1", dto.getRecordId());
        assertNull(dto.getSubmissionId(), "submissionId must safely be null when studentId is null");
    }

    @Test
    public void mapper_LegacyOverload_PreservesCompatibility() {
        StudentPlagiarismDetailDto dto = mapper.toStudentDetailDto(pair, evidences);

        assertNotNull(dto);
        assertEquals("1", dto.getRecordId());
        assertNull(dto.getSubmissionId());
    }
}
