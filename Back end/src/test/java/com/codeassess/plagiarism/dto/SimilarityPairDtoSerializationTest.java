package com.codeassess.plagiarism.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class SimilarityPairDtoSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Verify serialization produces both isFlagged and flagged for true value")
    void testSerializationTrue() throws Exception {
        SimilarityPairDto dto = SimilarityPairDto.builder()
                .pairId("1")
                .similarityRate(new BigDecimal("89.29"))
                .isFlagged(true)
                .build();

        String json = objectMapper.writeValueAsString(dto);
        System.out.println("SERIALIZED JSON (TRUE): " + json);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("isFlagged"), "JSON should contain 'isFlagged' key");
        assertTrue(node.get("isFlagged").asBoolean(), "'isFlagged' should be true");

        assertTrue(node.has("flagged"), "JSON should contain backward-compatible 'flagged' key");
        assertTrue(node.get("flagged").asBoolean(), "'flagged' should be true");
    }

    @Test
    @DisplayName("Verify serialization produces both isFlagged and flagged for false value")
    void testSerializationFalse() throws Exception {
        SimilarityPairDto dto = SimilarityPairDto.builder()
                .pairId("2")
                .similarityRate(new BigDecimal("12.50"))
                .isFlagged(false)
                .build();

        String json = objectMapper.writeValueAsString(dto);
        System.out.println("SERIALIZED JSON (FALSE): " + json);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("isFlagged"), "JSON should contain 'isFlagged' key");
        assertFalse(node.get("isFlagged").asBoolean(), "'isFlagged' should be false");

        assertTrue(node.has("flagged"), "JSON should contain backward-compatible 'flagged' key");
        assertFalse(node.get("flagged").asBoolean(), "'flagged' should be false");
    }

    @Test
    @DisplayName("Verify deserialization from JSON with isFlagged: true")
    void testDeserializationWithIsFlaggedTrue() throws Exception {
        String json = "{\"pairId\":\"1\",\"similarityRate\":89.29,\"isFlagged\":true}";
        SimilarityPairDto dto = objectMapper.readValue(json, SimilarityPairDto.class);
        assertTrue(dto.isFlagged());
    }

    @Test
    @DisplayName("Verify deserialization from JSON with isFlagged: false")
    void testDeserializationWithIsFlaggedFalse() throws Exception {
        String json = "{\"pairId\":\"1\",\"similarityRate\":12.50,\"isFlagged\":false}";
        SimilarityPairDto dto = objectMapper.readValue(json, SimilarityPairDto.class);
        assertFalse(dto.isFlagged());
    }

    @Test
    @DisplayName("Verify deserialization from JSON with legacy flagged: true")
    void testDeserializationWithFlaggedTrue() throws Exception {
        String json = "{\"pairId\":\"1\",\"similarityRate\":89.29,\"flagged\":true}";
        SimilarityPairDto dto = objectMapper.readValue(json, SimilarityPairDto.class);
        assertTrue(dto.isFlagged());
    }

    @Test
    @DisplayName("Verify deserialization from JSON with legacy flagged: false")
    void testDeserializationWithFlaggedFalse() throws Exception {
        String json = "{\"pairId\":\"1\",\"similarityRate\":12.50,\"flagged\":false}";
        SimilarityPairDto dto = objectMapper.readValue(json, SimilarityPairDto.class);
        assertFalse(dto.isFlagged());
    }

    @Test
    @DisplayName("Verify deserialization when both keys are present")
    void testDeserializationWithBothKeys() throws Exception {
        String json = "{\"pairId\":\"1\",\"similarityRate\":89.29,\"isFlagged\":true,\"flagged\":true}";
        SimilarityPairDto dto = objectMapper.readValue(json, SimilarityPairDto.class);
        assertTrue(dto.isFlagged());
    }
}
