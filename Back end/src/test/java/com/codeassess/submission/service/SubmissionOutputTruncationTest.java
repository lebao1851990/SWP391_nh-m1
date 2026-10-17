package com.codeassess.submission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionOutputTruncationTest {

    private static final int LIMIT = SubmissionPersistenceService.MAX_OUTPUT_BYTES; // 65,535

    @Test
    @DisplayName("Null and empty values retain intended behavior")
    void testNullAndEmptyValues() {
        assertThat(SubmissionPersistenceService.safeTruncateUtf8(null, LIMIT)).isNull();
        assertThat(SubmissionPersistenceService.safeTruncateUtf8("", LIMIT)).isEmpty();
        assertThat(SubmissionPersistenceService.safeTruncateUtf8("abc", 0)).isEmpty();
        assertThat(SubmissionPersistenceService.safeTruncateUtf8("abc", -1)).isEmpty();
    }

    @Test
    @DisplayName("ASCII output below the byte limit is returned unchanged")
    void testAsciiBelowLimit() {
        String input = "Hello, world! Compilation successful.\nExit code: 0\n";
        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).isSameAs(input);
        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(LIMIT);
    }

    @Test
    @DisplayName("ASCII output above the byte limit is truncated with marker and within 65,535 bytes")
    void testAsciiAboveLimit() {
        String input = "A".repeat(70_000);
        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).isNotNull();
        assertThat(result).endsWith(SubmissionPersistenceService.TRUNCATION_MARKER);
        byte[] utf8Bytes = result.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8Bytes.length).isLessThanOrEqualTo(LIMIT);
        assertThat(utf8Bytes.length).isEqualTo(LIMIT); // Exact fill for 1-byte chars + 17-byte marker
    }

    @Test
    @DisplayName("Vietnamese output: character count is below limit (45,000 chars) but UTF-8 size (66,600+ bytes) exceeds limit")
    void testVietnameseCharCountBelowLimitButBytesAboveLimit() {
        // Vietnamese characters like 'ế', 'ệ', 'đ', 'ồ' are 3 bytes each in UTF-8
        String line = "Kiểm tra tiếng Việt \u1EBF\u1EC7\u0111\u1ED3\n";
        int reps = 1800;
        String input = line.repeat(reps);

        int charCount = input.length();
        int byteCount = input.getBytes(StandardCharsets.UTF_8).length;

        // Verify precondition: char count well below 65,535, but byte count well above 65,535
        assertThat(charCount).isLessThan(LIMIT);
        assertThat(byteCount).isGreaterThan(LIMIT);

        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).isNotNull();
        assertThat(result).endsWith(SubmissionPersistenceService.TRUNCATION_MARKER);
        byte[] utf8Bytes = result.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8Bytes.length).isLessThanOrEqualTo(LIMIT);

        // Verify valid Unicode re-encoding
        String reEncoded = new String(utf8Bytes, StandardCharsets.UTF_8);
        assertThat(reEncoded).isEqualTo(result);
        assertThat(reEncoded).doesNotContain("\uFFFD"); // No replacement characters
    }

    @Test
    @DisplayName("CJK output: character count is below limit (30,000 chars) but UTF-8 size (90,000+ bytes) exceeds limit")
    void testCjkCharCountBelowLimitButBytesAboveLimit() {
        // CJK ideographs are 3 bytes each in UTF-8
        String block = "测试中文字符编译输出。\n";
        int reps = 2500;
        String input = block.repeat(reps);

        int charCount = input.length();
        int byteCount = input.getBytes(StandardCharsets.UTF_8).length;

        assertThat(charCount).isLessThan(LIMIT);
        assertThat(byteCount).isGreaterThan(LIMIT);

        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).isNotNull();
        assertThat(result).endsWith(SubmissionPersistenceService.TRUNCATION_MARKER);
        byte[] utf8Bytes = result.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8Bytes.length).isLessThanOrEqualTo(LIMIT);

        // Verify valid Unicode without corrupt characters
        String reEncoded = new String(utf8Bytes, StandardCharsets.UTF_8);
        assertThat(reEncoded).isEqualTo(result);
        assertThat(reEncoded).doesNotContain("\uFFFD");
    }

    @Test
    @DisplayName("Emoji and supplementary Unicode characters near the truncation boundary are never split")
    void testEmojiAndSupplementaryUnicodeNearBoundary() {
        // Truncation budget for content = 65,535 - 17 = 65,518 bytes
        // Case 1: Pad with ASCII up to 65,516 bytes (budget - 2)
        // Followed by emoji 😀 (U+1F600: 2 UTF-16 code units, 4 UTF-8 bytes)
        // 65,516 + 4 = 65,520 > budget (65,518). Emoji cannot fit in remaining 2 bytes.
        String pad = "X".repeat(LIMIT - 17 - 2);
        String emoji = "\uD83D\uDE00";
        // Total bytes = 65,516 + 4 + 4 + 4 + 100 = 65,628 > LIMIT (65,535) -> Truncation triggered!
        String input = pad + emoji + emoji + emoji + "Y".repeat(100);

        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).isNotNull();
        assertThat(result).endsWith(SubmissionPersistenceService.TRUNCATION_MARKER);
        byte[] utf8Bytes = result.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8Bytes.length).isLessThanOrEqualTo(LIMIT);

        // Content before marker must NOT end with a dangling high surrogate
        int markerIdx = result.indexOf(SubmissionPersistenceService.TRUNCATION_MARKER);
        assertThat(markerIdx).isGreaterThan(0);
        char charBeforeMarker = result.charAt(markerIdx - 1);
        assertThat(Character.isHighSurrogate(charBeforeMarker)).isFalse();
        assertThat(charBeforeMarker).isEqualTo('X');

        // Case 2: Emoji fits exactly in remaining budget
        // Pad with ASCII up to 65,514 bytes (budget - 4)
        // Emoji fits in the remaining 4 bytes, then next emoji cannot fit
        String pad2 = "Z".repeat(LIMIT - 17 - 4);
        String input2 = pad2 + emoji + emoji + "W".repeat(100);

        String result2 = SubmissionPersistenceService.safeTruncateUtf8(input2, LIMIT);

        assertThat(result2).isNotNull();
        assertThat(result2).endsWith(SubmissionPersistenceService.TRUNCATION_MARKER);
        byte[] utf8Bytes2 = result2.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8Bytes2.length).isLessThanOrEqualTo(LIMIT);

        int markerIdx2 = result2.indexOf(SubmissionPersistenceService.TRUNCATION_MARKER);
        // Prefix must end with the complete emoji (low surrogate preceded by high surrogate)
        char lastChar = result2.charAt(markerIdx2 - 1);
        char secondLastChar = result2.charAt(markerIdx2 - 2);
        assertThat(Character.isLowSurrogate(lastChar)).isTrue();
        assertThat(Character.isHighSurrogate(secondLastChar)).isTrue();
        assertThat(result2.substring(markerIdx2 - 2, markerIdx2)).isEqualTo(emoji);

        // Verify valid Unicode re-encoding
        String reEncoded2 = new String(utf8Bytes2, StandardCharsets.UTF_8);
        assertThat(reEncoded2).isEqualTo(result2);
        assertThat(reEncoded2).doesNotContain("\uFFFD");
    }

    @Test
    @DisplayName("Exact boundary: String of exactly 65,535 bytes is preserved without truncation marker")
    void testExactBoundaryUnchanged() {
        String input = "B".repeat(LIMIT);
        assertThat(input.getBytes(StandardCharsets.UTF_8).length).isEqualTo(LIMIT);

        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).isSameAs(input);
        assertThat(result).doesNotContain(SubmissionPersistenceService.TRUNCATION_MARKER);
        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("Exact boundary: String of 65,536 bytes triggers truncation and fits within 65,535 bytes")
    void testExactBoundaryPlusOneTruncated() {
        String input = "C".repeat(LIMIT + 1);
        assertThat(input.getBytes(StandardCharsets.UTF_8).length).isEqualTo(LIMIT + 1);

        String result = SubmissionPersistenceService.safeTruncateUtf8(input, LIMIT);

        assertThat(result).endsWith(SubmissionPersistenceService.TRUNCATION_MARKER);
        byte[] utf8Bytes = result.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8Bytes.length).isLessThanOrEqualTo(LIMIT);
    }

    @Test
    @DisplayName("Small byte budget where budget is smaller than truncation marker length")
    void testSmallByteBudget() {
        String input = "ABCDEFGHIJ";
        String result = SubmissionPersistenceService.safeTruncateUtf8(input, 5);

        assertThat(result).isEqualTo("ABCDE");
        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(5);
    }
}
