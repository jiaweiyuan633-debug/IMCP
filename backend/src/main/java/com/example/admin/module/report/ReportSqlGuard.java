package com.example.admin.module.report;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.config.MybatisPlusConfig;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 报表执行引擎安全守卫（批次4 修复 W1）。
 *
 * <p>原实现 {@code validateReadOnlySql} 只做"以 SELECT 开头 + 禁止分号/写关键字"的黑名单校验：
 * <ul>
 *   <li>JdbcTemplate 直查绕过 MyBatis-Plus 租户拦截器 → 跨租户读取；</li>
 *   <li>黑名单可被字符串字面量绕过（如 {@code WHERE name = 'DELETE'}），且不拦截
 *       {@code INTO OUTFILE}/{@code LOAD_FILE}/系统库访问/锁子句，存在 DoS 与文件读取后门。</li>
 * </ul>
 *
 * <p>本守卫在保存校验与执行期统一应用：
 * <ol>
 *   <li>结构性只读校验：JSqlParser 真实解析，仅接受单条 SELECT；
 *       {@code INTO OUTFILE}/{@code LOCK IN SHARE MODE} 等无法解析的表单在解析期即被拒绝。</li>
 *   <li>表级白名单：只允许访问租户表（见 {@link MybatisPlusConfig#TENANT_TABLES}），
 *       任何系统库 schema 前缀或非租户表引用一律拒绝，杜绝跨租户/元数据库读取。</li>
 *   <li>危险函数拦截：{@code SLEEP}/{@code BENCHMARK}/{@code GET_LOCK}/{@code LOAD_FILE} 等
 *       DoS 与文件读取向量。</li>
 *   <li>敏感列拦截：凭据列（sys_user.password / totp_secret、ai_service_config.api_key、
 *       sys_channel_config.config_json、sys_config.config_value 等）无论 SELECT/WHERE/GROUP BY/
 *       ORDER BY/子查询/函数参数一律拒绝；敏感表上的 {@code SELECT *} / {@code t.*} 同样拒绝
 *       （通配会展开出凭据列）。按表精确定位，不误伤 import_export_template.config_json 等普通业务列。</li>
 *   <li>锁子句拦截：{@code FOR UPDATE}/{@code FOR SHARE}/{@code NOWAIT} 等 JSqlParser 无法建模
 *       但会原样透传到执行 SQL 的锁子句（对原始 SQL 做字符串/注释掩码后扫描）。</li>
 *   <li>租户注入：复用 MyBatis-Plus {@link TenantLineInnerInterceptor}，为查询涉及的每张租户表
 *       注入 {@code tenant_id} 条件（JOIN 用别名限定、子查询/UNION/派生表递归处理），
 *       与主数据源的租户语义完全一致。</li>
 *   <li>行数上限：追加/收紧 {@code LIMIT}（{@code app.report.max-rows}，默认 5000），
 *       防止单次查询拉爆结果集。</li>
 * </ol>
 */
@Component
public class ReportSqlGuard {

    /** 报表允许访问的表：仅租户表。每张表执行时都会注入 tenant_id，天然保证多租户隔离。 */
    private static final Set<String> ALLOWED_TABLES = MybatisPlusConfig.TENANT_TABLES;

    /** MySQL 系统/信息 schema，任何表引用带这些 schema 前缀一律拒绝。 */
    private static final Set<String> FORBIDDEN_SCHEMAS = Set.of(
            "mysql", "information_schema", "performance_schema", "sys");

    /** 危险/耗资源函数：可被单条 SQL 拖垮实例的 DoS 向量或文件读取后门。 */
    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
            "SLEEP", "BENCHMARK", "GET_LOCK", "RELEASE_LOCK", "IS_FREE_LOCK", "IS_USED_LOCK",
            "LOAD_FILE", "MASTER_POS_WAIT");

    /** 字符串/注释掩码后仍需拦截的锁子句与写文件关键字（FOR UPDATE 等无法从 AST 建模）。 */
    private static final Pattern FORBIDDEN_CLAUSE_PATTERN = Pattern.compile(
            "\\bFOR\\s+UPDATE\\b|\\bFOR\\s+SHARE\\b|\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b"
                    + "|\\bNOWAIT\\b|\\bSKIP\\s+LOCKED\\b|\\bOUTFILE\\b|\\bDUMPFILE\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 敏感列（表 → 列，表名小写、列名原样）：报表查询命中任一列引用即拒绝，杜绝凭据列外泄。
     * 仅收录「当前在租户白名单内且可能承载凭据」的表，避免误伤普通业务列
     * （如 import_export_template.config_json 是列映射配置而非渠道密钥，不拦截）：
     * 用户凭据（sys_user.password / totp_secret）、系统参数值（sys_config.config_value，
     * 生产可能含 SMTP/AI 等密钥）、消息渠道密钥 JSON（sys_channel_config.config_json）、
     * AI 网关密钥（ai_service_config.api_key）。
     */
    private static final Map<String, Set<String>> SENSITIVE_TABLE_COLUMNS = Map.ofEntries(
            Map.entry("sys_user", Set.of("password", "totp_secret")),
            Map.entry("sys_config", Set.of("config_value")),
            Map.entry("sys_channel_config", Set.of("config_json")),
            Map.entry("ai_service_config", Set.of("api_key")));

    /** 解析失败/结构校验失败的统一拒绝异常。 */
    private static BusinessException invalidSql() {
        return new BusinessException(ResultCode.REPORT_SQL_INVALID);
    }

    private final int maxRows;

    /** 租户条件注入器：继承拦截器以访问 protected {@code processSelect}，逻辑与主数据源一致。 */
    private final TenantInjector tenantInjector;

    public ReportSqlGuard(@Value("${app.report.max-rows:5000}") int maxRows) {
        this.maxRows = maxRows;
        this.tenantInjector = new TenantInjector();
    }

    /** 创建/编辑校验：仅做结构性只读检查，不注入租户与上限（执行期才注入，保证租户动态可变）。 */
    public void validate(String sql) {
        assertReadOnly(parseSelect(sql), sql);
    }

    /** 执行前处理：校验只读 → 注入租户条件 → 收紧行数上限，返回可直接执行的 SQL。 */
    public String guard(String sql) {
        Select select = parseSelect(sql);
        assertReadOnly(select, sql);
        tenantInjector.inject(select);
        capLimit(select);
        return select.toString();
    }

    /** 解析为单条 SELECT；多语句、非 SELECT、解析失败一律拒绝。 */
    private Select parseSelect(String sql) {
        Statement statement;
        try {
            statement = JsqlParserGlobal.parse(sql);
        } catch (JSQLParserException e) {
            throw invalidSql();
        }
        if (!(statement instanceof Select select)) {
            throw invalidSql();
        }
        return select;
    }

    private void assertReadOnly(Select select, String rawSql) {
        // 锁/写文件关键字：AST 无法建模 FOR UPDATE，对原串做字符串/注释掩码后扫描。
        if (FORBIDDEN_CLAUSE_PATTERN.matcher(maskLiteralsAndComments(rawSql)).find()) {
            throw invalidSql();
        }
        // 表/函数/敏感列审计 + 通配符记录：触发一次全树遍历（visit(Table)/visit(Function)/visit(Column) 重写点做检查）。
        // Select 同时实现 Statement 与 Expression，需强转以消除 getTables 重载歧义。
        TableAuditor auditor = new TableAuditor();
        auditor.getTables((Statement) select);
        // 未限定敏感列与 SELECT * / t.* 需在表集合齐全后判定（selectItems 先于 fromItem 遍历），遍历结束统一校验。
        auditor.assertNoSensitiveColumnOrWildcard();
    }

    /** 收紧行数上限：已有 LIMIT 超过上限则压回，缺失或动态值（LIMIT :param）则统一设为硬上限。 */
    private void capLimit(Select select) {
        Limit limit = select.getLimit();
        if (limit == null) {
            Limit cap = new Limit();
            cap.setRowCount(new LongValue(maxRows));
            select.setLimit(cap);
            return;
        }
        Expression rowCount = limit.getRowCount();
        if (rowCount instanceof LongValue longValue) {
            if (longValue.getValue() > maxRows) {
                limit.setRowCount(new LongValue(maxRows));
            }
        } else {
            // LIMIT ? / LIMIT :param：无法静态判定值，直接替换为硬上限（原绑定参数随之失效，无残留 ?）
            limit.setRowCount(new LongValue(maxRows));
        }
    }

    /**
     * 对 SQL 做字符串字面量 + 注释掩码（引号内内容替换为 x，注释替换为空格），
     * 使 FORBIDDEN_CLAUSE_PATTERN 不会命中字面量/注释中的无害文本。
     */
    private static String maskLiteralsAndComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        char quote = 0; // ' " `，0 表示不在引号内
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (quote != 0) {
                out.append('x');
                if (c == '\\' && quote == '\'') {
                    i += 2; // MySQL 字符串反斜杠转义，跳过转义符与被转义字符
                    continue;
                }
                if (c == quote) {
                    if (quote == '\'' && i + 1 < n && sql.charAt(i + 1) == '\'') {
                        i += 2; // '' 是转义的单引号，仍在字符串内
                        continue;
                    }
                    quote = 0;
                }
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                out.append('x');
                i++;
                continue;
            }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                out.append("--");
                i += 2;
                while (i < n && sql.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '#') {
                while (i < n && sql.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                out.append("/*");
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, n);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 继承 MyBatis-Plus 租户拦截器以调用 protected {@code processSelect}，做租户条件注入。
     * 表名单与 MybatisPlusConfig 完全一致：非租户表不注入（但守卫的白名单已保证报表只能查租户表）。
     */
    private static final class TenantInjector extends TenantLineInnerInterceptor {

        TenantInjector() {
            super(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    return new LongValue(TenantContext.getTenantId());
                }

                @Override
                public String getTenantIdColumn() {
                    return "tenant_id";
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    return !ALLOWED_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
                }
            });
        }

        void inject(Select select) {
            processSelect(select, 0, null, null);
        }
    }

    /**
     * 全树遍历审计表、函数、敏感列与通配符：visit(Table)/visit(Function) 即时拒绝非法表与危险函数；
     * 敏感列分两路——可解析表限定的即时查表拦截，未限定列延后按「查询内敏感列并集」判定；
     * 通配符（SELECT * / t.*）因 selectItems 先于 fromItem 遍历，仅记录、遍历结束统一校验。
     */
    private static final class TableAuditor extends TablesNamesFinder {

        /** 列引用记录：限定列（qualifier 为小写，null 表示未限定）＋ 列名（大写）。 */
        private static final class ColumnRef {
            private final String qualifier;
            private final String name;

            ColumnRef(String qualifier, String name) {
                this.qualifier = qualifier;
                this.name = name;
            }
        }

        /** 本次查询涉及的表（小写）：供敏感列并集与通配符判定。 */
        private final Set<String> tablesInQuery = new HashSet<>();

        /** 表别名（小写）→ 真实表名（小写）：解析限定列/限定通配符所属表。 */
        private final Map<String, String> tableAliases = new HashMap<>();

        /**
         * 全部列引用：限定列是否命中按其所属表判定，未限定列按「查询内敏感列并集」判定。
         * 全部延后到遍历结束后解析——selectItems 先于 fromItem 被访问，列出现时别名映射尚未建立。
         */
        private final List<ColumnRef> columns = new ArrayList<>();

        /** SELECT *（不含限定）：遍历结束后判定是否落在敏感表上。 */
        private boolean bareWildcard;

        /** t.* / schema.t.* 的限定表（小写，可能为别名）：遍历结束后解析判定。 */
        private final List<String> wildcardTables = new ArrayList<>();

        @Override
        public void visit(Table table) {
            auditTable(table);
            String name = table.getName().toLowerCase(Locale.ROOT);
            tablesInQuery.add(name);
            if (table.getAlias() != null && table.getAlias().getName() != null) {
                tableAliases.put(table.getAlias().getName().toLowerCase(Locale.ROOT), name);
            }
            super.visit(table);
        }

        @Override
        public void visit(Function function) {
            if (function.getName() != null
                    && FORBIDDEN_FUNCTIONS.contains(function.getName().toUpperCase(Locale.ROOT))) {
                throw invalidSql();
            }
            super.visit(function);
        }

        @Override
        public void visit(Column column) {
            if (column.getColumnName() != null) {
                Table qualifier = column.getTable();
                String qualifierName = (qualifier != null && qualifier.getName() != null)
                        ? qualifier.getName().toLowerCase(Locale.ROOT) : null;
                columns.add(new ColumnRef(qualifierName, column.getColumnName().toUpperCase(Locale.ROOT)));
            }
            super.visit(column);
        }

        @Override
        public void visit(AllColumns allColumns) {
            bareWildcard = true;
            super.visit(allColumns);
        }

        @Override
        public void visit(AllTableColumns allTableColumns) {
            Table table = allTableColumns.getTable();
            if (table != null && table.getName() != null) {
                wildcardTables.add(table.getName().toLowerCase(Locale.ROOT));
            }
            super.visit(allTableColumns);
        }

        /** 遍历结束后统一校验：敏感列（限定/未限定）＋ 敏感表上的通配符（SELECT * / t.* 会带出凭据列）。 */
        void assertNoSensitiveColumnOrWildcard() {
            Set<String> sensitiveColumns = querySensitiveColumns();
            for (ColumnRef ref : columns) {
                if (ref.qualifier == null) {
                    // 未限定列：查询内任一敏感表含此列即拒绝（保守判定，误伤仅发生在同名列的歧义 JOIN）
                    if (sensitiveColumns.contains(ref.name)) {
                        throw invalidSql();
                    }
                } else {
                    // 限定列：解析别名/真名后仅校验该表自己的敏感列，不误伤 import_export_template.config_json 等
                    String table = tableAliases.getOrDefault(ref.qualifier, ref.qualifier);
                    if (isSensitiveColumn(table, ref.name)) {
                        throw invalidSql();
                    }
                }
            }
            if (bareWildcard && tablesInQuery.stream().anyMatch(SENSITIVE_TABLE_COLUMNS::containsKey)) {
                throw invalidSql();
            }
            for (String qualifier : wildcardTables) {
                if (SENSITIVE_TABLE_COLUMNS.containsKey(tableAliases.getOrDefault(qualifier, qualifier))) {
                    throw invalidSql();
                }
            }
        }

        /** 查询内全部表的敏感列并集（大写）：未限定列命中即拒绝。 */
        private Set<String> querySensitiveColumns() {
            Set<String> names = new HashSet<>();
            for (String table : tablesInQuery) {
                Set<String> columns = SENSITIVE_TABLE_COLUMNS.get(table);
                if (columns != null) {
                    columns.forEach(c -> names.add(c.toUpperCase(Locale.ROOT)));
                }
            }
            return names;
        }

        private void auditTable(Table table) {
            // 任何显式 schema 限定（mysql./sys./information_schema./自定义 schema）一律拒绝，
            // 报表只允许访问默认 schema 的租户表。
            if (table.getSchemaName() != null) {
                throw invalidSql();
            }
            String name = table.getName();
            if (name == null || !ALLOWED_TABLES.contains(name.toLowerCase(Locale.ROOT))) {
                throw invalidSql();
            }
        }
    }

    /** 指定表是否包含该敏感列（表名/列名均不区分大小写）。 */
    private static boolean isSensitiveColumn(String tableLower, String columnName) {
        Set<String> columns = SENSITIVE_TABLE_COLUMNS.get(tableLower);
        return columns != null && columns.contains(columnName.toLowerCase(Locale.ROOT));
    }
}
