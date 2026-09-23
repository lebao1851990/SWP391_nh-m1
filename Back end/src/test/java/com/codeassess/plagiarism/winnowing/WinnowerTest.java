package com.codeassess.plagiarism.winnowing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WinnowerTest {

    private Winnower winnower;

    @BeforeEach
    void setUp() {
        winnower = new Winnower(4);
    }

    private HashedKGram k(long hash, int pos) {
        return new HashedKGram(hash, pos, "Node", 1, 5);
    }

    @Test
    void testSequenceSmallerThanW() {
        List<HashedKGram> seq = Arrays.asList(
                k(77, 0), k(74, 1), k(85, 2)
        );
        List<HashedKGram> result = winnower.winnow(seq);
        assertEquals(1, result.size());
        assertEquals(74, result.get(0).getHash());
    }

    @Test
    void testSequenceExactlyW() {
        List<HashedKGram> seq = Arrays.asList(
                k(77, 0), k(74, 1), k(85, 2), k(40, 3)
        );
        List<HashedKGram> result = winnower.winnow(seq);
        assertEquals(1, result.size());
        assertEquals(40, result.get(0).getHash());
    }

    @Test
    void testSequenceLargerThanW() {
        // Hashes: 77, 74, 85, 40, 38, 90, 71, 40
        // Window 1: 77, 74, 85, 40 -> min 40 (pos 3)
        // Window 2: 74, 85, 40, 38 -> min 38 (pos 4)
        // Window 3: 85, 40, 38, 90 -> min 38 (pos 4, already selected)
        // Window 4: 40, 38, 90, 71 -> min 38 (pos 4, already selected)
        // Window 5: 38, 90, 71, 40 -> min 38 (pos 4, already selected)
        List<HashedKGram> seq = Arrays.asList(
                k(77, 0), k(74, 1), k(85, 2), k(40, 3), k(38, 4), k(90, 5), k(71, 6), k(40, 7)
        );
        List<HashedKGram> result = winnower.winnow(seq);
        assertEquals(2, result.size());
        assertEquals(40, result.get(0).getHash());
        assertEquals(38, result.get(1).getHash());
    }

    @Test
    void testTieBreakingRightmostMinimum() {
        // Hashes: 77, 40(pos1), 85, 40(pos3), 90
        // Window 1 (0-3): 77, 40, 85, 40 -> min is 40. Should pick rightmost, i.e., pos 3.
        // Window 2 (1-4): 40, 85, 40, 90 -> min is 40. Should pick rightmost, i.e., pos 3. (already selected)
        List<HashedKGram> seq = Arrays.asList(
                k(77, 0), k(40, 1), k(85, 2), k(40, 3), k(90, 4)
        );
        List<HashedKGram> result = winnower.winnow(seq);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getPosition()); // position 3 selected
    }
}
