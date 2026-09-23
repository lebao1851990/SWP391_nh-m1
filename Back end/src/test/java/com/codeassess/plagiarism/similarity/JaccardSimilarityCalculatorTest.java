package com.codeassess.plagiarism.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JaccardSimilarityCalculatorTest {

    private JaccardSimilarityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new JaccardSimilarityCalculator();
    }

    @Test
    void testPartialOverlap() {
        Set<Long> a = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        Set<Long> b = new HashSet<>(Arrays.asList(2L, 3L, 4L));

        SimilarityResult result = calculator.calculate(a, b);

        assertEquals(new BigDecimal("50.00"), result.getSimilarityRate());
        assertEquals(2, result.getSharedHashesCount());
        assertFalse(result.isFlagged());
    }

    @Test
    void testIdenticalSets() {
        Set<Long> a = new HashSet<>(Arrays.asList(1L, 2L));
        Set<Long> b = new HashSet<>(Arrays.asList(1L, 2L));

        SimilarityResult result = calculator.calculate(a, b);

        assertEquals(new BigDecimal("100.00"), result.getSimilarityRate());
        assertEquals(2, result.getSharedHashesCount());
        assertTrue(result.isFlagged());
    }

    @Test
    void testDisjointSets() {
        Set<Long> a = new HashSet<>(Arrays.asList(1L, 2L));
        Set<Long> b = new HashSet<>(Arrays.asList(3L, 4L));

        SimilarityResult result = calculator.calculate(a, b);

        assertEquals(new BigDecimal("0.00"), result.getSimilarityRate());
        assertEquals(0, result.getSharedHashesCount());
        assertFalse(result.isFlagged());
    }

    @Test
    void testEmptySets() {
        Set<Long> empty = Collections.emptySet();
        Set<Long> b = new HashSet<>(Arrays.asList(1L));

        SimilarityResult result1 = calculator.calculate(empty, empty);
        assertEquals(new BigDecimal("0.00"), result1.getSimilarityRate());
        assertEquals(0, result1.getSharedHashesCount());
        assertFalse(result1.isFlagged());

        SimilarityResult result2 = calculator.calculate(empty, b);
        assertEquals(new BigDecimal("0.00"), result2.getSimilarityRate());
        assertEquals(0, result2.getSharedHashesCount());
        assertFalse(result2.isFlagged());
    }

    @Test
    void testRoundingAndFlaggingThresholds() {
        // 7 hashes in union, 5 in intersection -> 5/7 = 71.428... -> 71.43
        Set<Long> a = new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L));
        Set<Long> b = new HashSet<>(Arrays.asList(2L, 3L, 4L, 5L, 6L, 7L));

        SimilarityResult result1 = calculator.calculate(a, b);
        assertEquals(new BigDecimal("71.43"), result1.getSimilarityRate());
        assertTrue(result1.isFlagged());

        // 69.99 is false, 70.00 is true
        // 100 in union, 70 in intersection -> 70.00
        Set<Long> a70 = new HashSet<>();
        Set<Long> b70 = new HashSet<>();
        for (long i=1; i<=100; i++) a70.add(i);
        for (long i=31; i<=130; i++) b70.add(i); // intersection 31..100 (70 elements), union 1..130 (130 elements) -> wait, 70/130 != 70%

        Set<Long> aExact70 = new HashSet<>();
        Set<Long> bExact70 = new HashSet<>();
        for (long i=1; i<=70; i++) { aExact70.add(i); bExact70.add(i); } // 70 common
        for (long i=71; i<=100; i++) { aExact70.add(i); } // 30 more in A. Total union = 100.
        
        SimilarityResult result2 = calculator.calculate(aExact70, bExact70);
        assertEquals(new BigDecimal("70.00"), result2.getSimilarityRate());
        assertTrue(result2.isFlagged());
    }

    @Test
    void testDuplicateHashesInInputHandled() {
        // Since input is Set, duplicates are automatically merged.
        Set<Long> a = new HashSet<>(Arrays.asList(1L, 1L, 2L));
        Set<Long> b = new HashSet<>(Arrays.asList(2L, 2L, 3L));

        SimilarityResult result = calculator.calculate(a, b);
        assertEquals(new BigDecimal("33.33"), result.getSimilarityRate()); // unique sets are {1,2} and {2,3}. Union {1,2,3}. Intersection {2}. 1/3 = 33.33
        assertEquals(1, result.getSharedHashesCount());
    }
}
