package com.codeassess.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlexibleStringDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deserializes string constraints correctly")
    void testStringConstraints() throws Exception {
        String json = """
                {
                  "title": "Sum Problem",
                  "constraints": "1 <= N <= 100"
                }
                """;
        AiDocumentExamGenerateResponse res = objectMapper.readValue(json, AiDocumentExamGenerateResponse.class);
        assertThat(res.getConstraints()).isEqualTo("1 <= N <= 100");
    }

    @Test
    @DisplayName("Deserializes array constraints to newline-separated string")
    void testArrayConstraints() throws Exception {
        String json = """
                {
                  "title": "Sum Problem",
                  "constraints": [
                    "1 <= N <= 10^5",
                    "-10^9 <= a[i] <= 10^9"
                  ]
                }
                """;
        AiDocumentExamGenerateResponse res = objectMapper.readValue(json, AiDocumentExamGenerateResponse.class);
        assertThat(res.getConstraints()).isEqualTo("1 <= N <= 10^5\n-10^9 <= a[i] <= 10^9");
    }

    @Test
    @DisplayName("Deserializes array inputSpecification and outputSpecification")
    void testArraySpecifications() throws Exception {
        String json = """
                {
                  "title": "Sum Problem",
                  "inputSpecification": ["Line 1: integer N", "Line 2: N integers"],
                  "outputSpecification": ["A single integer sum"]
                }
                """;
        AiDocumentExamGenerateResponse res = objectMapper.readValue(json, AiDocumentExamGenerateResponse.class);
        assertThat(res.getInputSpecification()).isEqualTo("Line 1: integer N\nLine 2: N integers");
        assertThat(res.getOutputSpecification()).isEqualTo("A single integer sum");
    }

    @Test
    @DisplayName("Deserializes numeric or boolean expectedOutput in AiTestCaseDraftDto")
    void testTestCaseDraftDtoOutputs() throws Exception {
        String json = """
                {
                  "name": "Case 1",
                  "inputData": "5\\n1 2 3 4 5",
                  "expectedOutput": 15
                }
                """;
        AiTestCaseDraftDto tc = objectMapper.readValue(json, AiTestCaseDraftDto.class);
        assertThat(tc.getExpectedOutput()).isEqualTo("15");
        assertThat(tc.getName()).isEqualTo("Case 1");
    }

    @Test
    @DisplayName("Deserializes array expectedOutput in AiTestCaseDraftDto")
    void testTestCaseDraftDtoArrayOutput() throws Exception {
        String json = """
                {
                  "name": "Case 2",
                  "inputData": [5, 1, 2, 3],
                  "expectedOutput": [1, 2, 3]
                }
                """;
        AiTestCaseDraftDto tc = objectMapper.readValue(json, AiTestCaseDraftDto.class);
        assertThat(tc.getExpectedOutput()).isEqualTo("1\n2\n3");
        assertThat(tc.getInputData()).isEqualTo("5\n1\n2\n3");
    }

    @Test
    @DisplayName("Respects aliases such as result, expectedResult, outputData")
    void testTestCaseAliases() throws Exception {
        String json = """
                {
                  "testCaseName": "Sample Alias",
                  "input": "test input",
                  "result": 42
                }
                """;
        AiTestCaseDraftDto tc = objectMapper.readValue(json, AiTestCaseDraftDto.class);
        assertThat(tc.getName()).isEqualTo("Sample Alias");
        assertThat(tc.getInputData()).isEqualTo("test input");
        assertThat(tc.getExpectedOutput()).isEqualTo("42");
    }
}

