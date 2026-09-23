package com.codeassess.plagiarism.similarity;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

@Component
public class JaccardSimilarityCalculator {

    private static final BigDecimal FLAG_THRESHOLD = new BigDecimal("70.00");

    public SimilarityResult calculate(Set<Long> uniqueHashesA, Set<Long> uniqueHashesB) {
        if (uniqueHashesA == null || uniqueHashesB == null) {
            return new SimilarityResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, false);
        }
        
        if (uniqueHashesA.isEmpty() && uniqueHashesB.isEmpty()) {
            return new SimilarityResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, false);
        }

        if (uniqueHashesA.isEmpty() || uniqueHashesB.isEmpty()) {
            return new SimilarityResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, false);
        }

        Set<Long> intersection = new HashSet<>(uniqueHashesA);
        intersection.retainAll(uniqueHashesB);

        Set<Long> union = new HashSet<>(uniqueHashesA);
        union.addAll(uniqueHashesB);

        int sharedCount = intersection.size();
        
        if (union.isEmpty()) {
            return new SimilarityResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, false);
        }

        double rate = (double) sharedCount / union.size() * 100.0;
        
        BigDecimal similarityRate = BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP);
        boolean isFlagged = similarityRate.compareTo(FLAG_THRESHOLD) >= 0;

        return new SimilarityResult(similarityRate, sharedCount, isFlagged);
    }
}
