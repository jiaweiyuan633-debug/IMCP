package com.example.admin.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void masksSensitiveFieldsRecursively() throws Exception {
        Payload payload = new Payload("admin", "plain-password", "real@example.com", "13800138000", Map.of("apiKey", "secret-key"));

        String json = LogMaskUtils.toMaskedJson(payload, objectMapper);

        assertTrue(json.contains("\"password\":\"******\""));
        assertTrue(json.contains("\"email\":\"******\""));
        assertTrue(json.contains("\"phone\":\"******\""));
        assertTrue(json.contains("\"apiKey\":\"******\""));
        assertFalse(json.contains("plain-password"));
        assertFalse(json.contains("secret-key"));
        assertEquals("admin", objectMapper.readTree(json).get("username").asText());
    }

    @Data
    @AllArgsConstructor
    private static class Payload {
        private String username;
        private String password;
        private String email;
        private String phone;
        private Map<String, String> nested;
    }
}
