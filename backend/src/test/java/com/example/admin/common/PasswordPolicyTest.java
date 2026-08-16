package com.example.admin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码复杂度策略单测（R4-1.40 批次13）。
 *
 * <p>策略升级为「8-32 位，需同时包含大写/小写/数字/特殊字符」。四类字符缺一不可，
 * 长度上下限均须严格执行——只含字母+数字的弱口令（password、a1234567）必须被拒绝。
 */
class PasswordPolicyTest {

    @Test
    void acceptsStrongPassword() {
        assertTrue(PasswordPolicy.matches("Abc@12345"));
        assertTrue(PasswordPolicy.matches("P@ssw0rd!"));
    }

    @Test
    void rejectsMissingUppercase() {
        assertFalse(PasswordPolicy.matches("abc@12345"));
    }

    @Test
    void rejectsMissingLowercase() {
        assertFalse(PasswordPolicy.matches("ABC@12345"));
    }

    @Test
    void rejectsMissingDigit() {
        assertFalse(PasswordPolicy.matches("Abc@defgh"));
    }

    @Test
    void rejectsMissingSpecialChar() {
        assertFalse(PasswordPolicy.matches("Abc12345"));
    }

    @Test
    void rejectsTooShort() {
        assertFalse(PasswordPolicy.matches("Ab@1"));
    }

    @Test
    void rejectsTooLong() {
        assertFalse(PasswordPolicy.matches("Abcdefgh1@Abcdefgh1@Abcdefgh1@Abc"));
    }

    @Test
    void rejectsNullAndBlank() {
        assertFalse(PasswordPolicy.matches(null));
        assertFalse(PasswordPolicy.matches(""));
    }

    @Test
    void rejectsLegacyWeakPassword() {
        // 旧策略（字母+数字）放行的弱口令在新策略下必须被拒绝
        assertFalse(PasswordPolicy.matches("a1234567"));
        assertFalse(PasswordPolicy.matches("password1"));
    }
}
