package com.example.admin.module.monitor.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JobInvokeUtilTest {

    @Test
    void rejectsInvalidInvokeTarget() {
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.invoke("missing-dot"));
    }
}
