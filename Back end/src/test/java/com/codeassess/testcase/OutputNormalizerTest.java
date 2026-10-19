package com.codeassess.testcase;

import com.codeassess.testcase.util.OutputNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutputNormalizer Unit Tests")
class OutputNormalizerTest {

    @Test
    @DisplayName("Matches identical single-line output")
    void testIdenticalSingleLine() {
        assertTrue(OutputNormalizer.matches("Hello World", "Hello World"));
    }

    @Test
    @DisplayName("Normalizes CRLF and LF differences")
    void testWindowsVsLinuxNewlines() {
        String crlf = "Line 1\r\nLine 2\r\nLine 3\r\n";
        String lf = "Line 1\nLine 2\nLine 3\n";
        assertTrue(OutputNormalizer.matches(crlf, lf));
    }

    @Test
    @DisplayName("Trims trailing whitespace per line and trailing newlines")
    void testTrailingWhitespaceAndNewlines() {
        String actual = "Output line 1   \nOutput line 2 \n\n\n";
        String expected = "Output line 1\nOutput line 2";
        assertTrue(OutputNormalizer.matches(actual, expected));
    }

    @Test
    @DisplayName("Fails on internal whitespace difference")
    void testInternalWhitespaceStrictness() {
        assertFalse(OutputNormalizer.matches("1 2 3", "1  2 3"));
        assertFalse(OutputNormalizer.matches("hello world", "helloworld"));
    }

    @Test
    @DisplayName("Fails on wrong value or ordering")
    void testWrongValue() {
        assertFalse(OutputNormalizer.matches("42", "43"));
        assertFalse(OutputNormalizer.matches("Apple\nBanana", "Banana\nApple"));
    }

    @Test
    @DisplayName("Handles empty outputs correctly")
    void testEmptyOutputs() {
        assertTrue(OutputNormalizer.matches("", ""));
        assertTrue(OutputNormalizer.matches("   \n\n", ""));
        assertFalse(OutputNormalizer.matches("Not Empty", ""));
        assertFalse(OutputNormalizer.matches("", "Expected Something"));
    }

    @Test
    @DisplayName("Supports Unicode and Vietnamese characters")
    void testUnicodeAndVietnamese() {
        String actual = "Xin chào Việt Nam! Điểm số: 10\r\n";
        String expected = "Xin chào Việt Nam! Điểm số: 10\n";
        assertTrue(OutputNormalizer.matches(actual, expected));

        assertFalse(OutputNormalizer.matches("Xin chao Viet Nam!", "Xin chào Việt Nam!"));
    }

    @Test
    @DisplayName("Null handling")
    void testNullHandling() {
        assertTrue(OutputNormalizer.matches(null, null));
        assertTrue(OutputNormalizer.matches(null, ""));
        assertTrue(OutputNormalizer.matches("", null));
        assertFalse(OutputNormalizer.matches("abc", null));
        assertFalse(OutputNormalizer.matches(null, "abc"));
    }
}
