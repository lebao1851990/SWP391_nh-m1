package com.codeassess.plagiarism.controller;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.service.AdminAppealService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAppealController.class)
@org.springframework.context.annotation.Import(com.codeassess.security.SecurityConfig.class)
public class AdminAppealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAppealService adminAppealService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.codeassess.security.CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User admin;

    @BeforeEach
    public void setup() {
        admin = User.builder().id("admin-1").email("admin@codeassess.edu").role(Role.ADMIN).build();
    }

    @Test
    @WithMockUser(username = "admin@codeassess.edu", roles = "ADMIN")
    public void getAdminAppeals_Success() throws Exception {
        when(userRepository.findByEmail("admin@codeassess.edu")).thenReturn(Optional.of(admin));
        AppealResponseDto dto = AppealResponseDto.builder()
                .id("apl_100")
                .status("PENDING")
                .build();
        when(adminAppealService.getAdminAppeals(any())).thenReturn(Collections.singletonList(dto));

        mockMvc.perform(get("/api/admin/appeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("apl_100"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "admin@codeassess.edu", roles = "ADMIN")
    public void getAdminAppeal_Success() throws Exception {
        when(userRepository.findByEmail("admin@codeassess.edu")).thenReturn(Optional.of(admin));
        AppealResponseDto dto = AppealResponseDto.builder()
                .id("apl_100")
                .status("PENDING")
                .build();
        when(adminAppealService.getAdminAppeal(eq("apl_100"))).thenReturn(dto);

        mockMvc.perform(get("/api/admin/appeals/apl_100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("apl_100"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "admin@codeassess.edu", roles = "ADMIN")
    public void resolveAppeal_Approved_Success() throws Exception {
        when(userRepository.findByEmail("admin@codeassess.edu")).thenReturn(Optional.of(admin));
        AppealResolveRequestDto request = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Accepting valid explanation")
                .adjustedScore(BigDecimal.valueOf(90.0))
                .build();

        AppealResponseDto response = AppealResponseDto.builder()
                .id("apl_100")
                .status("APPROVED")
                .reviewerNotes("Accepting valid explanation")
                .updatedScore(BigDecimal.valueOf(90.0))
                .build();

        when(adminAppealService.resolveAppeal(eq("apl_100"), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/appeals/apl_100/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.updatedScore").value(90.0));
    }

    @Test
    @WithMockUser(username = "admin@codeassess.edu", roles = "ADMIN")
    public void resolveAppeal_Rejected_Success() throws Exception {
        when(userRepository.findByEmail("admin@codeassess.edu")).thenReturn(Optional.of(admin));
        AppealResolveRequestDto request = AppealResolveRequestDto.builder()
                .decision("REJECTED")
                .reviewerNotes("Explanation is not substantiated")
                .build();

        AppealResponseDto response = AppealResponseDto.builder()
                .id("apl_100")
                .status("REJECTED")
                .reviewerNotes("Explanation is not substantiated")
                .build();

        when(adminAppealService.resolveAppeal(eq("apl_100"), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/appeals/apl_100/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(username = "admin@codeassess.edu", roles = "ADMIN")
    public void resolveAppeal_AlreadyResolved_ReturnsConflict() throws Exception {
        when(userRepository.findByEmail("admin@codeassess.edu")).thenReturn(Optional.of(admin));
        AppealResolveRequestDto request = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Resolved properly")
                .build();

        when(adminAppealService.resolveAppeal(eq("apl_100"), any(), any()))
                .thenThrow(new ConflictException("Đơn khiếu nại này đã được giải quyết trước đó và không thể thay đổi."));

        mockMvc.perform(put("/api/admin/appeals/apl_100/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "student@codeassess.edu", roles = "STUDENT")
    public void studentCannotCallAdminAppealApi() throws Exception {
        mockMvc.perform(get("/api/admin/appeals"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "teacher@codeassess.edu", roles = "TEACHER")
    public void teacherCannotCallAdminAppealApi() throws Exception {
        mockMvc.perform(get("/api/admin/appeals"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void unauthenticatedCannotCallAdminAppealApi() throws Exception {
        mockMvc.perform(get("/api/admin/appeals"))
                .andExpect(status().isUnauthorized());
    }
}
