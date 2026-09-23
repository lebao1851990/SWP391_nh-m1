package com.codeassess.plagiarism.similarity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SimilarityResult {
    private final BigDecimal similarityRate;
    private final int sharedHashesCount;
    private final boolean flagged;
}
