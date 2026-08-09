package com.example.admin.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

public final class LogMaskUtils {

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "oldPassword",
            "newPassword",
            "totpCode",
            "totpSecret",
            "secret",
            "apiKey",
            "accessToken",
            "refreshToken",
            "authorization",
            "access_token",
            "refresh_token",
            "api_key",
            "totp_secret",
            "phone",
            "mobile",
            "email",
            "idCard",
            "idCardNo");

    private LogMaskUtils() {
    }

    public static String toMaskedJson(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            mask(node);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException | RuntimeException exception) {
            return null;
        }
    }

    private static void mask(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                if (SENSITIVE_FIELDS.contains(entry.getKey()) && entry.getValue().isTextual()) {
                    objectNode.put(entry.getKey(), "******");
                } else {
                    mask(entry.getValue());
                }
            });
        } else {
            node.elements().forEachRemaining(LogMaskUtils::mask);
        }
    }
}
