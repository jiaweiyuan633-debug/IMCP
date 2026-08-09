package com.example.admin.common;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeInnerInterceptorTest {

    @Mock
    private Executor executor;

    @Mock
    private MappedStatement mappedStatement;

    @Mock
    private BoundSql boundSql;

    @AfterEach
    void clearDataScope() {
        DataScopeContext.clear();
    }

    @Test
    void skipsRewriteWhenAllDataScopeReturnsNullUserIds() {
        DataScopeContext.set(new DataScopeContext.Filter(
                null, null, Set.of("sys_user"), false));
        when(boundSql.getSql()).thenReturn("SELECT * FROM sys_user");
        DataScopeInnerInterceptor interceptor = new DataScopeInnerInterceptor();

        interceptor.beforeQuery(
                executor, mappedStatement, null, RowBounds.DEFAULT, null, boundSql);

        verify(boundSql).getSql();
    }
}
