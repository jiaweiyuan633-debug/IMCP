package com.example.admin.module.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totpService = new TotpService("test-encryption-key");

    @Test
    void generateSecretAndEncryptDecrypt() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.length() >= 16);

        String encrypted = totpService.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, totpService.decrypt(encrypted));
    }

    @Test
    void verifyRejectsBlankCode() {
        assertFalse(totpService.verify("SECRET", ""));
    }
}
