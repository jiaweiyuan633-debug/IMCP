package com.example.admin.module.report;

import com.example.admin.common.TenantContext;
import com.example.admin.module.report.vo.NameValueVo;
import com.example.admin.module.report.vo.RecentOperVo;
import com.example.admin.module.report.vo.ReportCenterVo;
import com.example.admin.module.report.vo.ReportScreenVo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表与数据大屏数据聚合。日志/业务表均带 tenant_id，JdbcTemplate 直查时手动追加租户条件，
 * 与 MyBatis 租户拦截器行为保持一致；flow_instance 为流程引擎表，不含租户列，全量统计。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int TREND_DAYS = 7;
    private static final int RECENT_OPER_LIMIT = 10;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public ReportCenterVo center() {
        Long tenantId = TenantContext.getTenantId();
        return ReportCenterVo.builder()
                .userCount(countTenant("sys_user", tenantId))
                .roleCount(countTenant("sys_role", tenantId))
                .deptCount(countTenant("sys_dept", tenantId))
                .deviceCount(countTenant("sys_device", tenantId))
                .jobCount(countTenant("sys_job", tenantId))
                .flowCount(count("flow_instance"))
                .loginTrend(loginTrend(tenantId))
                .operByModule(groupBy("sys_oper_log", "module", tenantId, TREND_DAYS, "oper_time"))
                .deviceByType(groupBy("sys_device", "device_type", tenantId, null, null))
                .deviceByStatus(groupBy("sys_device", "status", tenantId, null, null))
                .jobByStatus(groupBy("sys_job_log", "status", tenantId, TREND_DAYS, "start_time"))
                .aiByStatus(groupBy("ai_task", "status", tenantId, TREND_DAYS, "created_at"))
                .build();
    }

    public ReportScreenVo screen() {
        Long tenantId = TenantContext.getTenantId();
        return ReportScreenVo.builder()
                .loginSuccessCount(countByStatus("sys_login_log", "status", 1, tenantId))
                .operTotal(countTenant("sys_oper_log", tenantId))
                .operErrorCount(countByStatus("sys_oper_log", "status", 0, tenantId))
                .aiTaskCount(countTenant("ai_task", tenantId))
                .loginTrend(loginTrend(tenantId))
                .operTrend(groupBy("sys_oper_log", "oper_time", tenantId, TREND_DAYS, "oper_time"))
                .operByModule(groupBy("sys_oper_log", "module", tenantId, TREND_DAYS, "oper_time"))
                .deviceByType(groupBy("sys_device", "device_type", tenantId, null, null))
                .deviceByStatus(groupBy("sys_device", "status", tenantId, null, null))
                .jobByStatus(groupBy("sys_job_log", "status", tenantId, TREND_DAYS, "start_time"))
                .aiByStatus(groupBy("ai_task", "status", tenantId, TREND_DAYS, "created_at"))
                .recentOpers(recentOpers(tenantId))
                .build();
    }

    private long count(String table) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return total == null ? 0 : total;
    }

    private long countTenant(String table, Long tenantId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenantId);
        return total == null ? 0 : total;
    }

    private long countByStatus(String table, String column, int status, Long tenantId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND " + column + " = ?",
                Long.class, tenantId, status);
        return total == null ? 0 : total;
    }

    /** 近 7 天按日统计，Java 端补齐缺失日期，保证折线图横轴连续。 */
    private List<NameValueVo> loginTrend(Long tenantId) {
        return dailyTrend("sys_login_log", "login_time", tenantId);
    }

    private List<NameValueVo> dailyTrend(String table, String timeColumn, Long tenantId) {
        List<NameValueVo> rows = jdbcTemplate.query(
                "SELECT DATE_FORMAT(" + timeColumn + ", '%Y-%m-%d') AS name, COUNT(*) AS value "
                        + "FROM " + table
                        + " WHERE tenant_id = ? AND " + timeColumn + " >= ?"
                        + " GROUP BY DATE_FORMAT(" + timeColumn + ", '%Y-%m-%d')"
                        + " ORDER BY name",
                this::mapNameValue, tenantId, LocalDate.now().minusDays(TREND_DAYS - 1L));
        Map<String, Long> byDay = new LinkedHashMap<>();
        for (NameValueVo row : rows) {
            byDay.put(row.getName(), row.getValue());
        }
        List<NameValueVo> result = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(DAY_FORMAT);
            result.add(new NameValueVo(day, byDay.getOrDefault(day, 0L)));
        }
        return result;
    }

    /**
     * 按指定列分组统计。column 为日期列时按日聚合；days 非空且 timeColumn 非空时
     * 用该时间列过滤最近 N 天（各表时间列不同：oper_time/start_time/created_at）。
     * 查询结果按计数倒序，便于前端取 TOP 分布。
     */
    private List<NameValueVo> groupBy(String table, String column, Long tenantId, Integer days, String timeColumn) {
        String groupExpr = column.equals("oper_time") || column.equals("login_time")
                ? "DATE_FORMAT(" + column + ", '%Y-%m-%d')"
                : column;
        StringBuilder sql = new StringBuilder("SELECT ").append(groupExpr)
                .append(" AS name, COUNT(*) AS value FROM ").append(table)
                .append(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (days != null && timeColumn != null) {
            sql.append(" AND ").append(timeColumn).append(" >= ?");
            args.add(LocalDate.now().minusDays(days - 1L).atStartOfDay());
        }
        sql.append(" GROUP BY ").append(groupExpr).append(" ORDER BY value DESC, name");
        return jdbcTemplate.query(sql.toString(), this::mapNameValue, args.toArray());
    }

    private List<RecentOperVo> recentOpers(Long tenantId) {
        return jdbcTemplate.query(
                "SELECT user_id, module, action, status, duration_ms, oper_time "
                        + "FROM sys_oper_log WHERE tenant_id = ?"
                        + " ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new RecentOperVo(
                        toLong(rs, "user_id"),
                        rs.getString("module"),
                        rs.getString("action"),
                        toInt(rs, "status"),
                        toLong(rs, "duration_ms"),
                        rs.getTimestamp("oper_time") == null ? null : rs.getTimestamp("oper_time").toLocalDateTime()),
                tenantId, RECENT_OPER_LIMIT);
    }

    private NameValueVo mapNameValue(ResultSet rs, int rowNum) throws SQLException {
        return new NameValueVo(rs.getString("name"), rs.getLong("value"));
    }

    private Long toLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer toInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
