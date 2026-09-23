package com.codeassess;

import com.codeassess.dashboard.service.StudentDashboardService;
import com.codeassess.dashboard.dto.StudentDashboardDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DbPreflightDashboard {

    @Autowired
    private StudentDashboardService studentDashboardService;

    @Test
    public void testStudentDashboard() {
        System.out.println("====== STARTING DASHBOARD TEST ======");
        try {
            String studentId = "45abd7a5-5498-4492-8154-73824b25d8fd";
            StudentDashboardDto dto = studentDashboardService.getDashboard(studentId);
            System.out.println("====== DASHBOARD SUCCESS ======");
            System.out.println("Name: " + dto.getFullName());
            System.out.println("Recent Submissions: " + dto.getRecentSubmissions().size());
            System.out.println("Upcoming: " + dto.getUpcomingDeadlines().size());
        } catch (Exception e) {
            System.out.println("====== DASHBOARD FAILED ======");
            e.printStackTrace();
        }
    }
}
