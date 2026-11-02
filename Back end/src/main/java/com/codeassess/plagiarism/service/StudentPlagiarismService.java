package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.StudentPlagiarismDetailDto;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.mapper.PlagiarismMapper;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentPlagiarismService {

    private final SimilarityPairRepository similarityPairRepository;
    private final PlagiarismEvidenceRepository evidenceRepository;
    private final PlagiarismMapper mapper;

    @Transactional(readOnly = true)
    public StudentPlagiarismDetailDto getPlagiarismDetail(Long pairId, User student) {
        SimilarityPair pair = similarityPairRepository.findByIdAndStudentId(pairId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Plagiarism record not found"));

        List<PlagiarismEvidence> evidences = evidenceRepository.findBySimilarityPair_Id(pairId);

        return mapper.toStudentDetailDto(pair, evidences, student.getId());
    }
}
