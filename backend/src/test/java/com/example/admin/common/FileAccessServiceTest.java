package com.example.admin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAccessServiceTest {

    private final FileAccessService fileAccessService = new FileAccessService("test-secret");

    @Test
    void issueAndVerifyToken() {
        String token = fileAccessService.issue("/uploads/test.png");
        assertTrue(fileAccessService.verify("/uploads/test.png", token));
        assertFalse(fileAccessService.verify("/uploads/other.png", token));
        assertFalse(fileAccessService.verify("/uploads/test.png", "tampered"));
    }
}
