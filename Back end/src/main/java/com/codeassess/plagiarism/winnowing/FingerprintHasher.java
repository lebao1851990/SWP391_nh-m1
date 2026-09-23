package com.codeassess.plagiarism.winnowing;

import org.springframework.stereotype.Component;

@Component
public class FingerprintHasher {

    private static final long FNV_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;

    public long hash(String text) {
        if (text == null) {
            return 0L;
        }
        long hash = FNV_64_INIT;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= FNV_64_PRIME;
        }
        return hash;
    }
}
