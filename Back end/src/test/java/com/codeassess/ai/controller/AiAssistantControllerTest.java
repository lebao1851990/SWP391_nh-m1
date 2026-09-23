package com.codeassess.ai.controller;

import com.codeassess.ai.dto.AiAnalysisResponse;
import com.codeassess.ai.dto.AiCodeReviewRequest;
import com.codeassess.ai.service.AiAssistantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;
import com.codeassess.common.exception.GlobalExceptionHandler;

@WebMvcTest(AiAssistantController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false) // Bypassing security config for controller tests
class AiAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiAssistantService aiAssistantService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "TEACHER")
    void testGetSimilarityExplanation() throws Exception {
        when(aiAssistantService.getSimilarityExplanation(1L))
                .thenReturn(new AiAnalysisResponse("Test explanation"));

        mockMvc.perform(post("/api/teacher/plagiarism/1/ai/similarity-explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").value("Test explanation"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void testGetCodeReview_ValidRequest() throws Exception {
        AiCodeReviewRequest request = new AiCodeReviewRequest("ev123");
        when(aiAssistantService.getCodeReview(eq(1L), any(AiCodeReviewRequest.class)))
                .thenReturn(new AiAnalysisResponse("Test review"));

        mockMvc.perform(post("/api/teacher/plagiarism/1/ai/code-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").value("Test review"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void testGetCodeReview_EmptyBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/teacher/plagiarism/1/ai/code-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
