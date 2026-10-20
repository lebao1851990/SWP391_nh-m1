package com.codeassess.plagiarism.service;

import com.codeassess.plagiarism.repository.ExamMatrixCoordinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamMatrixSweeper {

    private final ExamMatrixCoordinationRepository coordinationRepository;
    private final SimilarityMatrixService similarityMatrixService;

    /**
     * Periodically sweeps for abandoned leases or pending generations.
     * Runs every 30 seconds by default.
     */
    @Scheduled(fixedDelayString = "${matrix.sweeper.fixed-delay-ms:30000}", initialDelay = 10000)
    public int sweepAndRecover() {
        List<String> examsToRecover = coordinationRepository.findExamsRequiringRefreshRecovery();
        if (examsToRecover.isEmpty()) {
            return 0;
        }

        log.info("ExamMatrixSweeper detected {} exams requiring refresh recovery", examsToRecover.size());
        int recovered = 0;
        for (String examId : examsToRecover) {
            try {
                similarityMatrixService.generateMatrixForExam(examId, "sweeper-worker");
                recovered++;
            } catch (Exception e) {
                log.error("Failed to recover matrix for exam {}: {}", examId, e.getMessage(), e);
            }
        }
        return recovered;
    }
}
