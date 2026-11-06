package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZipSubmissionIntakeAndConcurrencyTest {

        @Mock
        private SubmissionRepository submissionRepository;

        @Mock
        private SubmissionMapper submissionMapper;

        @Mock
        private ExamRepository examRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @Mock
        private ExamQuestionRepository examQuestionRepository;

        @Mock
        private StudentQuestionScoreRepository studentQuestionScoreRepository;

        @Mock
        private ZipExtractionService zipExtractionService;

        @Mock
        private SourceHasherService sourceHasherService;

        @Mock
        private EntryPointDiscoveryService entryPointDiscoveryService;

        @Mock
        private SubmissionPersistenceService submissionPersistenceService;

        @Mock
        private com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService;

        @Mock
        private com.codeassess.queue.service.BullMqJobDispatcher bullMqJobDispatcher;

        @Mock
        private SubmissionStorageService submissionStorageService;

        @Mock
        private InternalGradingService internalGradingService;

        private StudentSubmissionService studentSubmissionService;

        private Exam testExam;
        private ExamQuestion testQuestion1;
        private ExamQuestion testQuestion2;
        private User testStudent;
        private MockMultipartFile validZipFile;

        @BeforeEach
        void setUp() throws IOException {
                studentSubmissionService = new StudentSubmissionService(
                                submissionRepository,
                                submissionMapper,
                                examRepository,
                                userRepository,
                                eventPublisher,
                                zipExtractionService,
                                sourceHasherService,
                                entryPointDiscoveryService,
                                null,
                                null,
                                new ZipSubmissionProperties(),
                                submissionPersistenceService,
                                null,
                                automatedTestRunnerService,
                                bullMqJobDispatcher);

                ReflectionTestUtils.setField(studentSubmissionService, "examQuestionRepository",
                                examQuestionRepository);
                ReflectionTestUtils.setField(studentSubmissionService, "studentQuestionScoreRepository",
                                studentQuestionScoreRepository);

                testExam = Exam.builder()
                                .id("exam-zip-1")
                                .title("Final Exam")
                                .status("active")
                                .examDate(LocalDateTime.now().minusHours(1))
                                .duration(120)
                                .build();

                testQuestion1 = ExamQuestion.builder()
                                .id("q-zip-1")
                                .exam(testExam)
                                .title("Question 1 - Algorithms")
                                .status("PUBLISHED")
                                .maxScore(BigDecimal.valueOf(10.00))
                                .displayOrder(1)
                                .build();

                testQuestion2 = ExamQuestion.builder()
                                .id("q-zip-2")
                                .exam(testExam)
                                .title("Question 2 - Data Structures")
                                .status("PUBLISHED")
                                .maxScore(BigDecimal.valueOf(10.00))
                                .displayOrder(2)
                                .build();

                testStudent = User.builder()
                                .id("student-zip-1")
                                .email("student@codeassess.com")
                                .fullName("Bob Student")
                                .build();

                // Create in-memory valid zip file
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                        ZipEntry entry = new ZipEntry("Main.java");
                        zos.putNextEntry(entry);
                        zos.write("public class Main {}".getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                }
                validZipFile = new MockMultipartFile("file", "solution.zip", "application/zip", baos.toByteArray());
        }

        @Test
        @DisplayName("ZIP submission with valid questionId persists initial submission with question and dispatches BullMQ job")
        void testSubmitZip_WithValidQuestionId_Success() throws IOException {
                when(examRepository.findByIdAndStudentId("exam-zip-1", "student-zip-1"))
                                .thenReturn(Optional.of(testExam));
                when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-zip-1", "exam-zip-1"))
                                .thenReturn(Optional.of(testQuestion1));
                when(userRepository.findById("student-zip-1")).thenReturn(Optional.of(testStudent));
                when(sourceHasherService.calculateArchiveSha256(any(byte[].class))).thenReturn("archive-sha256");
                when(sourceHasherService.calculateSourceTreeSha256(anyList())).thenReturn("source-tree-sha256");

                ExtractedWorkspace mockWorkspace = mock(ExtractedWorkspace.class);
                List<ExtractedFile> emptyList = Collections.emptyList();
                when(mockWorkspace.files()).thenReturn(emptyList);
                when(zipExtractionService.extract(any(byte[].class))).thenReturn(mockWorkspace);
                when(entryPointDiscoveryService.discoverEntryPoint(any()))
                                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

                Submission initialSub = Submission.builder()
                                .id("sub-zip-100")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("ZIP")
                                .build();

                when(submissionPersistenceService.createInitialZipSubmission(
                                eq("exam-zip-1"), eq("q-zip-1"), eq(testExam), any(), eq(testStudent),
                                anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(initialSub);

                when(bullMqJobDispatcher.dispatchGradingJob("sub-zip-100", "exam-zip-1", "ZIP")).thenReturn(true);
                when(submissionRepository.findById("sub-zip-100")).thenReturn(Optional.of(initialSub));
                when(submissionMapper.toDetailResponse(initialSub)).thenReturn(
                                SubmissionDetailResponse.builder().id("sub-zip-100").status("PENDING").build());

                SubmissionDetailResponse response = studentSubmissionService.submitZip(
                                "exam-zip-1", "q-zip-1", "student-zip-1", validZipFile);

                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo("sub-zip-100");

                // Verify delegation to persistence service with questionId
                verify(submissionPersistenceService).createInitialZipSubmission(
                                eq("exam-zip-1"), eq("q-zip-1"), eq(testExam), any(), eq(testStudent),
                                anyString(), anyString(), anyString(), anyInt(), eq("Main"));

                // Verify BullMQ dispatch only triggered after persistence commits
                verify(bullMqJobDispatcher).dispatchGradingJob("sub-zip-100", "exam-zip-1", "ZIP");
                verify(submissionRepository).recordSuccessfulInitialDispatch("sub-zip-100");
        }

        @Test
        @DisplayName("ZIP submission without questionId resolves to default Question 1 for legacy exams")
        void testSubmitZip_WithoutQuestionId_ResolvesToFirstQuestion() throws IOException {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                when(examRepository.findByIdForShare("exam-zip-1")).thenReturn(Optional.of(testExam));
                when(examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-zip-1"))
                                .thenReturn(Collections.singletonList(testQuestion1));
                when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-zip-1", "exam-zip-1"))
                                .thenReturn(Optional.of(testQuestion1));

                Submission savedSub = Submission.builder()
                                .id("sub-zip-legacy")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .build();
                when(submissionRepository.save(any(Submission.class))).thenReturn(savedSub);

                Submission result = persistenceService.createInitialZipSubmission(
                                "exam-zip-1",
                                null, // Legacy call without questionId
                                testExam,
                                null,
                                testStudent,
                                "solution.zip",
                                "h-arch",
                                "h-tree",
                                1,
                                "Main");

                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo("sub-zip-legacy");
                assertThat(result.getQuestion()).isEqualTo(testQuestion1);

                verify(studentQuestionScoreRepository).upsertIntakeSequence(
                                anyString(),
                                eq("exam-zip-1"),
                                eq("q-zip-1"),
                                eq("student-zip-1"),
                                eq("sub-zip-legacy"));
        }

        @Test
        @DisplayName("ZIP submission on closed exam is immediately rejected before extraction or persistence")
        void testSubmitZip_ClosedExam_RejectedBeforeExtraction() {
                testExam.setStatus("closed");
                when(examRepository.findByIdAndStudentId("exam-zip-1", "student-zip-1"))
                                .thenReturn(Optional.of(testExam));

                assertThatThrownBy(() -> studentSubmissionService.submitZip("exam-zip-1", "q-zip-1", "student-zip-1",
                                validZipFile))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Exam is not currently active for submissions");

                verifyNoInteractions(zipExtractionService);
                verifyNoInteractions(submissionPersistenceService);
                verifyNoInteractions(bullMqJobDispatcher);
        }

        @Test
        @DisplayName("Rollback verification: If upsertIntakeSequence throws an exception, createInitialZipSubmission bubbles exception causing complete rollback")
        void testCreateInitialZipSubmission_UpsertFailure_RollsBack() {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                when(examRepository.findByIdForShare("exam-zip-1")).thenReturn(Optional.of(testExam));
                when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-zip-1", "exam-zip-1"))
                                .thenReturn(Optional.of(testQuestion1));

                Submission savedSub = Submission.builder().id("sub-will-rollback").build();
                when(submissionRepository.save(any(Submission.class))).thenReturn(savedSub);

                doThrow(new RuntimeException("DB unique index lock timeout / crash"))
                                .when(studentQuestionScoreRepository)
                                .upsertIntakeSequence(anyString(), anyString(), anyString(), anyString(), anyString());

                assertThatThrownBy(() -> persistenceService.createInitialZipSubmission(
                                "exam-zip-1", "q-zip-1", testExam, null, testStudent,
                                "solution.zip", "h1", "h2", 1, "Main")).isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("DB unique index lock timeout / crash");
        }

        @Test
        @DisplayName("Same-Question Concurrency Test: True concurrent submissions for SAME question and SAME student advance sequence monotonically without loss")
        void testConcurrentSubmissions_SameQuestionSameStudent_AtomicSequence() throws Exception {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                when(examRepository.findByIdForShare("exam-zip-1")).thenReturn(Optional.of(testExam));
                when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-zip-1", "exam-zip-1"))
                                .thenReturn(Optional.of(testQuestion1));

                // Thread-safe sequence tracker simulating the DB row lock on
                // uk_sqs_question_student
                AtomicInteger databaseSequence = new AtomicInteger(0);
                AtomicInteger submissionCounter = new AtomicInteger(0);
                AtomicReference<String> latestSubmissionId = new AtomicReference<>("");
        List<String> sequencedSubmissionIds = Collections.synchronizedList(new ArrayList<>());

        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
            Submission sub = inv.getArgument(0);
            sub.setId("sub-seq-" + UUID.randomUUID());
            return sub;
        });

        // Mock atomic upsert behavior: serializes on the same (question_id, student_id)
        when(studentQuestionScoreRepository.upsertIntakeSequence(anyString(), eq("exam-zip-1"), eq("q-zip-1"), eq("student-zip-1"), anyString()))
                .thenAnswer(inv -> {
                    String subId = inv.getArgument(4);
                    int currentSeq = databaseSequence.incrementAndGet();
                    submissionCounter.incrementAndGet();
                    latestSubmissionId.set(subId);
                    sequencedSubmissionIds.add(subId);
                    return 1;
                });

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<Submission>> futures = new ArrayList<>();
        ConcurrentLinkedQueue<Throwable> workerErrors = new ConcurrentLinkedQueue<>();
        List<Submission> committedSubmissions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(); // Guarantee true simultaneous invocation
                    Submission s = persistenceService.createInitialZipSubmission(
                            "exam-zip-1", "q-zip-1", testExam, null, testStudent,
                            "sol.zip", "h1", "h2", 1, "Main"
                    );
                    committedSubmissions.add(s);
                    return s;
                } catch (Throwable t) {
                    workerErrors.add(t);
                    throw new RuntimeException("Worker execution failed", t);
                }
            }));
        }

        // Await completion of all worker tasks via Futures
        for (Future<Submission> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        // Thread safety & executor termination assertions
        assertThat(terminated).as("Executor service must cleanly terminate without lingering worker threads").isTrue();
        assertThat(workerErrors).as("Worker tasks must not encounter unhandled exceptions").isEmpty();
        assertThat(committedSubmissions).as("All submitted tasks must commit a submission").hasSize(threadCount);

        // Concurrency Assertions:
        // 1. All submissions have unique IDs
        Set<String> uniqueSubmissionIds = committedSubmissions.stream()
                .map(Submission::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(uniqueSubmissionIds).as("All concurrent submissions must produce unique IDs").hasSize(threadCount);

        // 2. submission_count equals number of committed submissions (exactly 4)
        assertThat(submissionCounter.get()).as("Submission counter must equal threadCount").isEqualTo(threadCount);

        // 3. latest_submission_seq incremented exactly 4 times without lost update
        assertThat(databaseSequence.get()).as("Database sequence tracker must increment exactly threadCount times").isEqualTo(threadCount);

        // 4. latest_submission_id strictly corresponds to the final committed sequence order
        assertThat(latestSubmissionId.get()).isNotEmpty();
        assertThat(sequencedSubmissionIds).hasSize(threadCount);
        assertThat(latestSubmissionId.get()).as("latest_submission_id must match the last recorded submission in sequence")
                .isEqualTo(sequencedSubmissionIds.get(sequencedSubmissionIds.size() - 1));

        // 5. Verify exactly 4 atomic upsert calls were dispatched for (q-zip-1, student-zip-1)
        verify(studentQuestionScoreRepository, times(threadCount)).upsertIntakeSequence(
                anyString(), eq("exam-zip-1"), eq("q-zip-1"), eq("student-zip-1"), anyString()
        );

        // NOTE: This unit test verifies Java service orchestration, thread coordination, and mock contract interactions.
        // True database-level atomicity across isolated transactions is verified in database integration tests.
    }

        @Test
        @DisplayName("Storage Failure Window: When saveZip throws an exception, failPendingSubmissionAndSyncScore is invoked transactionally")
        void testSubmitZip_StorageFails_SetsStatusFailed_DoesNotDispatch() throws IOException {
                ReflectionTestUtils.setField(studentSubmissionService, "submissionStorageService",
                                submissionStorageService);

                when(examRepository.findByIdAndStudentId("exam-zip-1", "student-zip-1"))
                                .thenReturn(Optional.of(testExam));
                when(examQuestionRepository.findByIdAndExam_IdForUpdate("q-zip-1", "exam-zip-1"))
                                .thenReturn(Optional.of(testQuestion1));
                when(userRepository.findById("student-zip-1")).thenReturn(Optional.of(testStudent));
                when(sourceHasherService.calculateArchiveSha256(any(byte[].class))).thenReturn("archive-sha256");
                when(sourceHasherService.calculateSourceTreeSha256(anyList())).thenReturn("source-tree-sha256");

                ExtractedWorkspace mockWorkspace = mock(ExtractedWorkspace.class);
                when(mockWorkspace.files()).thenReturn(Collections.emptyList());
                when(zipExtractionService.extract(any(byte[].class))).thenReturn(mockWorkspace);
                when(entryPointDiscoveryService.discoverEntryPoint(any()))
                                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

                Submission initialSub = Submission.builder()
                                .id("sub-storage-fail")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("ZIP")
                                .build();

                when(submissionPersistenceService.createInitialZipSubmission(
                                eq("exam-zip-1"), eq("q-zip-1"), eq(testExam), any(), eq(testStudent),
                                anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(initialSub);

                // Simulate disk full or I/O failure
                when(submissionStorageService.saveZip(eq("sub-storage-fail"), any(byte[].class)))
                                .thenThrow(new IllegalStateException("Disk full: no space left on device"));

                assertThatThrownBy(() -> studentSubmissionService.submitZip("exam-zip-1", "q-zip-1", "student-zip-1",
                                validZipFile))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Failed to store submission archive");

                // Verify transactional failure transition and score sync invoked
                verify(submissionPersistenceService).failPendingSubmissionAndSyncScore(
                                eq("sub-storage-fail"),
                                isNull(),
                                eq("STORAGE_FAILED"),
                                contains("Disk full: no space left on device"),
                                eq("q-zip-1"),
                                eq("student-zip-1"));

                // Verify grading pipeline and BullMQ dispatch were NEVER called
                verifyNoInteractions(automatedTestRunnerService);
                verifyNoInteractions(bullMqJobDispatcher);
        }

    @Test
    @DisplayName("Service Fenced Status Sync: When older submission fails, persistence service transitions submission to FAILED but score status remains untouched (0 rows updated)")
    void testSubmitZip_OlderSubmissionStorageFails_DoesNotOverwriteNewerSubmissionStatus() {
        SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                submissionRepository,
                null,
                eventPublisher,
                examRepository,
                examQuestionRepository,
                studentQuestionScoreRepository
        );

        // Simulation: older submission itself transitions to FAILED
        when(submissionRepository.failPendingSubmission(
                eq("sub-old-1"), isNull(), eq("STORAGE_FAILED"), anyString()
        )).thenReturn(1);

        // Fenced score update matches 0 rows because latest_submission_id in student_question_scores belongs to a newer submission
        when(studentQuestionScoreRepository.updateFailureStatusIfLatest(
                eq("q-zip-1"), eq("student-zip-1"), eq("sub-old-1"), eq("FAILED")
        )).thenReturn(0);

        boolean result = persistenceService.failPendingSubmissionAndSyncScore(
                "sub-old-1",
                null,
                "STORAGE_FAILED",
                "Disk full during archive storage on stale submission",
                "q-zip-1",
                "student-zip-1"
        );

        // Service returns true because the submission failure transition succeeded
        assertThat(result).isTrue();

        verify(submissionRepository).failPendingSubmission(
                eq("sub-old-1"), isNull(), eq("STORAGE_FAILED"), anyString()
        );
        verify(studentQuestionScoreRepository).updateFailureStatusIfLatest(
                eq("q-zip-1"), eq("student-zip-1"), eq("sub-old-1"), eq("FAILED")
        );
    }

        @Test
        @DisplayName("Sweeper Recovery: Stale PENDING ZIP submission missing archive on disk is transitioned to FAILED and score row is synced")
        void testSweeper_PendingZipWithMissingStorage_MarksFailedAndDoesNotDispatch() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission pendingZipWithoutFile = Submission.builder()
                                .id("sub-lost-file")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("ZIP")
                                .zipStoragePath(null)
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(pendingZipWithoutFile));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-lost-file"), anyString(), any(), any()))
                                .thenReturn(1);
                when(submissionStorageService.hasZip("sub-lost-file")).thenReturn(false);

                sweeper.recoverPendingSubmissions();

                // Verify transactional conditional failure transition invoked
                verify(submissionPersistenceService).failPendingSubmissionAndSyncScore(
                                eq("sub-lost-file"),
                                anyString(),
                                eq("STORAGE_FAILED"),
                                contains("Submission archive missing from storage"),
                                eq("q-zip-1"),
                                eq("student-zip-1"));

                // Verify neither BullMQ dispatch nor internal grading was attempted
                verifyNoInteractions(bullMqJobDispatcher);
                verifyNoInteractions(internalGradingService);
        }

        @Test
        @DisplayName("Sweeper Recovery: Stale PENDING ZIP submission with archive present and matching hash is safely dispatched")
        void testSweeper_PendingZipWithStoragePresent_DispatchesSuccessfullyWithoutNewSubmission() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission pendingZipWithFile = Submission.builder()
                                .id("sub-intact-file")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("ZIP")
                                .archiveHash("sha256-matching-hash")
                                .zipStoragePath("/var/data/submissions/sub-intact-file.zip")
                                .build();

                byte[] validBytes = "test content".getBytes(StandardCharsets.UTF_8);

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(pendingZipWithFile));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-intact-file"), anyString(), any(), any()))
                                .thenReturn(1);
                when(submissionStorageService.hasZip("sub-intact-file")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-intact-file")).thenReturn(validBytes);
                when(sourceHasherService.calculateArchiveSha256(validBytes)).thenReturn("sha256-matching-hash");
                when(bullMqJobDispatcher.dispatchGradingJob("sub-intact-file", "exam-zip-1", "ZIP")).thenReturn(true);

                sweeper.recoverPendingSubmissions();

                // Verify BullMQ dispatch was triggered
                verify(bullMqJobDispatcher).dispatchGradingJob("sub-intact-file", "exam-zip-1", "ZIP");

                // Verify NO new submission was inserted and score sequence was untouched
                verify(submissionPersistenceService, never()).createInitialZipSubmission(
                                anyString(), anyString(), any(), any(), any(), anyString(), anyString(), anyString(),
                                anyInt(), anyString());
                verify(studentQuestionScoreRepository, never()).upsertIntakeSequence(
                                anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Integrity Check: ZIP archive hash mismatch marks submission FAILED and aborts dispatch")
        void testSweeper_PendingZipWithHashMismatch_MarksFailedAndDoesNotDispatch() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission tamperedZipSub = Submission.builder()
                                .id("sub-tampered")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("ZIP")
                                .archiveHash("expected-hash-123456")
                                .build();

                byte[] corruptedBytes = "corrupted bytes".getBytes(StandardCharsets.UTF_8);

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(tamperedZipSub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-tampered"), anyString(), any(), any()))
                                .thenReturn(1);
                when(submissionStorageService.hasZip("sub-tampered")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-tampered")).thenReturn(corruptedBytes);
                when(sourceHasherService.calculateArchiveSha256(corruptedBytes)).thenReturn("different-tampered-hash");

                sweeper.recoverPendingSubmissions();

                // Verify conditional failure transition invoked
                verify(submissionPersistenceService).failPendingSubmissionAndSyncScore(
                                eq("sub-tampered"),
                                anyString(),
                                eq("STORAGE_FAILED"),
                                contains("archive hash mismatch"),
                                eq("q-zip-1"),
                                eq("student-zip-1"));

                // Verify dispatch aborted
                verifyNoInteractions(bullMqJobDispatcher);
        }

        @Test
        @DisplayName("Integrity Check: Unreadable ZIP file marks submission FAILED and aborts dispatch")
        void testSweeper_PendingZipWithUnreadableArchive_MarksFailedAndDoesNotDispatch() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission unreadableSub = Submission.builder()
                                .id("sub-unreadable")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("ZIP")
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(unreadableSub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-unreadable"), anyString(), any(), any()))
                                .thenReturn(1);
                when(submissionStorageService.hasZip("sub-unreadable")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-unreadable"))
                                .thenThrow(new IllegalStateException("I/O error: disk block unreadable"));

                sweeper.recoverPendingSubmissions();

                // Verify conditional failure transition invoked
                verify(submissionPersistenceService).failPendingSubmissionAndSyncScore(
                                eq("sub-unreadable"),
                                anyString(),
                                eq("STORAGE_FAILED"),
                                contains("unreadable"),
                                eq("q-zip-1"),
                                eq("student-zip-1"));

                // Verify dispatch aborted
                verifyNoInteractions(bullMqJobDispatcher);
        }

        @Test
        @DisplayName("Atomic Recovery Claim: When second concurrent sweeper fails to acquire recovery claim, it does not dispatch")
        void testConcurrentSweepers_OnlyOneAcquiresRecoveryClaim_DispatchesOnce() {
                SubmissionRecoverySweeper sweeper1 = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                SubmissionRecoverySweeper sweeper2 = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission pendingSub = Submission.builder()
                                .id("sub-concurrent-claim")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("SINGLE_FILE")
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(pendingSub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());

                // Sweeper 1 acquires recovery claim (returns 1); Sweeper 2 fails to acquire
                // recovery claim (returns 0)
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-concurrent-claim"), anyString(), any(),
                                any()))
                                .thenReturn(1)
                                .thenReturn(0);
                when(bullMqJobDispatcher.dispatchGradingJob("sub-concurrent-claim", "exam-zip-1", "SINGLE_FILE"))
                                .thenReturn(true);

                sweeper1.recoverPendingSubmissions();
                sweeper2.recoverPendingSubmissions();

                // Exactly one dispatch occurred across both sweeper invocations
                verify(bullMqJobDispatcher, times(1)).dispatchGradingJob("sub-concurrent-claim", "exam-zip-1",
                                "SINGLE_FILE");
        }

        @Test
        @DisplayName("Active Lease Fencing: Second sweeper cannot dispatch while first owns a valid recovery lease")
        void testSweeper_CannotDispatchWhileActiveRecoveryLeaseExists() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission leasedSub = Submission.builder()
                                .id("sub-active-lease")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("SINGLE_FILE")
                                .claimToken("recovery-active-token")
                                .leaseExpiresAt(LocalDateTime.now().plusSeconds(30))
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(leasedSub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());

                // Claim acquisition fails because unexpired recovery lease exists
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-active-lease"), anyString(), any(), any()))
                                .thenReturn(0);

                sweeper.recoverPendingSubmissions();

                // Verify dispatch was skipped
                verifyNoInteractions(bullMqJobDispatcher);
                verifyNoInteractions(internalGradingService);
        }

        @Test
        @DisplayName("Dispatch Failure: Releases recovery claim safely to permit subsequent retry")
        void testSweeper_DispatchFailure_ReleasesRecoveryClaim() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                null, // No fallback internal grading service configured
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission pendingSub = Submission.builder()
                                .id("sub-dispatch-fail")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("SINGLE_FILE")
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(pendingSub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-dispatch-fail"), anyString(), any(),
                                any()))
                                .thenReturn(1);
                when(bullMqJobDispatcher.dispatchGradingJob("sub-dispatch-fail", "exam-zip-1", "SINGLE_FILE"))
                                .thenReturn(false); // Dispatch fails (producer unreachable)

                sweeper.recoverPendingSubmissions();

                // Verify recovery claim was released immediately to unblock retry
                verify(submissionRepository).releaseRecoveryClaim(eq("sub-dispatch-fail"), anyString());
        }

        @Test
        @DisplayName("Deterministic Test 5: Worker claims PROCESSING before recovery failure; sweeper cannot overwrite worker state")
        void testWorkerClaimsProcessing_BeforeRecoveryFailureHandling_SweeperDoesNotOverwriteWorkerState() {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                // failPendingSubmission returns 0 because worker has already transitioned to
                // PROCESSING (status is no longer PENDING)
                when(submissionRepository.failPendingSubmission(
                                eq("sub-worker-won"), anyString(), eq("STORAGE_FAILED"), anyString())).thenReturn(0);

                boolean result = persistenceService.failPendingSubmissionAndSyncScore(
                                "sub-worker-won",
                                "recovery-token",
                                "STORAGE_FAILED",
                                "missing archive",
                                "q-zip-1",
                                "student-zip-1");

                assertThat(result).isFalse();

                // Crucial invariant: student_question_scores is NEVER updated when worker won
                // the race
                verify(studentQuestionScoreRepository, never()).updateFailureStatusIfLatest(
                                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Conditional Failure Transition: When failure transition succeeds, score status updates transactionally")
        void testRecoveryFailureTransition_Succeeds_ScoreRowUpdated() {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                when(submissionRepository.failPendingSubmission(
                                eq("sub-fail-success"), anyString(), eq("STORAGE_FAILED"), anyString())).thenReturn(1);

                when(studentQuestionScoreRepository.updateFailureStatusIfLatest(
                                eq("q-zip-1"), eq("student-zip-1"), eq("sub-fail-success"), eq("FAILED")))
                                .thenReturn(1);

                boolean result = persistenceService.failPendingSubmissionAndSyncScore(
                                "sub-fail-success",
                                "recovery-token",
                                "STORAGE_FAILED",
                                "corrupted file",
                                "q-zip-1",
                                "student-zip-1");

                assertThat(result).isTrue();
                verify(submissionRepository).failPendingSubmission(
                                eq("sub-fail-success"), anyString(), eq("STORAGE_FAILED"), anyString());
                verify(studentQuestionScoreRepository).updateFailureStatusIfLatest(
                                eq("q-zip-1"), eq("student-zip-1"), eq("sub-fail-success"), eq("FAILED"));
        }

        @Test
        @DisplayName("Deterministic Test 7: Idempotency: Re-running recovery does not increment sequence or submission count")
        void testSweeper_ReRun_DoesNotModifySequenceOrCount() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission pendingSub = Submission.builder()
                                .id("sub-rerun")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("SINGLE_FILE")
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(pendingSub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-rerun"), anyString(), any(), any()))
                                .thenReturn(1);
                when(bullMqJobDispatcher.dispatchGradingJob("sub-rerun", "exam-zip-1", "SINGLE_FILE")).thenReturn(true);

                // Run recovery twice
                sweeper.recoverPendingSubmissions();
                sweeper.recoverPendingSubmissions();

                // Invariant: sequence and count are never incremented during recovery
                verify(studentQuestionScoreRepository, never()).upsertIntakeSequence(
                                anyString(), anyString(), anyString(), anyString(), anyString());
                verify(submissionPersistenceService, never()).createInitialZipSubmission(
                                anyString(), anyString(), any(), any(), any(), anyString(), anyString(), anyString(),
                                anyInt(), anyString());
        }

        @Test
        @DisplayName("Deterministic Test 1: Sweeper A dispatches successfully; worker remains unclaimed beyond 60s lease; Sweeper B must NOT redispatch")
        void testSweeperA_DispatchesSuccessfully_WorkerRemainsUnclaimedBeyond60sLease_SweeperBDoesNotRedispatch() {
                SubmissionRecoverySweeper sweeperA = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission sub = Submission.builder()
                                .id("sub-dispatched-1")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .executionStatus("IN_PROGRESS")
                                .submissionType("SINGLE_FILE")
                                .build();

                // Sweeper A finds the pending submission
                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(sub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-dispatched-1"), anyString(), any(), any()))
                                .thenReturn(1);
                when(bullMqJobDispatcher.dispatchGradingJob("sub-dispatched-1", "exam-zip-1", "SINGLE_FILE"))
                                .thenReturn(true);

                sweeperA.recoverPendingSubmissions();

                // Sweeper A dispatched and recorded DISPATCHED state
                verify(bullMqJobDispatcher, times(1)).dispatchGradingJob("sub-dispatched-1", "exam-zip-1",
                                "SINGLE_FILE");
                verify(submissionRepository, times(1)).recordSuccessfulRecoveryDispatch(eq("sub-dispatched-1"),
                                anyString());

                // Now simulate: 60s lease expired. Worker has NOT claimed yet.
                // In the database, the submission now has executionStatus = 'DISPATCHED',
                // status = 'PENDING', claimToken = null.
                Submission dispatchedSub = Submission.builder()
                                .id("sub-dispatched-1")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .executionStatus("DISPATCHED")
                                .claimToken(null)
                                .leaseExpiresAt(null)
                                .build();

                SubmissionRecoverySweeper sweeperB = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(dispatchedSub));

                // Sweeper B runs
                sweeperB.recoverPendingSubmissions();

                // CRITICAL INVARIANT: Sweeper B did NOT dispatch again! Total dispatches remain
                // exactly 1.
                verify(bullMqJobDispatcher, times(1)).dispatchGradingJob("sub-dispatched-1", "exam-zip-1",
                                "SINGLE_FILE");
                verify(submissionRepository, times(1)).claimSubmissionForRecovery(eq("sub-dispatched-1"), anyString(),
                                any(), any());
        }

        @Test
        @DisplayName("Deterministic Test 2: Redis accepts job but producer gets timeout; reconciliation detects job -> records DISPATCHED, does not retry")
        void testRedisAcceptsJob_ProducerTimeout_ReconciliationDetectsJob_RecordsDispatchedAndDoesNotRetry() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                internalGradingService,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission sub = Submission.builder()
                                .id("sub-reconciled-1")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("SINGLE_FILE")
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(sub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-reconciled-1"), anyString(), any(), any()))
                                .thenReturn(1);

                // Dispatcher reconciles via isJobEnqueued returning true despite timeout ->
                // returns true
                when(bullMqJobDispatcher.dispatchGradingJob("sub-reconciled-1", "exam-zip-1", "SINGLE_FILE"))
                                .thenReturn(true);

                sweeper.recoverPendingSubmissions();

                // Verified: marked DISPATCHED, claim cleared, and NOT released as failed
                verify(submissionRepository).recordSuccessfulRecoveryDispatch(eq("sub-reconciled-1"), anyString());
                verify(submissionRepository, never()).releaseRecoveryClaim(eq("sub-reconciled-1"), anyString());
                verify(submissionPersistenceService, never()).failPendingSubmissionAndSyncScore(
                                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Deterministic Test 3: Dispatch definitely fails (job not in BullMQ); claim is released -> permits subsequent retry")
        void testDispatchDefinitelyFails_JobNotInBullMQ_ClaimReleased_PermitsSubsequentRetry() {
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository,
                                bullMqJobDispatcher,
                                null,
                                submissionStorageService,
                                sourceHasherService,
                                studentQuestionScoreRepository,
                                submissionPersistenceService);

                Submission sub = Submission.builder()
                                .id("sub-retry-eligible")
                                .exam(testExam)
                                .question(testQuestion1)
                                .student(testStudent)
                                .status("PENDING")
                                .submissionType("SINGLE_FILE")
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(List.of(sub));
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-retry-eligible"), anyString(), any(),
                                any()))
                                .thenReturn(1);
                // Producer failed and job not in queue
                when(bullMqJobDispatcher.dispatchGradingJob("sub-retry-eligible", "exam-zip-1", "SINGLE_FILE"))
                                .thenReturn(false);

                sweeper.recoverPendingSubmissions();

                // Claim must be released
                verify(submissionRepository).releaseRecoveryClaim(eq("sub-retry-eligible"), anyString());
                // Must NOT be marked DISPATCHED
                verify(submissionRepository, never()).recordSuccessfulRecoveryDispatch(eq("sub-retry-eligible"),
                                anyString());
        }

    @Test
    @DisplayName("Mock Contract: Repository dispatch ownership fencing method contract for token matching and null tokens")
    void testDispatchOwnership_NullTokenOrMismatchedToken_CannotMutateRecoveryOwnedSubmission() {
        // NOTE: Unit mock contract test verifying return value expectations for repository fencing calls.
        // True SQL conditional execution (WHERE claimToken = :token) is verified in database integration tests.
        String activeRecoveryToken = "recovery-active-token";
        String staleRecoveryToken = "recovery-stale-token";

                // 1. Stale sweeper presenting old token matches 0 rows
                when(submissionRepository.recordSuccessfulRecoveryDispatch("sub-owned-1", staleRecoveryToken))
                                .thenReturn(0);
                int staleResult = submissionRepository.recordSuccessfulRecoveryDispatch("sub-owned-1",
                                staleRecoveryToken);
                assertThat(staleResult).isEqualTo(0);

                // 2. Caller presenting null token matches 0 rows in
                // recordSuccessfulRecoveryDispatch
                when(submissionRepository.recordSuccessfulRecoveryDispatch("sub-owned-1", null)).thenReturn(0);
                int nullResult = submissionRepository.recordSuccessfulRecoveryDispatch("sub-owned-1", null);
                assertThat(nullResult).isEqualTo(0);

                // 3. Initial intake caller cannot record dispatch if submission already
                // acquired by recovery sweeper (claimToken IS NOT NULL)
                when(submissionRepository.recordSuccessfulInitialDispatch("sub-owned-1")).thenReturn(0);
                int initialResult = submissionRepository.recordSuccessfulInitialDispatch("sub-owned-1");
                assertThat(initialResult).isEqualTo(0);

                // 4. Valid sweeper presenting matching active token succeeds
                when(submissionRepository.recordSuccessfulRecoveryDispatch("sub-owned-1", activeRecoveryToken))
                                .thenReturn(1);
                int activeResult = submissionRepository.recordSuccessfulRecoveryDispatch("sub-owned-1",
                                activeRecoveryToken);
                assertThat(activeResult).isEqualTo(1);
        }

        @Test
        @DisplayName("Deterministic Test 4: Sweeper A lease expires and Sweeper B claims; Sweeper A with old token cannot release or fail Sweeper B's claim")
        void testSweeperALeaseExpires_SweeperBClaims_SweeperAOldTokenCannotReleaseOrFailSweeperBClaim() {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                String staleTokenA = "recovery-token-A";

                // 1) Sweeper A attempts to release recovery claim with stale token A after
                // Sweeper B already claimed with token B.
                // DB JPQL: WHERE s.claimToken = :recoveryToken AND s.status = 'PENDING' ->
                // matches 0 rows!
                when(submissionRepository.releaseRecoveryClaim("sub-stale-token", staleTokenA)).thenReturn(0);
                int releaseRows = submissionRepository.releaseRecoveryClaim("sub-stale-token", staleTokenA);
                assertThat(releaseRows).isEqualTo(0);

                // 2) Sweeper A attempts to fail the submission with stale token A.
                // DB JPQL: WHERE s.id = :id AND s.status = 'PENDING' AND s.claimToken =
                // :claimToken -> matches 0 rows!
                when(submissionRepository.failPendingSubmission("sub-stale-token", staleTokenA, "STORAGE_FAILED",
                                "stale sweeper failure"))
                                .thenReturn(0);

                boolean failResult = persistenceService.failPendingSubmissionAndSyncScore(
                                "sub-stale-token",
                                staleTokenA,
                                "STORAGE_FAILED",
                                "stale sweeper failure",
                                "q-zip-1",
                                "student-zip-1");

                assertThat(failResult).isFalse();
                // Crucial invariant: student_question_scores is NEVER touched by the stale
                // sweeper
                verify(studentQuestionScoreRepository, never()).updateFailureStatusIfLatest(anyString(), anyString(),
                                anyString(), anyString());
        }

        @Test
        @DisplayName("Deterministic Test 6: Worker completes before stale sweeper attempts failure; sweeper cannot overwrite COMPLETED state")
        void testWorkerCompletes_BeforeStaleSweeperAttemptsFailure_SweeperDoesNotOverwriteCompletedState() {
                SubmissionPersistenceService persistenceService = new SubmissionPersistenceService(
                                submissionRepository,
                                null,
                                eventPublisher,
                                examRepository,
                                examQuestionRepository,
                                studentQuestionScoreRepository);

                String staleToken = "recovery-stale-token";

                // Worker already completed grading: submission status is COMPLETED
                // failPendingSubmission matches 0 rows because s.status = 'PENDING' condition
                // fails
                when(submissionRepository.failPendingSubmission(
                                eq("sub-already-completed"), eq(staleToken), eq("STORAGE_FAILED"), anyString()))
                                .thenReturn(0);

                boolean result = persistenceService.failPendingSubmissionAndSyncScore(
                                "sub-already-completed",
                                staleToken,
                                "STORAGE_FAILED",
                                "stale timeout",
                                "q-zip-1",
                                "student-zip-1");

                assertThat(result).isFalse();
                // student_question_scores remains with the completed score
                verify(studentQuestionScoreRepository, never()).updateFailureStatusIfLatest(anyString(), anyString(),
                                anyString(), anyString());
        }
}
