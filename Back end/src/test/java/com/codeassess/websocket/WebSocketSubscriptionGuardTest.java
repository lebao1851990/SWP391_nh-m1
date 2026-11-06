package com.codeassess.websocket;

import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.websocket.security.WebSocketSubscriptionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WebSocketSubscriptionGuardTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private WebSocketSubscriptionGuard guard;

    private User studentAlice;
    private User studentBob;
    private Submission aliceSubmission;

    @BeforeEach
    void setUp() {
        studentAlice = User.builder().id("user-alice").email("alice@uni.edu").role(Role.STUDENT).build();
        studentBob = User.builder().id("user-bob").email("bob@uni.edu").role(Role.STUDENT).build();

        aliceSubmission = Submission.builder()
                .id("sub-alice-001")
                .student(studentAlice)
                .build();
    }

    @Test
    @DisplayName("Admin can subscribe to any channel format")
    void testAdminSubscriptionAllowed() {
        assertTrue(guard.isAuthorized("user:any-user", "admin-id", "ADMIN"));
        assertTrue(guard.isAuthorized("submission:sub-xyz", "admin-id", "ADMIN"));
        assertTrue(guard.isAuthorized("exam:exam-123", "admin-id", "ADMIN"));
        assertTrue(guard.isAuthorized("queue:bullmq", "admin-id", "ADMIN"));
    }

    @Test
    @DisplayName("Student can subscribe to own user channel but not other students")
    void testStudentUserChannelIsolation() {
        // Own user channel
        assertTrue(guard.isAuthorized("user:user-alice", "user-alice", "STUDENT"));

        // Another student's channel
        assertFalse(guard.isAuthorized("user:user-bob", "user-alice", "STUDENT"),
                "Student Alice must not subscribe to Student Bob's personal channel");
    }

    @Test
    @DisplayName("Student can subscribe to own submission but is rejected for others")
    void testStudentSubmissionChannelIsolation() {
        when(submissionRepository.findById("sub-alice-001")).thenReturn(Optional.of(aliceSubmission));

        // Alice accessing her own submission
        assertTrue(guard.isAuthorized("submission:sub-alice-001", "user-alice", "STUDENT"));

        // Bob attempting to spy on Alice's submission
        assertFalse(guard.isAuthorized("submission:sub-alice-001", "user-bob", "STUDENT"),
                "Student Bob must be forbidden from subscribing to Alice's submission");
    }

    @Test
    @DisplayName("Student is forbidden from exam-wide and queue channels")
    void testStudentForbiddenFromExamAndQueue() {
        assertFalse(guard.isAuthorized("exam:exam-final", "user-alice", "STUDENT"));
        assertFalse(guard.isAuthorized("queue:bullmq", "user-alice", "STUDENT"));
    }

    @Test
    @DisplayName("Teacher is authorized for courses, exams, submissions and queue telemetry")
    void testTeacherAuthorized() {
        assertTrue(guard.isAuthorized("submission:sub-alice-001", "teacher-id", "TEACHER"));
        assertTrue(guard.isAuthorized("exam:exam-final", "teacher-id", "TEACHER"));
        assertTrue(guard.isAuthorized("queue:bullmq", "teacher-id", "TEACHER"));
    }

    @Test
    @DisplayName("Invalid or malformed channels are rejected")
    void testMalformedChannelsRejected() {
        assertFalse(guard.isAuthorized(null, "user-alice", "STUDENT"));
        assertFalse(guard.isAuthorized("", "user-alice", "STUDENT"));
        assertFalse(guard.isAuthorized("unrecognized:channel:name", "user-alice", "STUDENT"));
    }
}

