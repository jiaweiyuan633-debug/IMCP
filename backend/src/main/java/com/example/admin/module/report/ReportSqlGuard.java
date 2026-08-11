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
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
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
        // 表/函数审计：触发一次全树遍历（visit(Table)/visit(Function) 重写点做检查）。
        // Select 同时实现 Statement 与 Expression，需强转以消除 getTables 重载歧义。
        new TableAuditor().getTables((Statement) select);
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

    /** 全树遍历审计表与函数：重写 visit(Table)/visit(Function) 做检查，其余交给父类。 */
    private static final class TableAuditor extends TablesNamesFinder {

        @Override
        public void visit(Table table) {
            auditTable(table);
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
}
