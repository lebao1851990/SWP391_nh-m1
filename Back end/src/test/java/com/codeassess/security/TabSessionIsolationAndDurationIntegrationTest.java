package com.codeassess.security;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.TeacherExamService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TabSessionIsolationAndDurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private TeacherExamService teacherExamService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User teacherUser;
    private User studentUser;
    private CourseClass courseClass;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        teacherUser = userRepository.save(User.builder()
                .email("teacher_" + uid + "@fpt.edu.vn")
                .fullName("Teacher " + uid)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.TEACHER)
                .build());

        studentUser = userRepository.save(User.builder()
                .email("student_" + uid + "@fpt.edu.vn")
                .fullName("Student " + uid)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.STUDENT)
                .build());

        courseClass = courseClassRepository.save(CourseClass.builder()
                .code("PRJ_" + uid.toUpperCase())
                .name("PRJ Java " + uid)
                .semester("Fall 2026")
                .teacher(teacherUser)
                .build());
    }

    @Test
    @DisplayName("Tab Isolation: Two concurrent browser tabs maintain independent Teacher and Student sessions via X-Auth-Token")
    void testTwoTabsMaintainIndependentSessionsViaToken() throws Exception {
        // Tab 1: Teacher logs in
        LoginRequest teacherLogin = new LoginRequest();
        teacherLogin.setEmail(teacherUser.getEmail());
        teacherLogin.setPassword("Password123!");

        MvcResult teacherLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andReturn();

        JsonNode teacherJson = objectMapper.readTree(teacherLoginResult.getResponse().getContentAsString());
        String teacherToken = teacherJson.get("token").asText();

        // Tab 2: Student logs in
        LoginRequest studentLogin = new LoginRequest();
        studentLogin.setEmail(studentUser.getEmail());
        studentLogin.setPassword("Password123!");

        MvcResult studentLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(studentLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();

        JsonNode studentJson = objectMapper.readTree(studentLoginResult.getResponse().getContentAsString());
        String studentToken = studentJson.get("token").asText();

        // Verify tokens are distinct
        assertThat(teacherToken).isNotEqualTo(studentToken);

        // Tab 1 (Teacher) makes teacher API request with teacherToken -> 200 OK
        mockMvc.perform(get("/api/teacher/classes")
                        .header("X-Auth-Token", teacherToken))
                .andExpect(status().isOk());

        // Tab 2 (Student) attempts teacher API request with studentToken -> 403 Forbidden (Strict Backend RBAC)
        mockMvc.perform(get("/api/teacher/classes")
                        .header("X-Auth-Token", studentToken))
                .andExpect(status().isForbidden());

        // Tab 2 (Student) makes student API request with studentToken -> 200 OK
        mockMvc.perform(get("/api/student/exams")
                        .header("X-Auth-Token", studentToken))
                .andExpect(status().isOk());

        // Tab 1 (Teacher) attempts student API request with teacherToken -> 403 Forbidden
        mockMvc.perform(get("/api/student/exams")
                        .header("X-Auth-Token", teacherToken))
                .andExpect(status().isForbidden());

        // Logout on Tab 2 (Student) with studentToken
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Auth-Token", studentToken))
                .andExpect(status().isOk());

        // Tab 1 (Teacher) is STILL active and authenticated
        mockMvc.perform(get("/api/teacher/classes")
                        .header("X-Auth-Token", teacherToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Exam Duration: Creation, updating, aliases, and dual-field serialization in minutes")
    @Transactional
    void testExamDurationPersistenceAndDualSerialization() throws Exception {
        // 1. Create exam with 120 minutes duration
        LocalDateTime examDate = LocalDateTime.of(2026, 11, 1, 9, 0, 0);
        CreateExamDto createDto = CreateExamDto.builder()
                .title("Final Exam PRJ301")
                .classId(courseClass.getId())
                .date(examDate)
                .durationMinutes(120)
                .build();

        ExamResponse created = teacherExamService.createExam(createDto, teacherUser.getId());

        assertThat(created.getDuration()).isEqualTo(120);
        assertThat(created.getDurationMinutes()).isEqualTo(120);
        assertThat(created.getEndTime()).isEqualTo(examDate.plusMinutes(120));

        // 2. Verify JSON serialization exposes BOTH 'duration' and 'durationMinutes'
        String json = objectMapper.writeValueAsString(created);
        JsonNode tree = objectMapper.readTree(json);
        assertThat(tree.has("duration")).isTrue();
        assertThat(tree.get("duration").asInt()).isEqualTo(120);
        assertThat(tree.has("durationMinutes")).isTrue();
        assertThat(tree.get("durationMinutes").asInt()).isEqualTo(120);

        // 3. Update duration to 75 minutes
        UpdateExamDto updateDto = UpdateExamDto.builder()
                .durationMinutes(75)
                .build();

        ExamResponse updated = teacherExamService.updateExam(created.getId(), updateDto, teacherUser.getId());
        assertThat(updated.getDuration()).isEqualTo(75);
        assertThat(updated.getDurationMinutes()).isEqualTo(75);
        assertThat(updated.getEndTime()).isEqualTo(examDate.plusMinutes(75));

        // 4. Verify deserialization accepts alias 'duration' instead of 'durationMinutes'
        String incomingJson = "{\"title\":\"Alias Exam\",\"date\":\"2026-11-01T09:00:00\",\"duration\":150}";
        CreateExamDto parsedDto = objectMapper.readValue(incomingJson, CreateExamDto.class);
        assertThat(parsedDto.getDurationMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("Mandatory Tests T1-T12: 3 Concurrent Tabs (Student, Lecturer, Admin) with /api/user/profile and strict RBAC")
    void testThreeTabsWithThreeRolesIndependentSessions() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User adminUser = userRepository.save(User.builder()
                .email("admin_" + uid + "@fpt.edu.vn")
                .fullName("Admin " + uid)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.ADMIN)
                .build());

        // T1: Tab 1 logs in as Student
        LoginRequest sLogin = new LoginRequest();
        sLogin.setEmail(studentUser.getEmail());
        sLogin.setPassword("Password123!");
        MvcResult sRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();
        String studentToken = objectMapper.readTree(sRes.getResponse().getContentAsString()).get("token").asText();

        // T2: Tab 2 logs in as Lecturer/Teacher
        LoginRequest tLogin = new LoginRequest();
        tLogin.setEmail(teacherUser.getEmail());
        tLogin.setPassword("Password123!");
        MvcResult tRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andReturn();
        String teacherToken = objectMapper.readTree(tRes.getResponse().getContentAsString()).get("token").asText();

        // T4: Tab 3 logs in as Admin
        LoginRequest aLogin = new LoginRequest();
        aLogin.setEmail(adminUser.getEmail());
        aLogin.setPassword("Password123!");
        MvcResult aRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();
        String adminToken = objectMapper.readTree(aRes.getResponse().getContentAsString()).get("token").asText();

        // Ensure all 3 tokens are distinct
        assertThat(studentToken).isNotEqualTo(teacherToken);
        assertThat(teacherToken).isNotEqualTo(adminToken);
        assertThat(studentToken).isNotEqualTo(adminToken);

        // T3 & T6: Call /api/user/profile from each tab -> Returns exact user and role of that tab
        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(studentUser.getEmail()))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(teacherUser.getEmail()))
                .andExpect(jsonPath("$.role").value("TEACHER"));

        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(adminUser.getEmail()))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // T7 & T11: Call role-protected endpoints concurrently
        // Admin endpoint: Admin=200, Teacher=403, Student=403
        mockMvc.perform(get("/api/admin/exams").header("X-Auth-Token", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/exams").header("X-Auth-Token", teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/exams").header("X-Auth-Token", studentToken))
                .andExpect(status().isForbidden());

        // Teacher endpoint: Teacher=200, Student=403
        mockMvc.perform(get("/api/teacher/classes").header("X-Auth-Token", teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/teacher/classes").header("X-Auth-Token", studentToken))
                .andExpect(status().isForbidden());

        // Student endpoint: Student=200, Teacher=403
        mockMvc.perform(get("/api/student/exams").header("X-Auth-Token", studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/student/exams").header("X-Auth-Token", teacherToken))
                .andExpect(status().isForbidden());

        // T8: Logout Tab 1 (Student)
        mockMvc.perform(post("/api/auth/logout").header("X-Auth-Token", studentToken))
                .andExpect(status().isOk());

        // Student token is now revoked
        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", studentToken))
                .andExpect(status().isUnauthorized());

        // Tab 2 (Teacher) and Tab 3 (Admin) remain active and authenticated
        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // T10: Student logs back in with new token
        MvcResult sReLoginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sLogin)))
                .andExpect(status().isOk())
                .andReturn();
        String newStudentToken = objectMapper.readTree(sReLoginRes.getResponse().getContentAsString()).get("token").asText();

        // Verify Tab 2 (Teacher) and Tab 3 (Admin) still retain their original identities
        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));
        mockMvc.perform(get("/api/user/profile").header("X-Auth-Token", newStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }
}
