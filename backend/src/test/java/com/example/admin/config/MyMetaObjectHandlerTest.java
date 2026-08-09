package com.example.admin.config;

import com.example.admin.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class MyMetaObjectHandlerTest {

    private final MetaObject metaObject = mock(MetaObject.class);

    @Test
    void insertFillSetsAllAuditFields() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::tryGetUserId).thenReturn(1L);
            new MyMetaObjectHandler().insertFill(metaObject);
        }
        verify(metaObject).setValue(eq("createdAt"), any());
        verify(metaObject).setValue(eq("updatedAt"), any());
        verify(metaObject).setValue(eq("createdBy"), eq(1L));
        verify(metaObject).setValue(eq("updatedBy"), eq(1L));
    }

    @Test
    void updateFillSetsOnlyUpdateFields() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::tryGetUserId).thenReturn(1L);
            new MyMetaObjectHandler().updateFill(metaObject);
        }
        verify(metaObject).setValue(eq("updatedAt"), any());
        verify(metaObject).setValue(eq("updatedBy"), eq(1L));
    }
}
