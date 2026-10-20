package com.codeassess.testcase.util;

public final class OutputNormalizer {

    private OutputNormalizer() {}

    /**
     * Normalizes output by converting Windows/Mac newlines to Unix newlines (\n),
     * stripping trailing whitespace from each line, and stripping trailing empty newlines.
     * Preserves internal spacing, punctuation, case, and Unicode characters.
     *
     * @param raw the raw stdout string
     * @return normalized string, never null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        // 1. Normalize line breaks to \n
        String text = raw.replace("\r\n", "\n").replace("\r", "\n");

        // 2. Split lines and strip trailing whitespace from each line
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            // Strip trailing whitespace (spaces, tabs)
            String line = stripTrailingSpaces(lines[i]);
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }

        // 3. Strip trailing newlines and spaces from the very end of output
        String result = sb.toString();
        while (result.endsWith("\n") || result.endsWith(" ") || result.endsWith("\t")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    /**
     * Exact comparison after normalization. Does NOT use contains().
     *
     * @param actual actual stdout from program execution
     * @param expected expected output configured for test case
     * @return true if outputs match exactly after standard whitespace/newline normalization
     */
    public static boolean matches(String actual, String expected) {
        String normActual = normalize(actual);
        String normExpected = normalize(expected);
        return normActual.equals(normExpected);
    }

    private static String stripTrailingSpaces(String s) {
        int len = s.length();
        while (len > 0 && (s.charAt(len - 1) == ' ' || s.charAt(len - 1) == '\t')) {
            len--;
        }
        return (len < s.length()) ? s.substring(0, len) : s;
    }
}
