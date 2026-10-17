package com.codeassess.submission.dto;

import java.util.List;

/**
 * Encapsulates the result of scanning a project workspace for executable Java main methods.
 */
public record EntryPointResult(
        Status status,
        String entryPointClass,
        List<String> candidates,
        String message
) {
    public enum Status {
        FOUND,
        NONE,
        AMBIGUOUS,
        INVALID_REQUESTED
    }

    public static EntryPointResult found(String entryPointClass, List<String> candidates) {
        return new EntryPointResult(Status.FOUND, entryPointClass, candidates, "Entry point identified: " + entryPointClass);
    }

    public static EntryPointResult none() {
        return new EntryPointResult(Status.NONE, null, List.of(), "No executable main method found in project");
    }

    public static EntryPointResult ambiguous(List<String> candidates) {
        return new EntryPointResult(Status.AMBIGUOUS, null, candidates,
                "Multiple main methods found (" + String.join(", ", candidates) + "). Ambiguous entry point.");
    }

    public static EntryPointResult invalidRequested(String requested, String reason) {
        return new EntryPointResult(Status.INVALID_REQUESTED, null, List.of(),
                "Invalid requested entry point '" + requested + "': " + reason);
    }
}
