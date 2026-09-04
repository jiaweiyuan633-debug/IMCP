package cn.admin.scaffold.config;

import cn.admin.scaffold.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyMetaObjectHandlerTest {

    private final MetaObject metaObject = mock(MetaObject.class);

    // 真实实现通过 setFieldValByName -> metaObject.hasSetter 守卫后才写入，
    // 因此必须让 mock 的 hasSetter 返回 true，否则 setValue 永不被调用。
    @BeforeEach
    void stubHasSetter() {
        when(metaObject.hasSetter(anyString())).thenReturn(true);
    }

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
