package com.codeassess.ai.client;

import com.codeassess.ai.dto.AiDocumentExamGenerateResponse;
import com.codeassess.ai.exception.AiIntegrationException;
import com.codeassess.ai.exception.AiTruncationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiAiClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private GeminiAiClient geminiAiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", "gemini-secret-api-key-xyz123");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        geminiAiClient = new GeminiAiClient(restClient, "gemini-2.5-flash", 8192, objectMapper);
    }

    @Test
    @DisplayName("generateSimilarityExplanation returns generated text on 200 OK")
    void generateSimilarityExplanation_success() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "The two code submissions share identical AST loop structures and logic."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 35,
                    "candidatesTokenCount": 14,
                    "totalTokenCount": 49
                  }
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret-api-key-xyz123"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiClient.generateSimilarityExplanation("Analyze AST similarity for pair #123");

        mockServer.verify();
        assertThat(result).isEqualTo("The two code submissions share identical AST loop structures and logic.");
    }

    @Test
    @DisplayName("generateCodeReview returns generated text on 200 OK")
    void generateCodeReview_success() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Both implementations use identical recursive traversal algorithms."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret-api-key-xyz123"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiClient.generateCodeReview("Review syntax match between code snippet A and B");

        mockServer.verify();
        assertThat(result).isEqualTo("Both implementations use identical recursive traversal algorithms.");
    }

    @Test
    @DisplayName("HTTP 401/403 maps to AiIntegrationException without leaking the API key")
    void authenticationFailure_handledSafelyWithoutLeakingSecret() {
        String mockErrorJson = """
                {
                  "error": {
                    "code": 403,
                    "message": "The provided API key is expired or invalid",
                    "status": "PERMISSION_DENIED"
                  }
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mockErrorJson));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider authentication failed. Please check the configured API key.")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("gemini-secret-api-key-xyz123"));

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 429 maps to rate limit AiIntegrationException")
    void rateLimit_handledSafely() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":429,\"message\":\"Quota exceeded\"}}"));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider rate limit exceeded. Please try again shortly.");

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 500 maps to provider unavailable AiIntegrationException")
    void serverError_handledSafely() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider service is currently unavailable. Please try again later.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Empty candidates in response maps to clear AiIntegrationException")
    void emptyCandidates_handledSafely() {
        String mockEmptyResponseJson = """
                {
                  "candidates": []
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockEmptyResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider returned an empty response.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Response blocked by safety filter maps to clear AiIntegrationException")
    void safetyBlock_handledSafely() {
        String mockSafetyResponseJson = """
                {
                  "candidates": [
                    {
                      "finishReason": "SAFETY",
                      "index": 0
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockSafetyResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI response was blocked by safety filters (finishReason: SAFETY).");

        mockServer.verify();
    }

    @Test
    @DisplayName("Blank prompt is rejected before HTTP request")
    void blankPrompt_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt cannot be empty");
    }

    @Test
    @DisplayName("Constructor rejects empty or null API key")
    void constructor_rejectsEmptyApiKey() {
        assertThatThrownBy(() -> new GeminiAiClient("", "gemini-2.5-flash", 30, null, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Gemini API key must not be null or empty");

        assertThatThrownBy(() -> new GeminiAiClient(null, "gemini-2.5-flash", 30, null, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Gemini API key must not be null or empty");
    }

    // =========================================================================
    // TASK-AI-JSON-01 TEST CASES
    // =========================================================================

    @Test
    @DisplayName("1. Valid structured JSON: correctly configures responseMimeType and parses DTO")
    void generateStructuredOutput_validStructuredJson() {
        String rawExamJson = """
                {
                  "title": "Thuật toán sắp xếp QuickSort",
                  "problemStatement": "Cài đặt thuật toán QuickSort phân đoạn Lomuto.",
                  "inputSpecification": "Dòng 1: n số nguyên.",
                  "outputSpecification": "In ra mảng đã sắp xếp.",
                  "constraints": "1 <= n <= 10^5",
                  "sampleInput": "4\\n4 2 1 3",
                  "sampleOutput": "1 2 3 4"
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawExamJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"responseMimeType\":\"application/json\"")))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System instruction",
                "User prompt",
                AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Thuật toán sắp xếp QuickSort");
        assertThat(result.getProblemStatement()).isEqualTo("Cài đặt thuật toán QuickSort phân đoạn Lomuto.");
    }

    @Test
    @DisplayName("2. problemStatement containing Vietnamese text: Unicode preserved without corruption")
    void generateStructuredOutput_vietnameseText() {
        String rawExamJson = """
                {
                  "title": "Kiểm tra cây nhị phân tìm kiếm (BST)",
                  "problemStatement": "Cho một cây nhị phân gồm N nút có giá trị nguyên. Hãy xác định cây có phải là Cây Nhị Phân Tìm Kiếm hợp lệ không? Đảm bảo duyệt trung thứ tự tăng dần ngặt.",
                  "inputSpecification": "Dòng 1: số nguyên N đại diện cho số lượng đỉnh.",
                  "outputSpecification": "In ra 'YES' nếu là BST hợp lệ, ngược lại in 'NO'."
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawExamJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result.getProblemStatement()).contains("Cây Nhị Phân Tìm Kiếm hợp lệ không?");
        assertThat(result.getOutputSpecification()).contains("In ra 'YES' nếu là BST hợp lệ");
    }

    @Test
    @DisplayName("3. problemStatement containing quotes and backslashes: Escaping handled accurately")
    void generateStructuredOutput_quotesAndBackslashes() {
        String rawExamJson = """
                {
                  "title": "Xử lý ký tự thoát trong String",
                  "problemStatement": "Viết chương trình đọc đường dẫn file Windows dạng \\"C:\\\\Users\\\\Admin\\\\data.txt\\" và in ra tên thư mục.",
                  "inputSpecification": "Chuỗi chứa dấu gạch chéo ngược \\\\ và dấu nháy kép \\".",
                  "outputSpecification": "Chuỗi thư mục cha."
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawExamJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result.getProblemStatement()).contains("C:\\Users\\Admin\\data.txt");
    }

    @Test
    @DisplayName("4. problemStatement containing Markdown code blocks: Code formatting preserved")
    void generateStructuredOutput_markdownCodeBlocks() {
        String rawExamJson = """
                {
                  "title": "Mô phỏng ngăn xếp",
                  "problemStatement": "### Mô tả bài toán\\nCài đặt cấu trúc dữ liệu `Stack` với interface:\\n```java\\npublic interface CustomStack {\\n    void push(int x);\\n    int pop();\\n}\\n```\\nTuân thủ nguyên tắc LIFO.",
                  "inputSpecification": "Các thao tác push và pop.",
                  "outputSpecification": "Kết quả các lần pop."
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawExamJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result.getProblemStatement()).contains("```java");
        assertThat(result.getProblemStatement()).contains("public interface CustomStack");
    }

    @Test
    @DisplayName("5. Newline characters in JSON strings: Parsed cleanly without error")
    void generateStructuredOutput_newlineCharacters() {
        String rawExamJson = """
                {
                  "title": "Xử lý văn bản nhiều dòng",
                  "problemStatement": "Dòng 1: Giới thiệu\\nDòng 2: Yêu cầu chi tiết\\nDòng 3: Kết luận",
                  "inputSpecification": "Văn bản đầu vào\\nCó nhiều dòng.",
                  "outputSpecification": "Kết quả chuẩn hóa."
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawExamJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result.getProblemStatement()).contains("\n");
        assertThat(result.getProblemStatement()).isEqualTo("Dòng 1: Giới thiệu\nDòng 2: Yêu cầu chi tiết\nDòng 3: Kết luận");
    }

    @Test
    @DisplayName("6. Truncated JSON: Unexpected end-of-input throws AiTruncationException")
    void generateStructuredOutput_truncatedJson_throwsAiTruncationException() {
        // Truncated raw string simulating sudden cut off mid-string
        String truncatedJson = "{\"title\":\"Đề thi bị ngắt giữa chừng\",\"problemStatement\":\"Đây là đoạn văn bị cắt ngang";

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(truncatedJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateStructuredOutput("System", "Prompt", AiDocumentExamGenerateResponse.class))
                .isInstanceOf(AiTruncationException.class)
                .hasMessageContaining("truncated structured JSON output");

        mockServer.verify();
    }

    @Test
    @DisplayName("9. Wrong field types: Invalid scalar / type mismatch throws AiIntegrationException")
    void generateStructuredOutput_wrongFieldTypes_throwsAiIntegrationException() {
        // String instead of object or malformed JSON syntax
        String invalidJson = "{ invalid json without quotes: 123 }";

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(invalidJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateStructuredOutput("System", "Prompt", AiDocumentExamGenerateResponse.class))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessageContaining("AI provider returned invalid structured JSON output");

        mockServer.verify();
    }

    @Test
    @DisplayName("10. Provider response exceeding output limits: finishReason=MAX_TOKENS throws AiTruncationException")
    void generateStructuredOutput_maxTokensFinishReason_throwsAiTruncationException() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"title\\":\\"Đề thi ngắt do vượt token\\",\\"problemStatement\\":\\"Mô tả dài..."
                          }
                        ]
                      },
                      "finishReason": "MAX_TOKENS",
                      "index": 0
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateStructuredOutput("System", "Prompt", AiDocumentExamGenerateResponse.class))
                .isInstanceOf(AiTruncationException.class)
                .hasMessageContaining("truncated at MAX_TOKENS");

        mockServer.verify();
    }

    // =========================================================================
    // TASK-AI-JSON-04 TEST CASES
    // =========================================================================

    @Test
    @DisplayName("TASK-AI-JSON-04: Raw JSON array response for AiTestCaseBatchResponse is converted cleanly")
    void generateStructuredOutput_rawJsonArrayForTestCaseBatch_convertedSuccessfully() {
        String rawArrayJson = """
                [
                  {
                    "name": "TC 1 - Normal",
                    "input": "5\\n1 2 3",
                    "output": "6",
                    "score": 5.0,
                    "is_hidden": false
                  },
                  {
                    "test_case_name": "TC 2 - Edge Case",
                    "input_data": "0\\n",
                    "expected_output": "0",
                    "weight": 5.0,
                    "hidden": true
                  }
                ]
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawArrayJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        com.codeassess.ai.dto.AiTestCaseBatchResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", com.codeassess.ai.dto.AiTestCaseBatchResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTestCases()).hasSize(2);
        assertThat(result.getTestCases().get(0).getName()).isEqualTo("TC 1 - Normal");
        assertThat(result.getTestCases().get(0).getInputData()).isEqualTo("5\n1 2 3");
        assertThat(result.getTestCases().get(0).getExpectedOutput()).isEqualTo("6");
        assertThat(result.getTestCases().get(0).getIsHidden()).isFalse();

        assertThat(result.getTestCases().get(1).getName()).isEqualTo("TC 2 - Edge Case");
        assertThat(result.getTestCases().get(1).getInputData()).isEqualTo("0\n");
        assertThat(result.getTestCases().get(1).getExpectedOutput()).isEqualTo("0");
        assertThat(result.getTestCases().get(1).getIsHidden()).isTrue();
    }

    @Test
    @DisplayName("TASK-AI-JSON-04: Snake_case and alias property names in AiDocumentExamGenerateResponse map accurately")
    void generateStructuredOutput_snakeCaseAndAliases_mappedAccurately() {
        String rawExamJson = """
                {
                  "questionTitle": "Cài đặt cây BST",
                  "problem_statement": "Viết chương trình thêm và tìm kiếm phần tử trong cây nhị phân tìm kiếm.",
                  "input_specification": "Dòng 1: N phần tử.",
                  "output_specification": "Kết quả duyệt tiền thứ tự.",
                  "constraint": "1 <= N <= 10^5",
                  "sample_input": "3\\n2 1 3",
                  "sample_output": "2 1 3",
                  "learning_objectives": ["Hiểu cấu trúc cây BST", "Tối ưu tìm kiếm"],
                  "source_references": ["Chương 4: Cây nhị phân, tr. 45"],
                  "test_cases": [
                    {
                      "name": "TC 1",
                      "input": "1\\n10",
                      "output": "10"
                    }
                  ],
                  "rubric_criteria": [
                    {
                      "criterion": "Cài đặt nút cây",
                      "score": 5.0
                    },
                    {
                      "criterion": "Hàm thêm và tìm",
                      "score": 5.0
                    }
                  ]
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawExamJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Cài đặt cây BST");
        assertThat(result.getProblemStatement()).isEqualTo("Viết chương trình thêm và tìm kiếm phần tử trong cây nhị phân tìm kiếm.");
        assertThat(result.getInputSpecification()).isEqualTo("Dòng 1: N phần tử.");
        assertThat(result.getOutputSpecification()).isEqualTo("Kết quả duyệt tiền thứ tự.");
        assertThat(result.getConstraints()).isEqualTo("1 <= N <= 10^5");
        assertThat(result.getSampleInput()).isEqualTo("3\n2 1 3");
        assertThat(result.getSampleOutput()).isEqualTo("2 1 3");
        assertThat(result.getLearningObjectives()).containsExactly("Hiểu cấu trúc cây BST", "Tối ưu tìm kiếm");
        assertThat(result.getSourceReferences()).containsExactly("Chương 4: Cây nhị phân, tr. 45");
        assertThat(result.getTestCases()).hasSize(1);
        assertThat(result.getTestCases().get(0).getInputData()).isEqualTo("1\n10");
        assertThat(result.getTestCases().get(0).getExpectedOutput()).isEqualTo("10");
        assertThat(result.getRubricCriteria()).hasSize(2);
        assertThat(result.getRubricCriteria().get(0).getName()).isEqualTo("Cài đặt nút cây");
        assertThat(result.getRubricCriteria().get(0).getMaxScore()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("TASK-AI-JSON-04: Raw JSON array response for AiExamOutlineResponse is converted cleanly")
    void generateStructuredOutput_rawJsonArrayForOutline_convertedSuccessfully() {
        String rawOutlineArray = """
                [
                  {
                    "question_index": 1,
                    "title": "Cấu trúc Stack",
                    "concept": "Ngăn xếp LIFO",
                    "summary": "Cài đặt ngăn xếp",
                    "difficulty": "EASY"
                  },
                  {
                    "question_index": 2,
                    "title": "Cấu trúc Queue",
                    "concept": "Hàng đợi FIFO",
                    "summary": "Cài đặt hàng đợi",
                    "difficulty": "MEDIUM"
                  }
                ]
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(rawOutlineArray));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        com.codeassess.ai.dto.AiExamOutlineResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", com.codeassess.ai.dto.AiExamOutlineResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getQuestions()).hasSize(2);
        assertThat(result.getQuestions().get(0).getQuestionIndex()).isEqualTo(1);
        assertThat(result.getQuestions().get(0).getTitle()).isEqualTo("Cấu trúc Stack");
        assertThat(result.getQuestions().get(0).getTargetConcept()).isEqualTo("Ngăn xếp LIFO");
        assertThat(result.getQuestions().get(0).getShortSummary()).isEqualTo("Cài đặt ngăn xếp");

        assertThat(result.getQuestions().get(1).getQuestionIndex()).isEqualTo(2);
        assertThat(result.getQuestions().get(1).getTitle()).isEqualTo("Cấu trúc Queue");
    }

    @Test
    @DisplayName("TASK-AI-JSON-02: Nested object wrapper under 'question' key is unwrapped cleanly")
    void generateStructuredOutput_nestedQuestionObjectWrapper_unwrappedSuccessfully() {
        String wrappedJson = """
                {
                  "question": {
                    "title": "Cài đặt hàng đợi ưu tiên",
                    "problem_description": "Cài đặt Priority Queue bằng Min Heap.",
                    "input_format": "N dòng chứa thao tác push/pop",
                    "output_format": "Các phần tử được pop theo thứ tự tăng dần",
                    "limits": "1 <= N <= 100000"
                  }
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(wrappedJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Cài đặt hàng đợi ưu tiên");
        assertThat(result.getProblemStatement()).isEqualTo("Cài đặt Priority Queue bằng Min Heap.");
        assertThat(result.getInputSpecification()).isEqualTo("N dòng chứa thao tác push/pop");
        assertThat(result.getOutputSpecification()).isEqualTo("Các phần tử được pop theo thứ tự tăng dần");
        assertThat(result.getConstraints()).isEqualTo("1 <= N <= 100000");
    }

    @Test
    @DisplayName("TASK-AI-JSON-02: Nested question array under 'questions' key is unwrapped for single question")
    void generateStructuredOutput_nestedQuestionArrayInObjectWrapper_unwrappedSuccessfully() {
        String wrappedArrayJson = """
                {
                  "questions": [
                    {
                      "title": "Duyệt cây theo mức (BFS)",
                      "task": "Cài đặt duyệt cây theo mức sử dụng Hàng đợi (Queue).",
                      "inputSpecification": "Cây nhị phân",
                      "outputSpecification": "Thứ tự các nút"
                    }
                  ]
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(wrappedArrayJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiDocumentExamGenerateResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", AiDocumentExamGenerateResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Duyệt cây theo mức (BFS)");
        assertThat(result.getProblemStatement()).isEqualTo("Cài đặt duyệt cây theo mức sử dụng Hàng đợi (Queue).");
    }

    @Test
    @DisplayName("TASK-AI-JSON-02: Test case visibility string ('HIDDEN'/'PUBLIC') deserializes accurately")
    void generateStructuredOutput_testCaseVisibilityString_deserializedAccurately() {
        String tcBatchJson = """
                {
                  "testCases": [
                    {
                      "name": "TC Public 1",
                      "input": "1 2",
                      "output": "3",
                      "points": 5.0,
                      "visibility": "PUBLIC"
                    },
                    {
                      "name": "TC Hidden 2",
                      "input": "5 10",
                      "output": "15",
                      "points": 5.0,
                      "visibility": "HIDDEN"
                    }
                  ]
                }
                """;

        String mockResponseJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """, objectMapper.valueToTree(tcBatchJson));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        com.codeassess.ai.dto.AiTestCaseBatchResponse result = geminiAiClient.generateStructuredOutput(
                "System", "Prompt", com.codeassess.ai.dto.AiTestCaseBatchResponse.class
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTestCases()).hasSize(2);
        assertThat(result.getTestCases().get(0).getIsHidden()).isFalse();
        assertThat(result.getTestCases().get(1).getIsHidden()).isTrue();
    }
}
