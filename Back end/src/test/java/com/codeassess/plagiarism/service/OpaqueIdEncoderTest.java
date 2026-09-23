package com.codeassess.plagiarism.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OpaqueIdEncoderTest {

    @Test
    public void testEncodeDecode() {
        Long rawId = 12345L;
        String opaque = OpaqueIdEncoder.encode(rawId);
        
        assertNotNull(opaque);
        assertTrue(opaque.startsWith("apl_"));
        assertNotEquals(String.valueOf(rawId), opaque);
        
        Long decoded = OpaqueIdEncoder.decode(opaque);
        assertEquals(rawId, decoded);
    }
    
    @Test
    public void testInvalidDecode() {
        assertNull(OpaqueIdEncoder.decode(null));
        assertNull(OpaqueIdEncoder.decode("invalid_format"));
        assertNull(OpaqueIdEncoder.decode("apl_xyz"));
    }
}
