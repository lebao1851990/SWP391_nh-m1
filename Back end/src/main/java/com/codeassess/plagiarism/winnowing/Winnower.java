package com.codeassess.plagiarism.winnowing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Winnower {

    private final int windowSize;

    public Winnower(@Value("${plagiarism.window-size:4}") int windowSize) {
        this.windowSize = windowSize;
    }

    public List<HashedKGram> winnow(List<HashedKGram> sequence) {
        List<HashedKGram> fingerprints = new ArrayList<>();

        if (sequence == null || sequence.isEmpty()) {
            return fingerprints;
        }

        if (sequence.size() <= windowSize) {
            // Sequence smaller or equal to W: just select the minimum hash overall
            HashedKGram min = sequence.get(0);
            for (int i = 1; i < sequence.size(); i++) {
                if (sequence.get(i).getHash() <= min.getHash()) { // tie-break rightmost
                    min = sequence.get(i);
                }
            }
            fingerprints.add(min);
            return fingerprints;
        }

        int lastSelectedPosition = -1;

        for (int i = 0; i <= sequence.size() - windowSize; i++) {
            int minIndex = i;
            for (int j = 1; j < windowSize; j++) {
                int currentIndex = i + j;
                // <= guarantees we pick the rightmost minimum
                if (sequence.get(currentIndex).getHash() <= sequence.get(minIndex).getHash()) {
                    minIndex = currentIndex;
                }
            }

            if (minIndex != lastSelectedPosition) {
                fingerprints.add(sequence.get(minIndex));
                lastSelectedPosition = minIndex;
            }
        }

        return fingerprints;
    }
}
