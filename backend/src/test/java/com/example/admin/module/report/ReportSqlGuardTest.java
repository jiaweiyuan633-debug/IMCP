package com.example.admin.module.report;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 报表 SQL 守卫单元测试（批次4 修复 W1）：
 * 覆盖结构性只读校验、表级白名单、危险函数、锁子句、租户注入与行数上限的全部绕过向量。
 */
class ReportSqlGuardTest {

    /** 行数上限调小便于断言收紧逻辑。 */
    private static final int MAX_ROWS = 5;

    private ReportSqlGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReportSqlGuard(MAX_ROWS);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BusinessException sqlInvalid() {
        return new BusinessException(ResultCode.REPORT_SQL_INVALID);
    }

    // ---------- 合法查询 ----------

    @Test
    void acceptsPlainTenantSelectAndInjectsTenant() {
        String sql = guard.guard("SELECT * FROM sys_device WHERE id = 1");

        assertThat(sql).contains("tenant_id = 1").contains("LIMIT 5");
    }

    @Test
    void injectsTenantIntoJoinWithAliases() {
        String sql = guard.guard("SELECT d.name FROM sys_device d "
                + "JOIN device_telemetry t ON d.id = t.device_id WHERE t.occurred_at > ?");

        assertThat(sql).contains("AND d.tenant_id = 1").contains("AND t.tenant_id = 1");
    }

    @Test
    void injectsTenantIntoWhereSubquery() {
        String sql = guard.guard(
                "SELECT * FROM sys_device WHERE id IN (SELECT device_id FROM device_telemetry WHERE t > 1)");

        assertThat(sql).contains("SELECT * FROM sys_device WHERE id IN "
                + "(SELECT device_id FROM device_telemetry WHERE t > 1 AND tenant_id = 1) AND tenant_id = 1");
    }

    @Test
    void injectsTenantIntoUnionBranches() {
        String sql = guard.guard("SELECT * FROM sys_device UNION SELECT * FROM sys_device");

        assertThat(sql).contains("SELECT * FROM sys_device WHERE tenant_id = 1 "
                + "UNION SELECT * FROM sys_device WHERE tenant_id = 1");
    }

    @Test
    void acceptsDerivedTableSubquery() {
        // 派生表别名不应被当作真实表拒掉；内层租户表仍被注入
        String sql = guard.guard("SELECT * FROM (SELECT * FROM sys_device) x");

        assertThat(sql).contains("(SELECT * FROM sys_device WHERE tenant_id = 1) x");
    }

    @Test
    void acceptsQueryWithoutTables() {
        assertThat(guard.guard("SELECT :val AS result")).isEqualTo("SELECT :val AS result LIMIT 5");
    }

    // ---------- LIMIT 上限 ----------

    @Test
    void capsLimitAboveCap() {
        assertThat(guard.guard("SELECT * FROM sys_device LIMIT 1000000")).contains("LIMIT 5");
    }

    @Test
    void keepsLimitUnderCap() {
        assertThat(guard.guard("SELECT * FROM sys_device LIMIT 3")).contains("LIMIT 3");
    }

    @Test
    void replacesDynamicLimitWithCap() {
        // LIMIT :n 无法静态判定值，统一收紧为硬上限，原命名占位随之消失
        assertThat(guard.guard("SELECT * FROM sys_device LIMIT :n"))
                .contains("LIMIT 5").doesNotContain(":n");
    }

    // ---------- 结构性拒绝 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM sys_device INTO OUTFILE '/tmp/x'",
            "SELECT * FROM sys_device LOCK IN SHARE MODE",
            "SELECT 1; DROP TABLE sys_device",
            "DELETE FROM sys_device WHERE id = 1",
            "UPDATE sys_device SET name = 'x'",
            "DROP TABLE sys_device",
    })
    void rejectsNonReadOnlyShapes(String sql) {
        assertThatThrownBy(() -> guard.guard(sql)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT LOAD_FILE('/etc/passwd')",
            "SELECT SLEEP(5)",
            "SELECT BENCHMARK(10000000, MD5('a'))",
            "SELECT GET_LOCK('x', 10)",
            "SELECT RELEASE_LOCK('x')",
            "SELECT * FROM sys_device WHERE SLEEP(5) = 1",
            "SELECT * FROM sys_device HAVING BENCHMARK(1, 2)",
    })
    void rejectsDangerousFunctions(String sql) {
        assertThatThrownBy(() -> guard.guard(sql)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM mysql.user",
            "SELECT * FROM information_schema.tables",
            "SELECT * FROM performance_schema.events_statements_history",
            "SELECT * FROM sys.sys_config",
    })
    void rejectsSystemSchemaTables(String sql) {
        assertThatThrownBy(() -> guard.guard(sql)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNonTenantTable() {
        // sys_menu 非租户表，报表一律不允许访问
        assertThatThrownBy(() -> guard.guard("SELECT * FROM sys_menu"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM sys_device FOR UPDATE",
            "SELECT * FROM sys_device FOR SHARE",
            "SELECT * FROM sys_device WHERE id = 1 FOR UPDATE NOWAIT",
    })
    void rejectsLockingClauses(String sql) {
        assertThatThrownBy(() -> guard.guard(sql))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    // ---------- 字面量/注释不误伤 ----------

    @Test
    void acceptsStringLiteralContainingWriteKeyword() {
        // 旧黑名单会误拒；解析器 + 掩码保证字面量中的文本不触发拒绝
        assertThatCode(() -> guard.guard("SELECT name FROM sys_device WHERE remark = 'DELETE'"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsStringLiteralContainingLockKeyword() {
        assertThatCode(() -> guard.guard("SELECT name FROM sys_device WHERE remark = 'FOR UPDATE'"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsCommentContainingLockKeyword() {
        // -- 行注释中的文本被掩码，不误伤；注释本身由解析器安全处理
        assertThatCode(() -> guard.guard("SELECT * FROM sys_device -- FOR UPDATE"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsLockKeywordOutsideCommentOrLiteral() {
        assertThatThrownBy(() -> guard.guard("SELECT * FROM sys_device FOR UPDATE"))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 保存期校验（validate）----------

    @Test
    void validateAcceptsReadOnlySelect() {
        assertThatCode(() -> guard.validate("SELECT id, name FROM sys_device WHERE status = 1"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRejectsLockingClause() {
        assertThatThrownBy(() -> guard.validate("SELECT * FROM sys_device FOR UPDATE"))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 敏感列 / 通配符（R1-1.2 列级黑名单）----------

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT password FROM sys_user",
            "SELECT totp_secret FROM sys_user WHERE id = 1",
            "SELECT u.password FROM sys_user u WHERE u.id = 1",
            "SELECT u.totp_secret FROM sys_user u JOIN sys_device d ON d.id = u.id",
            // R4-1.38：手机号/邮箱为 PII 列，报表读取同样禁止
            "SELECT phone FROM sys_user",
            "SELECT email FROM sys_user WHERE id = 1",
            "SELECT u.phone, u.email FROM sys_user u",
            "SELECT api_key FROM ai_service_config WHERE enabled = 1",
            "SELECT config_value FROM sys_config WHERE config_key = 'smtp.password'",
            "SELECT config_json FROM sys_channel_config WHERE channel_type = 'MAIL'",
            "SELECT id, password FROM sys_user ORDER BY password",
            "SELECT (SELECT totp_secret FROM sys_user WHERE id = 1) AS s",
            "SELECT CONCAT('x', api_key) FROM ai_service_config",
    })
    void rejectsSensitiveColumns(String sql) {
        assertThatThrownBy(() -> guard.guard(sql)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM sys_user",
            "SELECT * FROM sys_user u WHERE u.status = 1",
            "SELECT u.* FROM sys_user u",
            "SELECT * FROM (SELECT * FROM sys_user) x",
    })
    void rejectsWildcardExpandingSensitiveColumns(String sql) {
        assertThatThrownBy(() -> guard.guard(sql)).isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsWildcardOnBenignTable() {
        assertThat(guard.guard("SELECT * FROM sys_device")).contains("tenant_id = 1").contains("LIMIT 5");
    }

    @Test
    void allowsBenignColumnsOfSensitiveTable() {
        // sys_user 可查非敏感列：白名单表仍可正常做报表，仅凭据列被拦
        assertThatCode(() -> guard.guard("SELECT username, nickname, status FROM sys_user WHERE id = 1"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsConfigJsonOnBenignImportExportTemplate() {
        // import_export_template.config_json 是列映射配置（非渠道密钥），按表精确判定不误伤
        assertThatCode(() -> guard.guard("SELECT config_json FROM import_export_template"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsQualifiedBenignColumnEvenWhenSensitiveTableJoined() {
        // 精确到表：即便 JOIN 了敏感表 sys_channel_config，限定到 import_export_template 的 config_json 仍放行
        assertThatCode(() -> guard.guard(
                "SELECT t.config_json FROM import_export_template t "
                        + "JOIN sys_channel_config c ON c.id = t.created_by"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRejectsSensitiveColumnOnSave() {
        assertThatThrownBy(() -> guard.validate("SELECT password FROM sys_user"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }
}
