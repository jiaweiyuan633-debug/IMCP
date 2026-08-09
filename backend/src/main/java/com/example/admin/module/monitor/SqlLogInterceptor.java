package com.example.admin.module.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        })
})
public class SqlLogInterceptor implements Interceptor {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.sql-log-threshold-ms:50}")
    private long thresholdMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        long start = System.currentTimeMillis();
        Object result;
        Throwable error = null;
        try {
            result = invocation.proceed();
        } catch (Throwable throwable) {
            error = throwable;
            throw throwable;
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (duration >= thresholdMs) {
                saveLog(sql, mappedStatement.getId(), duration, error == null, error == null ? null : error.getMessage());
            }
        }
        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no custom properties
    }

    private void saveLog(String sql, String method, long duration, boolean success, String errorMsg) {
        try {
            String sqlText = sql.length() > 2000 ? sql.substring(0, 2000) : sql;
            String error = errorMsg != null && errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg;
            jdbcTemplate.update(
                    "INSERT INTO sys_sql_log (sql_text, method, duration_ms, success, error_msg) VALUES (?, ?, ?, ?, ?)",
                    sqlText, method, duration, success ? 1 : 0, error);
        } catch (Exception exception) {
            log.warn("Failed to save sql log", exception);
        }
    }
}

