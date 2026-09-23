package com.codeassess.plagiarism.service;

public class OpaqueIdEncoder {
    
    // Feature-local mask to prevent trivial exposure of database sequence
    private static final long XOR_MASK = 0x5A93D28F1B3E4C76L;
    
    public static String encode(Long id) {
        if (id == null) return null;
        long masked = id ^ XOR_MASK;
        return "apl_" + Long.toHexString(masked);
    }
    
    public static Long decode(String opaqueId) {
        if (opaqueId == null || !opaqueId.startsWith("apl_")) {
            return null;
        }
        try {
            long masked = Long.parseUnsignedLong(opaqueId.substring(4), 16);
            return masked ^ XOR_MASK;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
