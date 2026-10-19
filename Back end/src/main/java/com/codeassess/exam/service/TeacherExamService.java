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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final ClassStudentRepository classStudentRepository;
    private final CourseClassRepository courseClassRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

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
        return populateCounts(examMapper.toResponse(exam));
    }

    @Transactional(readOnly = true)
    public List<com.codeassess.submission.dto.response.TeacherSubmissionListResponse> getExamSubmissions(String examId, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(examId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));
        
        String courseCode = exam.getClasses() != null && !exam.getClasses().isEmpty()
                ? exam.getClasses().stream().map(CourseClass::getCode).sorted().collect(Collectors.joining(", "))
                : (exam.getCourseClass() != null ? exam.getCourseClass().getCode() : "");

        return submissionRepository.findByExam_Id(examId).stream()
                .map(sub -> com.codeassess.submission.dto.response.TeacherSubmissionListResponse.builder()
                        .id(sub.getId())
                        .studentName(sub.getStudent().getFullName())
                        .studentEmail(sub.getStudent().getEmail())
                        .examTitle(exam.getTitle())
                        .courseCode(courseCode)
                        .status(sub.getStatus())
                        .totalScore(sub.getTotalScore())
                        .submittedAt(("COMPLETED".equalsIgnoreCase(sub.getStatus()) || "EVALUATED".equalsIgnoreCase(sub.getStatus())) && sub.getEvaluatedAt() != null
                                ? sub.getEvaluatedAt()
                                : sub.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public ExamResponse createExam(CreateExamDto dto, String teacherId) {
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
                .title(dto.getTitle())
                .courseClass(classes.get(0))
                .classes(new HashSet<>(classes))
                .examDate(dto.getDate())
                .duration(dto.getDurationMinutes())
                .status("active")
                .build();

        return populateCounts(examMapper.toResponse(examRepository.save(newExam)));
    }

    @Transactional
    public ExamResponse updateExam(String id, UpdateExamDto dto, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));

        if (dto.getTitle() != null) exam.setTitle(dto.getTitle());
        if (dto.getDate() != null) exam.setExamDate(dto.getDate());
        if (dto.getDurationMinutes() != null) exam.setDuration(dto.getDurationMinutes());
        if (dto.getStatus() != null) exam.setStatus(dto.getStatus().toLowerCase());

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
}
