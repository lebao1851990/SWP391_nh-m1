package com.codeassess.admin.controller;

import com.codeassess.admin.dto.response.AdminDashboardMetricsDto;
import com.codeassess.admin.service.AdminMetricsService;
import com.codeassess.common.exception.GlobalExceptionHandler;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.security.SecurityConfig;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMetricsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
public class AdminMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMetricsService adminMetricsService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void anonymousCannotAccessAdminMetricsReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessAdminMetricsReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessAdminMetricsReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessMetricsReturns200AndData() throws Exception {
        AdminDashboardMetricsDto metricsDto = AdminDashboardMetricsDto.builder()
                .totalUsers(12L)
                .totalClasses(3L)
                .totalExams(7L)
                .activeScans(0L)
                .build();

        when(adminMetricsService.getMetrics()).thenReturn(metricsDto);

        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(12))
                .andExpect(jsonPath("$.totalClasses").value(3))
                .andExpect(jsonPath("$.totalExams").value(7))
                .andExpect(jsonPath("$.activeScans").value(0));

        verify(adminMetricsService).getMetrics();
    }
}
