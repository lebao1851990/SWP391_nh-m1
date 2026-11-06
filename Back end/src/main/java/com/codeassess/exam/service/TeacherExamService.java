package com.codeassess.exam.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.codeassess.exam.dto.response.ExamQuestionResponse;
import com.codeassess.testcase.dto.response.TestCaseResponse;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.exam.entity.ExamQuestion;

@Service
public class TeacherExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final ClassStudentRepository classStudentRepository;
    private final CourseClassRepository courseClassRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private com.codeassess.plagiarism.repository.SimilarityPairRepository similarityPairRepository;

    @Autowired(required = false)
    private com.codeassess.scoring.repository.ExamRubricCriteriaRepository examRubricCriteriaRepository;

    @Autowired(required = false)
    private com.codeassess.exam.repository.ExamQuestionRepository examQuestionRepository;

    @Autowired(required = false)
    private com.codeassess.testcase.repository.TestCaseRepository testCaseRepository;

    public TeacherExamService(
            ExamRepository examRepository,
            ExamMapper examMapper,
            ClassStudentRepository classStudentRepository,
            CourseClassRepository courseClassRepository,
            SubmissionRepository submissionRepository) {
        this(examRepository, examMapper, classStudentRepository, courseClassRepository, submissionRepository, null);
    }

    @Autowired
    public TeacherExamService(
            ExamRepository examRepository,
            ExamMapper examMapper,
            ClassStudentRepository classStudentRepository,
            CourseClassRepository courseClassRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository) {
        this.examRepository = examRepository;
        this.examMapper = examMapper;
        this.classStudentRepository = classStudentRepository;
        this.courseClassRepository = courseClassRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
    }

    private ExamResponse populateCounts(ExamResponse response) {
        if (response != null) {
            if (response.getClassIds() != null && !response.getClassIds().isEmpty()) {
                response.setEnrolledCount((int) classStudentRepository.countDistinctStudentsByClassIds(response.getClassIds()));
            } else if (response.getClassId() != null) {
                response.setEnrolledCount((int) classStudentRepository.countByCourseClass_Id(response.getClassId()));
            }
            if (response.getId() != null) {
                response.setSubmittedCount((int) submissionRepository.countByExam_Id(response.getId()));
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getTeacherExams(String teacherId) {
        return examRepository.findByCourseClass_Teacher_Id(teacherId).stream()
                .map(examMapper::toResponse)
                .map(this::populateCounts)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getTeacherExamById(String id, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));
        ExamResponse response = populateCounts(examMapper.toResponse(exam));
        if (examRubricCriteriaRepository != null && response != null) {
            var criteria = examRubricCriteriaRepository.findByExam_IdOrderByDisplayOrderAsc(id).stream()
                    .map(c -> new com.codeassess.ai.dto.AiRubricItemDto(c.getName(), c.getDescription(), c.getMaxScore()))
                    .collect(Collectors.toList());
            response.setRubricCriteria(criteria);
        }
        populateQuestionsAndTestCases(response, exam);
        return response;
    }

    @Transactional(readOnly = true)
    public List<com.codeassess.submission.dto.response.TeacherSubmissionListResponse> getExamSubmissions(String examId, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(examId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));
        
        String courseCode = exam.getClasses() != null && !exam.getClasses().isEmpty()
                ? exam.getClasses().stream().map(CourseClass::getCode).sorted().collect(Collectors.joining(", "))
                : (exam.getCourseClass() != null ? exam.getCourseClass().getCode() : "");

        List<com.codeassess.plagiarism.entity.SimilarityPair> pairs = (similarityPairRepository != null)
                ? similarityPairRepository.findByExam_IdAndIsActiveTrue(examId)
                : java.util.Collections.emptyList();

        return submissionRepository.findByExam_Id(examId).stream()
                .map(sub -> {
                    BigDecimal maxSim = null;
                    boolean isFlagged = false;
                    for (com.codeassess.plagiarism.entity.SimilarityPair p : pairs) {
                        if ((p.getSubmissionA() != null && sub.getId().equals(p.getSubmissionA().getId()))
                                || (p.getSubmissionB() != null && sub.getId().equals(p.getSubmissionB().getId()))) {
                            if (p.getSimilarityRate() != null) {
                                if (maxSim == null || p.getSimilarityRate().compareTo(maxSim) > 0) {
                                    maxSim = p.getSimilarityRate();
                                }
                            }
                            if (Boolean.TRUE.equals(p.getIsFlagged())) {
                                isFlagged = true;
                            }
                        }
                    }

                    String plagStatus;
                    if (isFlagged) {
                        plagStatus = "FLAGGED";
                    } else if (maxSim != null) {
                        plagStatus = "SAFE";
                    } else {
                        plagStatus = "NOT_CHECKED";
                    }

                    return com.codeassess.submission.dto.response.TeacherSubmissionListResponse.builder()
                            .id(sub.getId())
                            .studentId(sub.getStudent() != null ? sub.getStudent().getId() : null)
                            .studentName(sub.getStudent() != null ? sub.getStudent().getFullName() : null)
                            .studentEmail(sub.getStudent() != null ? sub.getStudent().getEmail() : null)
                            .examTitle(exam.getTitle())
                            .courseCode(courseCode)
                            .questionId(sub.getQuestion() != null ? sub.getQuestion().getId() : null)
                            .questionTitle(sub.getQuestion() != null ? sub.getQuestion().getTitle() : null)
                            .status(sub.getStatus())
                            .totalScore(sub.getTotalScore())
                            .submittedAt(("COMPLETED".equalsIgnoreCase(sub.getStatus()) || "EVALUATED".equalsIgnoreCase(sub.getStatus())) && sub.getEvaluatedAt() != null
                                    ? sub.getEvaluatedAt()
                                    : sub.getCreatedAt())
                            .originalFilename(sub.getOriginalFilename())
                            .executionStatus(sub.getExecutionStatus())
                            .autoScore(sub.getAutoScore())
                            .testCasesPassed(sub.getTestCasesPassed())
                            .testCasesTotal(sub.getTestCasesTotal())
                            .plagiarismStatus(plagStatus)
                            .maxSimilarityRate(maxSim)
                            .instructorFeedback(sub.getInstructorFeedback())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ExamResponse createExam(CreateExamDto dto, String teacherId) {
        String rawTitle = dto.getTitle();
        if ((rawTitle == null || rawTitle.trim().isEmpty()) && dto.getNewClassCode() != null && !dto.getNewClassCode().isBlank()) {
            rawTitle = "Exam for " + dto.getNewClassCode().trim();
        }
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Tiêu đề bài thi không được để trống hoặc chỉ có khoảng trắng");
        }
        String trimmedTitle = rawTitle.trim();
        if (trimmedTitle.length() > 255) {
            throw new IllegalArgumentException("Tiêu đề bài thi không được vượt quá 255 ký tự");
        }

        if (dto.getDurationMinutes() != null && (dto.getDurationMinutes() < 1 || dto.getDurationMinutes() > 1440)) {
            throw new IllegalArgumentException("Thời lượng bài thi phải từ 1 đến 1440 phút");
        }

        String programmingLanguage = "Java";
        if (dto.getProgrammingLanguage() != null && !dto.getProgrammingLanguage().isBlank()) {
            String lang = dto.getProgrammingLanguage().trim();
            if (!lang.equalsIgnoreCase("Java") && !lang.equalsIgnoreCase("C++") && !lang.equalsIgnoreCase("Python") && !lang.equalsIgnoreCase("JavaScript")) {
                throw new IllegalArgumentException("Ngôn ngữ lập trình không hợp lệ. Chỉ chấp nhận: Java, C++, Python, JavaScript");
            }
            programmingLanguage = lang;
        }

        String initialStatus = "active";
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            initialStatus = dto.getStatus().trim().toLowerCase();
        }
        List<String> targetIds = new ArrayList<>();
        if (dto.getClassIds() != null && !dto.getClassIds().isEmpty()) {
            targetIds.addAll(dto.getClassIds());
        } else if (dto.getClassId() != null && !dto.getClassId().isBlank()) {
            targetIds.add(dto.getClassId());
        }

        List<String> distinctIds = targetIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        List<CourseClass> classes = new ArrayList<>();
        if (!distinctIds.isEmpty()) {
            classes = courseClassRepository.findAllById(distinctIds);
            if (classes.size() != distinctIds.size()) {
                throw new ResourceNotFoundException("One or more classes not found or access denied");
            }
            for (CourseClass cc : classes) {
                if (!cc.getTeacher().getId().equals(teacherId)) {
                    throw new ResourceNotFoundException("Class not found or access denied: " + cc.getId());
                }
            }
        } else if (dto.getNewClassCode() != null && !dto.getNewClassCode().isBlank()) {
            if (userRepository == null) {
                throw new IllegalStateException("UserRepository not initialized");
            }
            String code = dto.getNewClassCode().trim().toUpperCase();
            String name = (dto.getNewClassName() != null && !dto.getNewClassName().isBlank()) 
                    ? dto.getNewClassName().trim() 
                    : code;
            String semester = (dto.getSemester() != null && !dto.getSemester().isBlank()) 
                    ? dto.getSemester().trim() 
                    : "Fall 2026";

            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + teacherId));

            CourseClass courseClass = courseClassRepository.findByCode(code).orElseGet(() -> 
                courseClassRepository.save(CourseClass.builder()
                        .name(name)
                        .code(code)
                        .semester(semester)
                        .teacher(teacher)
                        .build())
            );

            if (!courseClass.getTeacher().getId().equals(teacherId)) {
                throw new ResourceNotFoundException("Class already belongs to another teacher: " + code);
            }
            classes.add(courseClass);
        } else {
            throw new IllegalArgumentException("At least one class must be selected for the exam");
        }

        Exam newExam = Exam.builder()
                .title(trimmedTitle)
                .courseClass(classes.get(0))
                .classes(new HashSet<>(classes))
                .examDate(dto.getDate())
                .duration(dto.getDurationMinutes())
                .programmingLanguage(programmingLanguage)
                .status(initialStatus)
                .build();

        Exam savedExam = examRepository.save(newExam);
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            saveExamQuestionsAndTestCases(savedExam, dto.getQuestions());
        }
        return populateCounts(examMapper.toResponse(savedExam));
    }

    @Transactional
    public ExamResponse updateExam(String id, UpdateExamDto dto, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));

        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        String currentRuntimeStatus = exam.getRuntimeStatus(now);

        if (dto.getTitle() != null) {
            if (dto.getTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Tiêu đề bài thi không được để trống hoặc chỉ có khoảng trắng");
            }
            String trimmed = dto.getTitle().trim();
            if (trimmed.length() > 255) {
                throw new IllegalArgumentException("Tiêu đề bài thi không được vượt quá 255 ký tự");
            }
            exam.setTitle(trimmed);
        }
        if (dto.getDate() != null && (exam.getExamDate() == null || !dto.getDate().isEqual(exam.getExamDate()))) {
            if ("active".equalsIgnoreCase(currentRuntimeStatus)) {
                throw new IllegalStateException("Không thể sửa đổi thời gian bắt đầu khi bài thi đang diễn ra");
            }
            if ("completed".equalsIgnoreCase(currentRuntimeStatus)) {
                throw new IllegalStateException("Không thể sửa đổi thời gian bắt đầu khi bài thi đã kết thúc");
            }
            if ("upcoming".equalsIgnoreCase(currentRuntimeStatus) && dto.getDate().isBefore(now)) {
                throw new IllegalArgumentException("Thời gian bắt đầu của bài thi sắp tới không thể là thời điểm trong quá khứ");
            }
            exam.setExamDate(dto.getDate());
        }
        if (dto.getDurationMinutes() != null && (exam.getDuration() == null || !dto.getDurationMinutes().equals(exam.getDuration()))) {
            if (dto.getDurationMinutes() < 1 || dto.getDurationMinutes() > 1440) {
                throw new IllegalArgumentException("Thời lượng bài thi phải từ 1 đến 1440 phút");
            }
            if ("active".equalsIgnoreCase(currentRuntimeStatus)) {
                throw new IllegalStateException("Không thể sửa đổi thời lượng khi bài thi đang diễn ra");
            }
            if ("completed".equalsIgnoreCase(currentRuntimeStatus)) {
                throw new IllegalStateException("Không thể sửa đổi thời lượng khi bài thi đã kết thúc");
            }
            exam.setDuration(dto.getDurationMinutes());
        }
        if (dto.getProgrammingLanguage() != null && !dto.getProgrammingLanguage().isBlank()) {
            String lang = dto.getProgrammingLanguage().trim();
            if (!lang.equalsIgnoreCase("Java") && !lang.equalsIgnoreCase("C++") && !lang.equalsIgnoreCase("Python") && !lang.equalsIgnoreCase("JavaScript")) {
                throw new IllegalArgumentException("Ngôn ngữ lập trình không hợp lệ. Chỉ chấp nhận: Java, C++, Python, JavaScript");
            }
            exam.setProgrammingLanguage(lang);
        }

        if (dto.getQuestions() != null) {
            saveExamQuestionsAndTestCases(exam, dto.getQuestions());
        }

        if (dto.getStatus() != null) {
            String newStatus = dto.getStatus().trim().toLowerCase();
            if ("published".equals(newStatus)) {
                exam = examRepository.findByIdForUpdate(id).orElse(exam);
                validateExamForPublish(exam);

                if (exam.getExamDate() != null && exam.getExamDate().isAfter(now)) {
                    exam.setStatus("upcoming");
                } else {
                    exam.setStatus("active");
                }
            } else if ("active".equals(newStatus)) {
                if (exam.getExamDate() != null && now.isBefore(exam.getExamDate())) {
                    throw new IllegalStateException("Không thể chuyển trạng thái sang ACTIVE trước thời gian bắt đầu quy định");
                }
                exam.setStatus("active");
            } else if ("completed".equals(newStatus)) {
                LocalDateTime endTime = exam.getExamDate() != null && exam.getDuration() != null
                        ? exam.getExamDate().plusMinutes(exam.getDuration())
                        : null;
                if (endTime != null && now.isBefore(endTime)) {
                    throw new IllegalStateException("Không thể chuyển trạng thái sang COMPLETED trước khi hết thời gian làm bài");
                }
                exam.setStatus("completed");
            } else if ("draft".equals(newStatus)) {
                if ("active".equalsIgnoreCase(currentRuntimeStatus)) {
                    throw new IllegalStateException("Không thể chuyển bài thi đang diễn ra về bản nháp");
                }
                if (submissionRepository != null && submissionRepository.countByExam_Id(id) > 0) {
                    throw new IllegalStateException("Không thể chuyển bài thi về bản nháp khi đã có bài nộp");
                }
                exam.setStatus("draft");
            } else {
                exam.setStatus(newStatus);
            }
        }

        if (dto.getClassIds() != null || dto.getClassId() != null) {
            List<String> targetIds = new ArrayList<>();
            if (dto.getClassIds() != null) {
                targetIds.addAll(dto.getClassIds());
            } else if (dto.getClassId() != null) {
                targetIds.add(dto.getClassId());
            }

            List<String> distinctIds = targetIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (distinctIds.isEmpty()) {
                throw new IllegalArgumentException("Exam must have at least one assigned class");
            }

            // Check if any removed classes have existing submissions
            Set<CourseClass> currentClasses = new HashSet<>();
            if (exam.getClasses() != null) {
                currentClasses.addAll(exam.getClasses());
            }
            if (exam.getCourseClass() != null) {
                currentClasses.add(exam.getCourseClass());
            }
            for (CourseClass currentClass : currentClasses) {
                if (!distinctIds.contains(currentClass.getId())) {
                    if (submissionRepository != null) {
                        long subCount = submissionRepository.countSubmissionsByExamIdAndClassId(exam.getId(), currentClass.getId());
                        if (subCount > 0) {
                            String className = currentClass.getName();
                            if (courseClassRepository != null) {
                                className = courseClassRepository.findById(currentClass.getId()).map(CourseClass::getName).orElse(className);
                            }
                            throw new IllegalStateException(String.format("Không thể xóa lớp học '%s' khỏi bài thi vì sinh viên trong lớp đã có bài nộp (%d bài nộp)", className != null ? className : currentClass.getId(), subCount));
                        }
                    }
                }
            }

            List<CourseClass> classes = courseClassRepository.findAllById(distinctIds);
            if (classes.size() != distinctIds.size()) {
                throw new ResourceNotFoundException("One or more classes not found or access denied");
            }
            for (CourseClass cc : classes) {
                if (!cc.getTeacher().getId().equals(teacherId)) {
                    throw new ResourceNotFoundException("Class not found or access denied: " + cc.getId());
                }
            }

            exam.setCourseClass(classes.get(0));
            exam.setClasses(new HashSet<>(classes));
        }

        return populateCounts(examMapper.toResponse(examRepository.save(exam)));
    }

    private void saveExamQuestionsAndTestCases(Exam exam, List<com.codeassess.exam.dto.request.ExamQuestionDraftDto> questionDtos) {
        if (examQuestionRepository == null || questionDtos == null) {
            return;
        }

        List<ExamQuestion> existingQuestions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(exam.getId());
        Map<String, ExamQuestion> existingMap = existingQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getId, q -> q));

        Set<String> processedQuestionIds = new HashSet<>();
        int qOrder = 0;

        for (com.codeassess.exam.dto.request.ExamQuestionDraftDto qDto : questionDtos) {
            String title = qDto.getTitle() != null && !qDto.getTitle().isBlank() ? qDto.getTitle().trim() : "Câu hỏi #" + (qOrder + 1);
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }
            BigDecimal maxScore = qDto.getMaxScore() != null ? qDto.getMaxScore().setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(10.00);

            ExamQuestion question;
            if (qDto.getId() != null && existingMap.containsKey(qDto.getId())) {
                question = existingMap.get(qDto.getId());
                question.setTitle(title);
                question.setDescription(qDto.getDescription());
                question.setMaxScore(maxScore);
                question.setDisplayOrder(qOrder++);
                if (qDto.getStatus() != null && !qDto.getStatus().isBlank()) {
                    question.setStatus(qDto.getStatus());
                }
            } else {
                question = ExamQuestion.builder()
                        .exam(exam)
                        .title(title)
                        .description(qDto.getDescription())
                        .maxScore(maxScore)
                        .displayOrder(qOrder++)
                        .status(qDto.getStatus() != null && !qDto.getStatus().isBlank() ? qDto.getStatus() : "DRAFT")
                        .build();
            }
            question = examQuestionRepository.save(question);
            processedQuestionIds.add(question.getId());

            if (testCaseRepository != null && qDto.getTestCases() != null) {
                List<TestCase> existingTcs = testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(question.getId());
                Map<String, TestCase> existingTcMap = existingTcs.stream()
                        .collect(Collectors.toMap(TestCase::getId, tc -> tc));
                Set<String> processedTcIds = new HashSet<>();
                int tcOrder = 0;

                for (com.codeassess.exam.dto.request.TestCaseDraftDto tcDto : qDto.getTestCases()) {
                    String tcName = tcDto.getName() != null && !tcDto.getName().isBlank() ? tcDto.getName().trim() : "Test Case #" + (tcOrder + 1);
                    BigDecimal tcPoints = tcDto.getPoints() != null ? tcDto.getPoints().setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(1.00);
                    Boolean isHidden = Boolean.TRUE.equals(tcDto.getIsHidden());

                    TestCase tc;
                    if (tcDto.getId() != null && existingTcMap.containsKey(tcDto.getId())) {
                        tc = existingTcMap.get(tcDto.getId());
                        tc.setName(tcName);
                        tc.setDescription(tcDto.getDescription());
                        tc.setInputData(tcDto.getInputData());
                        tc.setExpectedOutput(tcDto.getExpectedOutput());
                        tc.setPoints(tcPoints);
                        tc.setIsHidden(isHidden);
                        tc.setDisplayOrder(tcOrder++);
                    } else {
                        tc = TestCase.builder()
                                .exam(exam)
                                .question(question)
                                .name(tcName)
                                .description(tcDto.getDescription())
                                .inputData(tcDto.getInputData())
                                .expectedOutput(tcDto.getExpectedOutput())
                                .points(tcPoints)
                                .isHidden(isHidden)
                                .displayOrder(tcOrder++)
                                .build();
                    }
                    tc = testCaseRepository.save(tc);
                    processedTcIds.add(tc.getId());
                }

                for (TestCase oldTc : existingTcs) {
                    if (!processedTcIds.contains(oldTc.getId())) {
                        testCaseRepository.delete(oldTc);
                    }
                }
            }
        }

        for (ExamQuestion oldQ : existingQuestions) {
            if (!processedQuestionIds.contains(oldQ.getId())) {
                if (testCaseRepository != null) {
                    List<TestCase> oldTcs = testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(oldQ.getId());
                    testCaseRepository.deleteAll(oldTcs);
                }
                examQuestionRepository.delete(oldQ);
            }
        }
    }

    private void validateExamForPublish(Exam exam) {
        if (exam.getExamDate() == null) {
            throw new IllegalStateException("Thời gian thi chưa được thiết lập trước khi xuất bản");
        }
        if (exam.getDuration() == null || exam.getDuration() < 1) {
            throw new IllegalStateException("Thời lượng bài thi không hợp lệ trước khi xuất bản");
        }

        if (examQuestionRepository != null) {
            List<com.codeassess.exam.entity.ExamQuestion> questions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(exam.getId());
            if (questions.isEmpty()) {
                throw new IllegalStateException("Không thể xuất bản bài thi chưa có câu hỏi nào");
            }

            // Lock questions in deterministic ID order to prevent concurrent test case / question edits and deadlocks
            List<com.codeassess.exam.entity.ExamQuestion> sortedQuestions = new java.util.ArrayList<>(questions);
            sortedQuestions.sort(java.util.Comparator.comparing(com.codeassess.exam.entity.ExamQuestion::getId));
            for (com.codeassess.exam.entity.ExamQuestion sq : sortedQuestions) {
                examQuestionRepository.findByIdAndExam_IdForUpdate(sq.getId(), exam.getId());
            }

            BigDecimal totalScore = questions.stream()
                    .map(com.codeassess.exam.entity.ExamQuestion::getMaxScore)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            if (totalScore.compareTo(BigDecimal.valueOf(10.00).setScale(2, java.math.RoundingMode.HALF_UP)) != 0) {
                throw new IllegalStateException(String.format("Tổng điểm các câu hỏi phải bằng chính xác 10.00 điểm trước khi xuất bản (Hiện tại: %s)", totalScore));
            }

            for (com.codeassess.exam.entity.ExamQuestion q : questions) {
                if (q.getTitle() == null || q.getTitle().trim().isEmpty()) {
                    throw new IllegalStateException(String.format("Câu hỏi #%d chưa có tiêu đề", (q.getDisplayOrder() != null ? q.getDisplayOrder() : 0) + 1));
                }
                if (q.getTitle().trim().length() > 255) {
                    throw new IllegalStateException(String.format("Tiêu đề câu hỏi #%d vượt quá giới hạn 255 ký tự", (q.getDisplayOrder() != null ? q.getDisplayOrder() : 0) + 1));
                }
                if (q.getDescription() == null || q.getDescription().trim().isEmpty()) {
                    throw new IllegalStateException(String.format("Câu hỏi \"%s\" chưa có nội dung đề bài", q.getTitle()));
                }
                if (q.getMaxScore() == null || q.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(String.format("Câu hỏi \"%s\" phải có điểm tối đa lớn hơn 0", q.getTitle()));
                }
                if (q.getMaxScore().scale() > 2) {
                    throw new IllegalStateException(String.format("Câu hỏi \"%s\" có điểm tối đa vượt quá 2 chữ số thập phân (DECIMAL(5,2))", q.getTitle()));
                }

                if (testCaseRepository != null) {
                    List<com.codeassess.testcase.entity.TestCase> testCases = testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(q.getId());
                    if (testCases.isEmpty()) {
                        throw new IllegalStateException(String.format("Câu hỏi \"%s\" cần tối thiểu 1 test case", q.getTitle()));
                    }

                    boolean hasPublic = testCases.stream().anyMatch(tc -> tc.getIsHidden() == null || !tc.getIsHidden());
                    if (!hasPublic) {
                        throw new IllegalStateException(String.format("Câu hỏi \"%s\" cần tối thiểu 1 test case công khai (Public/Sample)", q.getTitle()));
                    }

                    BigDecimal qTcSum = BigDecimal.ZERO;
                    for (com.codeassess.testcase.entity.TestCase tc : testCases) {
                        if (tc.getName() == null || tc.getName().trim().isEmpty()) {
                            throw new IllegalStateException(String.format("Câu hỏi \"%s\": Có test case chưa được đặt tên", q.getTitle()));
                        }
                        if (tc.getInputData() != null && !tc.getInputData().isEmpty() && tc.getInputData().trim().isEmpty()) {
                            throw new IllegalStateException(String.format("Câu hỏi \"%s\": Test case \"%s\" có dữ liệu đầu vào chỉ chứa khoảng trắng", q.getTitle(), tc.getName()));
                        }
                        if (tc.getExpectedOutput() == null || tc.getExpectedOutput().trim().isEmpty()) {
                            throw new IllegalStateException(String.format("Câu hỏi \"%s\": Test case \"%s\" chưa có kết quả mong đợi (Expected Output)", q.getTitle(), tc.getName()));
                        }
                        if (tc.getPoints() == null || tc.getPoints().compareTo(BigDecimal.ZERO) <= 0) {
                            throw new IllegalStateException(String.format("Câu hỏi \"%s\": Test case \"%s\" phải có điểm lớn hơn 0", q.getTitle(), tc.getName()));
                        }
                        if (tc.getPoints().scale() > 2) {
                            throw new IllegalStateException(String.format("Câu hỏi \"%s\": Test case \"%s\" có điểm vượt quá 2 chữ số thập phân", q.getTitle(), tc.getName()));
                        }
                        if (tc.getPoints().compareTo(q.getMaxScore()) > 0) {
                            throw new IllegalStateException(String.format("Câu hỏi \"%s\": Test case \"%s\" có điểm (%s) vượt quá điểm câu hỏi (%s)", q.getTitle(), tc.getName(), tc.getPoints(), q.getMaxScore()));
                        }
                        qTcSum = qTcSum.add(tc.getPoints()).setScale(2, java.math.RoundingMode.HALF_UP);
                    }
                    if (qTcSum.compareTo(q.getMaxScore()) > 0) {
                        throw new IllegalStateException(String.format("Câu hỏi \"%s\": Tổng điểm các test case (%s) vượt quá điểm câu hỏi (%s)", q.getTitle(), qTcSum, q.getMaxScore()));
                    }
                }
            }
        }
    }

    @Transactional
    public ExamResponse publishExam(String id, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));

        // Acquire pessimistic write lock on the Exam to serialize publish against concurrent mutations
        exam = examRepository.findByIdForUpdate(id).orElse(exam);

        validateExamForPublish(exam);

        LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
        if (exam.getExamDate() != null && exam.getExamDate().isAfter(now)) {
            exam.setStatus("upcoming");
        } else {
            exam.setStatus("active");
        }

        return populateCounts(examMapper.toResponse(examRepository.save(exam)));
    }

    @Transactional
    public void deleteTeacherExam(String id, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));

        // Case C: Check if exam has active grading jobs
        long activeGradingJobs = submissionRepository.countActiveGradingSubmissionsByExamId(id);
        if (activeGradingJobs > 0) {
            throw new IllegalStateException("Cannot delete exam while submissions are actively being graded. Please wait for grading to complete.");
        }

        // Case B: If exam has submissions, perform soft delete to protect historical grades and audit trail
        long submissionCount = submissionRepository.countByExam_Id(id);
        if (submissionCount > 0) {
            exam.setDeletedAt(java.time.LocalDateTime.now());
            exam.setStatus("archived");
            examRepository.save(exam);
            return;
        }

        // Case A: No submissions, safe to delete associations and entity
        exam.getClasses().clear();
        examRepository.delete(exam);
    }

    private void populateQuestionsAndTestCases(ExamResponse response, Exam exam) {
        if (response == null || exam == null) return;
        List<ExamQuestion> qList = (examQuestionRepository != null)
                ? examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(exam.getId())
                : Collections.emptyList();

        if (qList.isEmpty()) {
            List<TestCase> tcs = (testCaseRepository != null)
                    ? testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(exam.getId())
                    : Collections.emptyList();
            ExamQuestionResponse defaultQ = ExamQuestionResponse.builder()
                    .id(exam.getId())
                    .examId(exam.getId())
                    .title(exam.getTitle())
                    .description(exam.getProblemStatement())
                    .maxScore(BigDecimal.valueOf(10.00))
                    .displayOrder(0)
                    .status(exam.getStatus() != null ? exam.getStatus().toUpperCase() : "PUBLISHED")
                    .createdAt(exam.getCreatedAt())
                    .testCases(mapTestCases(tcs, true))
                    .build();
            response.setQuestions(List.of(defaultQ));
            response.setTotalQuestions(1);
            response.setTotalAllocatedPoints(BigDecimal.valueOf(10.00));
            return;
        }

        List<ExamQuestionResponse> qDtos = new ArrayList<>();
        BigDecimal sumAllocated = BigDecimal.ZERO;
        for (ExamQuestion eq : qList) {
            List<TestCase> tcs = (testCaseRepository != null)
                    ? testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(eq.getId())
                    : Collections.emptyList();
            ExamQuestionResponse qDto = ExamQuestionResponse.builder()
                    .id(eq.getId())
                    .examId(exam.getId())
                    .title(eq.getTitle())
                    .description(eq.getDescription())
                    .maxScore(eq.getMaxScore() != null ? eq.getMaxScore() : BigDecimal.valueOf(10.00))
                    .displayOrder(eq.getDisplayOrder())
                    .status(eq.getStatus())
                    .createdAt(eq.getCreatedAt())
                    .testCases(mapTestCases(tcs, true))
                    .build();
            qDtos.add(qDto);
            if (eq.getMaxScore() != null) {
                sumAllocated = sumAllocated.add(eq.getMaxScore());
            }
        }
        response.setQuestions(qDtos);
        response.setTotalQuestions(qDtos.size());
        response.setTotalAllocatedPoints(sumAllocated.setScale(2, RoundingMode.HALF_UP));
    }

    private List<TestCaseResponse> mapTestCases(List<TestCase> tcs, boolean isTeacher) {
        if (tcs == null || tcs.isEmpty()) return Collections.emptyList();
        return tcs.stream().map(tc -> {
            boolean isHidden = Boolean.TRUE.equals(tc.getIsHidden());
            return TestCaseResponse.builder()
                    .id(tc.getId())
                    .examId(tc.getExam() != null ? tc.getExam().getId() : null)
                    .questionId(tc.getQuestion() != null ? tc.getQuestion().getId() : null)
                    .name(tc.getName())
                    .description(tc.getDescription())
                    .inputData(isTeacher || !isHidden ? tc.getInputData() : null)
                    .expectedOutput(isTeacher || !isHidden ? tc.getExpectedOutput() : null)
                    .points(tc.getPoints())
                    .isHidden(isHidden)
                    .displayOrder(tc.getDisplayOrder())
                    .createdAt(tc.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
