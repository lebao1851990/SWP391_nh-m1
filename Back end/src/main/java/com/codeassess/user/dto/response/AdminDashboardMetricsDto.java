package com.codeassess.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardMetricsDto {
    private long totalUsers;
    private long totalClasses;
    private long totalExams;
    private long activeScans;
}
