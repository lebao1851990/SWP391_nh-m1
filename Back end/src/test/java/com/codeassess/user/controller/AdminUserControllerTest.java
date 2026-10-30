package com.codeassess.user.controller;

import com.codeassess.common.exception.GlobalExceptionHandler;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.security.SecurityConfig;
import com.codeassess.user.dto.request.UpdateUserDto;
import com.codeassess.user.dto.response.UserResponse;
import com.codeassess.user.entity.Role;
import com.codeassess.user.repository.UserRepository;
import com.codeassess.user.service.AdminUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // --- Anonymous access (401) ---

    @Test
    void unauthenticatedCannotAccessGetAllUsersReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unauthenticatedCannotAccessGetUserByIdReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unauthenticatedCannotUpdateUserReturns401() throws Exception {
        UpdateUserDto dto = UpdateUserDto.builder()
                .name("Updated Name")
                .role("STUDENT")
                .build();

        mockMvc.perform(put("/api/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- Role-based access: TEACHER forbidden (403) ---

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessGetAllUsersReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessGetUserByIdReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotUpdateUserReturns403() throws Exception {
        UpdateUserDto dto = UpdateUserDto.builder()
                .name("Updated Name")
                .role("STUDENT")
                .build();

        mockMvc.perform(put("/api/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- Role-based access: STUDENT forbidden (403) ---

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessGetAllUsersReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessGetUserByIdReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotUpdateUserReturns403() throws Exception {
        UpdateUserDto dto = UpdateUserDto.builder()
                .name("Updated Name")
                .role("STUDENT")
                .build();

        mockMvc.perform(put("/api/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- Role-based access: ADMIN allowed (200) & business error handling ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessGetAllUsersReturns200AndList() throws Exception {
        UserResponse mockUser = UserResponse.builder()
                .id("1")
                .email("test@test.com")
                .fullName("Test User")
                .role(Role.STUDENT)
                .build();

        when(adminUserService.getAllUsers()).thenReturn(List.of(mockUser));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].email").value("test@test.com"))
                .andExpect(jsonPath("$[0].fullName").value("Test User"))
                .andExpect(jsonPath("$[0].role").value("STUDENT"));

        verify(adminUserService).getAllUsers();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessGetUserByIdReturns200AndUser() throws Exception {
        UserResponse mockUser = UserResponse.builder()
                .id("1")
                .email("test@test.com")
                .fullName("Test User")
                .role(Role.STUDENT)
                .build();

        when(adminUserService.getUserById("1")).thenReturn(mockUser);

        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(adminUserService).getUserById("1");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetUserByIdNotFoundReturns404() throws Exception {
        when(adminUserService.getUserById("999"))
                .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(get("/api/admin/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: 999"));

        verify(adminUserService).getUserById("999");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateUserWithValidDataReturns200() throws Exception {
        UpdateUserDto dto = UpdateUserDto.builder()
                .name("Updated Teacher")
                .role("TEACHER")
                .build();

        UserResponse updatedResponse = UserResponse.builder()
                .id("1")
                .email("test@test.com")
                .fullName("Updated Teacher")
                .role(Role.TEACHER)
                .build();

        when(adminUserService.updateUser(eq("1"), any(UpdateUserDto.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.fullName").value("Updated Teacher"))
                .andExpect(jsonPath("$.role").value("TEACHER"));

        verify(adminUserService).updateUser(eq("1"), any(UpdateUserDto.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdateUserNotFoundReturns404() throws Exception {
        UpdateUserDto dto = UpdateUserDto.builder()
                .name("Valid Name")
                .role("STUDENT")
                .build();

        when(adminUserService.updateUser(eq("999"), any(UpdateUserDto.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(put("/api/admin/users/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(adminUserService).updateUser(eq("999"), any(UpdateUserDto.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdateUserWithInvalidValidationPayloadReturns400() throws Exception {
        UpdateUserDto invalidDto = UpdateUserDto.builder()
                .name("") // blank name violation
                .role("INVALID_ROLE") // regex pattern violation
                .build();

        mockMvc.perform(put("/api/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
