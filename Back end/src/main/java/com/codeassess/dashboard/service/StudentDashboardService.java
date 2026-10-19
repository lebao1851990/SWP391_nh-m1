package com.codeassess.dashboard.service;

import com.codeassess.dashboard.dto.StudentDashboardDto;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.StudentExamService;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudentDashboardService {

    private final UserRepository userRepository;
    private final StudentExamService studentExamService;
    private final SubmissionRepository submissionRepository;

    public StudentDashboardService(
            UserRepository userRepository,
            StudentExamService studentExamService,
            SubmissionRepository submissionRepository) {
        this.userRepository = userRepository;
        this.studentExamService = studentExamService;
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public StudentDashboardDto getDashboard(String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        List<ExamResponse> allExams = studentExamService.getStudentExams(studentId);
        List<ExamResponse> activeExams = allExams.stream()
                .filter(e -> "PUBLISHED".equals(e.getStatus()) || "ACTIVE".equals(e.getStatus()))
                .collect(Collectors.toList());

        List<Submission> allSubmissions = submissionRepository.findByStudent_Id(studentId);
        int submittedCount = allSubmissions.size();
        int gradedCount = (int) allSubmissions.stream().filter(s -> "GRADED".equals(s.getStatus())).count();

        // For pending appeals, we might need appeal repo, but we can set it to 0 for now
        int pendingAppealsCount = 0;

        List<ExamResponse> upcomingDeadlines = activeExams.stream()
                .limit(3) // mock sort
                .collect(Collectors.toList());

        List<SubmissionDetailResponse> recentSubmissions = allSubmissions.stream()
                .map(s -> SubmissionDetailResponse.builder()
                        .id(s.getId())
                        .examId(s.getExam().getId())
                        .status(s.getStatus())
                        .totalScore(s.getTotalScore())
                        .build())
                .limit(5) // mock sort
                .collect(Collectors.toList());

        return StudentDashboardDto.builder()
                .fullName(student.getFullName())
                .email(student.getEmail())
                .activeExamsCount(activeExams.size())
                .submittedCount(submittedCount)
                .gradedCount(gradedCount)
                .pendingAppealsCount(pendingAppealsCount)
                .upcomingDeadlines(upcomingDeadlines)
                .recentSubmissions(recentSubmissions)
                .build();
    }
}
