package com.codeassess.exam.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamTest {

    @Test
    @DisplayName("Upcoming scheduled exam with status 'upcoming' or 'scheduled' is NOT concluded (AI locked)")
    void testUpcomingExam_NotConcluded() {
        Exam upcoming = Exam.builder()
                .status("upcoming")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).plusHours(2))
                .duration(90)
                .build();
        assertFalse(upcoming.isConcluded());

        Exam scheduled = Exam.builder()
                .status("scheduled")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).plusDays(1))
                .duration(60)
                .build();
        assertFalse(scheduled.isConcluded());

        Exam draft = Exam.builder()
                .status("draft")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).plusDays(1))
                .duration(60)
                .build();
        assertFalse(draft.isConcluded());
    }

    @Test
    @DisplayName("Active exam before deadline is NOT concluded (AI locked)")
    void testActiveExam_BeforeDeadline_NotConcluded() {
        Exam active = Exam.builder()
                .status("active")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).minusMinutes(30))
                .duration(90) // 60 minutes remaining
                .build();
        assertFalse(active.isConcluded());
    }

    @Test
    @DisplayName("Active exam exactly at deadline is NOT concluded (AI locked)")
    void testActiveExam_AtDeadline_NotConcluded() {
        // Start time + duration equals exactly now
        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        Exam active = Exam.builder()
                .status("active")
                .examDate(now.minusMinutes(60))
                .duration(60) // Deadline is right now
                .build();
        // now.isAfter(deadline) will be false when now <= deadline
        assertFalse(active.isConcluded());
    }

    @Test
    @DisplayName("Active exam after deadline IS concluded (AI unlocked)")
    void testActiveExam_AfterDeadline_Concluded() {
        Exam active = Exam.builder()
                .status("active")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).minusMinutes(120))
                .duration(90) // Ended 30 minutes ago
                .build();
        assertTrue(active.isConcluded());
    }

    @Test
    @DisplayName("Exams with concluded statuses (completed, closed, archived, ended, cancelled, canceled) ARE concluded")
    void testConcludedStatuses_Concluded() {
        String[] statuses = {"completed", "closed", "archived", "ended", "cancelled", "canceled", "COMPLETED", "CLOSED", "CANCELLED"};
        for (String status : statuses) {
            Exam exam = Exam.builder().status(status).build();
            assertTrue(exam.isConcluded(), "Expected status " + status + " to be concluded");
        }
    }

    @Test
    @DisplayName("Soft-deleted exam (deletedAt != null) IS concluded regardless of status")
    void testSoftDeletedExam_Concluded() {
        Exam exam = Exam.builder()
                .status("active")
                .deletedAt(LocalDateTime.now(Exam.APP_ZONE))
                .build();
        assertTrue(exam.isConcluded());
    }

    @Test
    @DisplayName("Active exam missing time data or with invalid duration is NOT concluded (fail-safe lock)")
    void testActiveExam_MissingTimeData_NotConcluded() {
        Exam noDate = Exam.builder()
                .status("active")
                .examDate(null)
                .duration(60)
                .build();
        assertFalse(noDate.isConcluded());

        Exam noDuration = Exam.builder()
                .status("active")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).minusHours(2))
                .duration(null)
                .build();
        assertFalse(noDuration.isConcluded());

        Exam zeroDuration = Exam.builder()
                .status("active")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).minusHours(2))
                .duration(0)
                .build();
        assertFalse(zeroDuration.isConcluded());

        Exam negativeDuration = Exam.builder()
                .status("active")
                .examDate(LocalDateTime.now(Exam.APP_ZONE).minusHours(2))
                .duration(-10)
                .build();
        assertFalse(negativeDuration.isConcluded());
    }

    @Test
    @DisplayName("Null, blank, or unknown status is NOT concluded (fail-safe lock)")
    void testUnknownOrEmptyStatus_NotConcluded() {
        Exam nullStatus = Exam.builder().status(null).build();
        assertFalse(nullStatus.isConcluded());

        Exam blankStatus = Exam.builder().status("   ").build();
        assertFalse(blankStatus.isConcluded());

        Exam unknownStatus1 = Exam.builder().status("UNKNOWN").build();
        assertFalse(unknownStatus1.isConcluded());

        Exam unknownStatus2 = Exam.builder().status("PENDING_REVIEW").build();
        assertFalse(unknownStatus2.isConcluded());
    }
}
