package com.codeassess.plagiarism.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.CodeComparisonDataDto;
import com.codeassess.plagiarism.dto.PlagiarismRecordDto;
import com.codeassess.plagiarism.dto.SimilarityPairDto;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.mapper.PlagiarismMapper;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeassess.plagiarism.dto.AdjudicationRequestDto;
import com.codeassess.plagiarism.dto.AdjudicationResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/teacher/plagiarism")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
@Transactional(readOnly = true)
public class TeacherPlagiarismController {

    private final SimilarityPairRepository similarityPairRepository;
    private final PlagiarismEvidenceRepository evidenceRepository;
    private final PlagiarismMapper mapper;
    private final UserRepository userRepository;

    private User getAuthenticatedTeacher() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
    }

    private void verifyOwnership(SimilarityPair pair, User teacher) {
        if (!pair.getExam().getCourseClass().getTeacher().getId().equals(teacher.getId())) {
            // "Use existing 404 masking convention for inaccessible resources."
            throw new ResourceNotFoundException("Plagiarism record not found");
        }
    }

    @GetMapping
    public ResponseEntity<List<SimilarityPairDto>> listAllPlagiarismPairs() {
        User teacher = getAuthenticatedTeacher();
        
        List<SimilarityPair> pairs = similarityPairRepository.findActiveByTeacherId(teacher.getId());

        return ResponseEntity.ok(pairs.stream().map(mapper::toPairDto).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlagiarismRecordDto> getPlagiarismDetail(@PathVariable Long id) {
        SimilarityPair pair = similarityPairRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plagiarism record not found"));
        
        verifyOwnership(pair, getAuthenticatedTeacher());
        
        List<PlagiarismEvidence> evidences = evidenceRepository.findBySimilarityPair_Id(id);
        return ResponseEntity.ok(mapper.toRecordDto(pair, evidences));
    }

    @GetMapping("/{id}/compare")
    public ResponseEntity<CodeComparisonDataDto> getComparison(@PathVariable Long id) {
        SimilarityPair pair = similarityPairRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plagiarism record not found"));
        
        verifyOwnership(pair, getAuthenticatedTeacher());
        
        List<PlagiarismEvidence> evidences = evidenceRepository.findBySimilarityPair_Id(id);
        return ResponseEntity.ok(mapper.toComparisonDto(pair, evidences));
    }

    @PostMapping("/{id}/adjudicate")
    @Transactional
    public ResponseEntity<AdjudicationResponseDto> adjudicatePair(
            @PathVariable Long id,
            @Valid @RequestBody AdjudicationRequestDto request) {
        User teacher = getAuthenticatedTeacher();
        SimilarityPair pair = similarityPairRepository.findByIdAndTeacherIdForUpdate(id, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Plagiarism record not found"));

        String status = request.getStatus() != null ? request.getStatus().trim().toUpperCase() : "PENDING";
        if (!isValidAdjudicationStatus(status)) {
            throw new IllegalArgumentException("Invalid adjudication status: " + status);
        }

        pair.setAdjudicationStatus(status);
        pair.setAdjudicationNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        pair.setAdjudicatedBy(teacher);
        pair.setAdjudicatedAt(LocalDateTime.now());
        similarityPairRepository.save(pair);

        return ResponseEntity.ok(AdjudicationResponseDto.builder()
                .pairId(pair.getId().toString())
                .status(pair.getAdjudicationStatus())
                .notes(pair.getAdjudicationNotes())
                .adjudicatedBy(teacher.getFullName() != null ? teacher.getFullName() : teacher.getEmail())
                .adjudicatedAt(pair.getAdjudicatedAt())
                .build());
    }

    private boolean isValidAdjudicationStatus(String status) {
        return "PENDING".equals(status) ||
               "FLAG_CONFIRMED".equals(status) ||
               "FALSE_POSITIVE".equals(status) ||
               "HEARING_SCHEDULED".equals(status);
    }
}
