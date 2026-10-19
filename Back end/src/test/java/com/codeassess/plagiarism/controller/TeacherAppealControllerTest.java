package com.codeassess.plagiarism.controller;

import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.service.TeacherAppealService;
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

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherAppealController.class)
@org.springframework.context.annotation.Import(com.codeassess.security.SecurityConfig.class)
public class TeacherAppealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherAppealService teacherAppealService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.codeassess.security.CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User teacher;

    @BeforeEach
    public void setup() {
        teacher = User.builder().id("teacher-1").email("teachera@fpt.edu.vn").role(com.codeassess.user.entity.Role.TEACHER).build();
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void getTeacherAppeals_Success() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacher));
        AppealResponseDto dto = AppealResponseDto.builder()
                .id("apl_100")
                .status("PENDING")
                .build();
        when(teacherAppealService.getTeacherAppeals(any())).thenReturn(Collections.singletonList(dto));

        mockMvc.perform(get("/api/teacher/appeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("apl_100"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void getTeacherAppeal_Success() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacher));
        AppealResponseDto dto = AppealResponseDto.builder()
                .id("apl_100")
                .status("PENDING")
                .build();
        when(teacherAppealService.getTeacherAppeal(eq("apl_100"), any())).thenReturn(dto);

        mockMvc.perform(get("/api/teacher/appeals/apl_100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("apl_100"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void teacherCannotResolveAppeal_ReturnsForbidden() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacher));
        AppealResolveRequestDto request = AppealResolveRequestDto.builder()
                .decision("APPROVED")
                .reviewerNotes("Accepting valid explanation")
                .build();

        // Teachers are strictly forbidden from resolving appeals
        mockMvc.perform(put("/api/teacher/appeals/apl_100/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void studentCannotCallTeacherAppealApi() throws Exception {
        mockMvc.perform(get("/api/teacher/appeals"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void unauthenticatedCannotCallTeacherAppealApi() throws Exception {
        mockMvc.perform(get("/api/teacher/appeals"))
                .andExpect(status().isUnauthorized());
    }
}
