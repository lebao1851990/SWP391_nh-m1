package com.codeassess.classes.controller;

import com.codeassess.classes.dto.request.EnrollStudentDto;
import com.codeassess.classes.dto.response.ClassStudentResponse;
import com.codeassess.classes.dto.response.ExcelImportResultDto;
import com.codeassess.classes.dto.response.ExcelRowErrorDto;
import com.codeassess.classes.service.AdminClassService;
import com.codeassess.classes.service.ExcelRosterImportService;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ExcelBatchImportException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminClassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminClassService adminClassService;

    @MockBean
    private ExcelRosterImportService excelRosterImportService;

    // --- 1. Authentication Checks (401) ---

    @Test
    @DisplayName("Unauthenticated requests to admin roster endpoints return 401")
    void unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/admin/classes/c-1/students"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"s-1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/classes/c-1/students/s-1"))
                .andExpect(status().isUnauthorized());

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes());
        mockMvc.perform(multipart("/api/admin/classes/c-1/students/import-excel").file(file))
                .andExpect(status().isUnauthorized());
    }

    // --- 2. Authorization Checks (403) ---

    @Test
    @DisplayName("Teacher role cannot access admin roster endpoints (403)")
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessRosterEndpoints_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/classes/c-1/students"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"s-1\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/classes/c-1/students/s-1"))
                .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes());
        mockMvc.perform(multipart("/api/admin/classes/c-1/students/import-excel").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student role cannot access admin roster endpoints (403)")
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessRosterEndpoints_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/classes/c-1/students"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"s-1\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/classes/c-1/students/s-1"))
                .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes());
        mockMvc.perform(multipart("/api/admin/classes/c-1/students/import-excel").file(file))
                .andExpect(status().isForbidden());
    }

    // --- 3. GET /api/admin/classes/{id}/students ---

    @Test
    @DisplayName("Admin get roster returns 200 with list of enrolled students")
    @WithMockUser(roles = "ADMIN")
    void admin_GetClassStudents_Returns200() throws Exception {
        ClassStudentResponse studentDto = ClassStudentResponse.builder()
                .id("s-1")
                .studentId("s-1")
                .email("student@test.com")
                .fullName("Test Student")
                .build();

        Mockito.when(adminClassService.getClassStudents("c-1")).thenReturn(List.of(studentDto));

        mockMvc.perform(get("/api/admin/classes/c-1/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s-1"))
                .andExpect(jsonPath("$[0].studentId").value("s-1"))
                .andExpect(jsonPath("$[0].email").value("student@test.com"))
                .andExpect(jsonPath("$[0].fullName").value("Test Student"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Admin get roster for non-existent class returns 404")
    @WithMockUser(roles = "ADMIN")
    void admin_GetClassStudents_ClassNotFound_Returns404() throws Exception {
        Mockito.when(adminClassService.getClassStudents("c-999"))
                .thenThrow(new ResourceNotFoundException("Class not found with id: c-999"));

        mockMvc.perform(get("/api/admin/classes/c-999/students"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- 4. POST /api/admin/classes/{id}/students ---

    @Test
    @DisplayName("Admin enrolls student successfully, returns 201 Created")
    @WithMockUser(roles = "ADMIN")
    void admin_EnrollStudent_Returns201() throws Exception {
        EnrollStudentDto reqDto = EnrollStudentDto.builder().studentId("s-1").build();
        ClassStudentResponse resDto = ClassStudentResponse.builder()
                .id("s-1")
                .studentId("s-1")
                .email("student@test.com")
                .fullName("Test Student")
                .build();

        Mockito.when(adminClassService.enrollStudent(eq("c-1"), any(EnrollStudentDto.class)))
                .thenReturn(resDto);

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value("s-1"))
                .andExpect(jsonPath("$.email").value("student@test.com"));
    }

    @Test
    @DisplayName("Admin enrolls duplicate student returns 409 Conflict")
    @WithMockUser(roles = "ADMIN")
    void admin_EnrollStudent_Duplicate_Returns409() throws Exception {
        EnrollStudentDto reqDto = EnrollStudentDto.builder().studentId("s-1").build();

        Mockito.when(adminClassService.enrollStudent(eq("c-1"), any(EnrollStudentDto.class)))
                .thenThrow(new ConflictException("Student is already enrolled in this class"));

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Student is already enrolled in this class"));
    }

    @Test
    @DisplayName("Admin enrolls non-student user returns 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    void admin_EnrollStudent_NotStudentRole_Returns400() throws Exception {
        EnrollStudentDto reqDto = EnrollStudentDto.builder().studentId("t-1").build();

        Mockito.when(adminClassService.enrollStudent(eq("c-1"), any(EnrollStudentDto.class)))
                .thenThrow(new IllegalArgumentException("User is not a student"));

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("User is not a student"));
    }

    @Test
    @DisplayName("Admin enrolls with student or class not found returns 404")
    @WithMockUser(roles = "ADMIN")
    void admin_EnrollStudent_NotFound_Returns404() throws Exception {
        EnrollStudentDto reqDto = EnrollStudentDto.builder().studentId("ghost").build();

        Mockito.when(adminClassService.enrollStudent(eq("c-1"), any(EnrollStudentDto.class)))
                .thenThrow(new ResourceNotFoundException("Student not found"));

        mockMvc.perform(post("/api/admin/classes/c-1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- 5. DELETE /api/admin/classes/{id}/students/{studentId} ---

    @Test
    @DisplayName("Admin unenrolls student returns 204 No Content")
    @WithMockUser(roles = "ADMIN")
    void admin_UnenrollStudent_Returns204() throws Exception {
        Mockito.doNothing().when(adminClassService).unenrollStudent("c-1", "s-1");

        mockMvc.perform(delete("/api/admin/classes/c-1/students/s-1"))
                .andExpect(status().isNoContent());

        Mockito.verify(adminClassService).unenrollStudent("c-1", "s-1");
    }

    @Test
    @DisplayName("Admin unenrolls student not in class returns 404")
    @WithMockUser(roles = "ADMIN")
    void admin_UnenrollStudent_NotFound_Returns404() throws Exception {
        Mockito.doThrow(new ResourceNotFoundException("Student enrollment not found in this class"))
                .when(adminClassService).unenrollStudent("c-1", "s-999");

        mockMvc.perform(delete("/api/admin/classes/c-1/students/s-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- 6. POST /api/admin/classes/{id}/students/import-excel ---

    @Test
    @DisplayName("Admin imports students from Excel successfully returns 200")
    @WithMockUser(roles = "ADMIN")
    void admin_ImportStudentsFromExcel_Success_Returns200() throws Exception {
        ExcelImportResultDto resultDto = ExcelImportResultDto.builder()
                .totalRows(2)
                .importedCount(2)
                .failedCount(0)
                .isStrict(false)
                .errors(List.of())
                .build();

        Mockito.when(excelRosterImportService.importStudentsFromExcel(eq("c-1"), any(), eq(false)))
                .thenReturn(resultDto);

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes());

        mockMvc.perform(multipart("/api/admin/classes/c-1/students/import-excel")
                        .file(file)
                        .param("strict", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andExpect(jsonPath("$.strict").value(false));
    }

    @Test
    @DisplayName("Admin imports students in strict mode with validation failure returns 400 with errors")
    @WithMockUser(roles = "ADMIN")
    void admin_ImportStudentsFromExcel_StrictFailure_Returns400() throws Exception {
        ExcelImportResultDto failedResult = ExcelImportResultDto.builder()
                .totalRows(3)
                .importedCount(0)
                .failedCount(1)
                .isStrict(true)
                .errors(List.of(ExcelRowErrorDto.builder()
                        .rowNumber(2)
                        .identifier("std-bad")
                        .errorCode("INVALID_EMAIL")
                        .message("Invalid email format")
                        .build()))
                .build();

        Mockito.when(excelRosterImportService.importStudentsFromExcel(eq("c-1"), any(), eq(true)))
                .thenThrow(new ExcelBatchImportException(
                        "Strict import aborted: 1 validation error(s) found. No rows were saved.",
                        failedResult));

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes());

        mockMvc.perform(multipart("/api/admin/classes/c-1/students/import-excel")
                        .file(file)
                        .param("strict", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.errors[0].errorCode").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(2));
    }

    @Test
    @DisplayName("Admin imports students with non-existent class returns 404")
    @WithMockUser(roles = "ADMIN")
    void admin_ImportStudentsFromExcel_ClassNotFound_Returns404() throws Exception {
        Mockito.when(excelRosterImportService.importStudentsFromExcel(eq("c-999"), any(), eq(false)))
                .thenThrow(new ResourceNotFoundException("Class not found with id: c-999"));

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes());

        mockMvc.perform(multipart("/api/admin/classes/c-999/students/import-excel")
                        .file(file))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
