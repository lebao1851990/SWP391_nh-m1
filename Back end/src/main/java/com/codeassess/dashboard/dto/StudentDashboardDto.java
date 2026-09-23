package com.codeassess.dashboard.dto;

import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StudentDashboardDto {
    private String fullName;
    private String email;
    private int activeExamsCount;
    private int submittedCount;
    private int gradedCount;
    private int pendingAppealsCount;
    private List<ExamResponse> upcomingDeadlines;
    private List<SubmissionDetailResponse> recentSubmissions;
}
