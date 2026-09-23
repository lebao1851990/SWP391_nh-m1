package com.codeassess.admin.service;

import com.codeassess.admin.dto.response.AdminDashboardMetricsDto;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final UserRepository userRepository;
    private final CourseClassRepository courseClassRepository;
    private final ExamRepository examRepository;

    @Transactional(readOnly = true)
    public AdminDashboardMetricsDto getMetrics() {
        long totalUsers = userRepository.count();
        long totalClasses = courseClassRepository.count();
        long totalExams = examRepository.count();
        
        // activeScans currently reports 0 because the current synchronous AST scanning architecture 
        // does not persist active/running scan state. It is not a count of completed submissions 
        // or similarity pairs.
        long activeScans = 0;

        return AdminDashboardMetricsDto.builder()
                .totalUsers(totalUsers)
                .totalClasses(totalClasses)
                .totalExams(totalExams)
                .activeScans(activeScans)
                .build();
    }
}
