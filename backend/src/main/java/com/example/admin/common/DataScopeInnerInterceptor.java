package com.example.admin.common;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.example.admin.module.system.DataPermissionRuleResolver;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 行级数据权限 SQL 改写拦截器（批次2b 起按配置生效）。
 *
 * <p>对命中的受控表追加「关联用户列 IN (当前用户可见集合)」条件；受控表及其关联列的映射
 * 不再硬编码，而是由 {@link DataPermissionRuleResolver} 从 sys_data_permission 配置表读取，
 * 新增受控表只需在管理端配置，无需改代码。表未配置任何规则时不施加行级过滤。
 */
@Slf4j
public class DataScopeInnerInterceptor implements InnerInterceptor {

    private final Supplier<DataPermissionRuleResolver> ruleResolverSupplier;
    private DataPermissionRuleResolver ruleResolver;

    public DataScopeInnerInterceptor(DataPermissionRuleResolver ruleResolver) {
        this(() -> ruleResolver);
    }

    /**
     * @param ruleResolverSupplier 懒加载规则解析器：MybatisPlusConfig 构建拦截器时
     *                             resolver 可能尚未就绪（resolver 依赖 mapper，mapper 依赖
     *                             SqlSessionFactory，而 SqlSessionFactory 依赖本拦截器），
     *                             首次查询时才解析真实实例，避免 Bean 循环依赖。
     */
    public DataScopeInnerInterceptor(Supplier<DataPermissionRuleResolver> ruleResolverSupplier) {
        this.ruleResolverSupplier = ruleResolverSupplier;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement mappedStatement, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        DataScopeContext.Filter filter = DataScopeContext.get();
        if (filter == null || !filter.active()) {
            return;
        }
        String original = boundSql.getSql();
        String rewritten = rewrite(original, filter);
        if (rewritten != null && !rewritten.equals(original)) {
            PluginUtils.mpBoundSql(boundSql).sql(rewritten);
        }
    }

    private String rewrite(String sql, DataScopeContext.Filter filter) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            if (!(select.getSelectBody() instanceof PlainSelect plainSelect)) {
                // 批次4（R4-1.50）：fail-closed——非 PlainSelect（UNION/集合查询等）无法安全
                // 注入行级条件，直接拒绝执行，杜绝绕过数据权限
                throw new IllegalStateException("data scope unsupported statement type: " + select.getClass().getSimpleName());
            }
            // 批次4（R4-1.50）：全树收集表（含子查询/派生表内层）——TablesNamesFinder 递归
            // 遍历所有层级，修复此前仅顶层 FROM/JOIN 表被注入、受控表出现在子查询中被绕过的问题。
            // Select 同时实现 Statement 与 Expression，需强转为 Statement 消解 getTables 重载歧义。
            Set<String> tablesInQuery = new TablesNamesFinder().getTables((net.sf.jsqlparser.statement.Statement) select);
            boolean touchesControlled = tablesInQuery.stream().anyMatch(filter.tables()::contains);
            if (!touchesControlled) {
                return null; // 查询不涉及受控表，无行级过滤需要
            }
            Expression where = plainSelect.getWhere();
            boolean injected = false;
            boolean controlledInTopLevel = false;
            for (Table table : tables(plainSelect)) {
                String tableName = table.getName().toLowerCase();
                if (filter.tables().contains(tableName)) {
                    controlledInTopLevel = true;
                    Expression condition = buildCondition(table, tableName, filter);
                    if (condition != null) {
                        where = where == null ? condition : new AndExpression(where, condition);
                        injected = true;
                    }
                }
            }
            if (injected && where != null) {
                plainSelect.setWhere(where);
                return select.toString();
            }
            // 语义边界：受控表出现在「子查询/派生表内层」而顶层未直接引用（controlledInTopLevel=false）
            // 时，行级条件无法在顶层注入——fail-closed 拒绝，杜绝内层受控表绕过（批次4）。
            // 反之顶层涉及受控表但未注入条件，属正常无过滤语义（未配置规则/可见集合为 null），
            // 保持原 SQL（与批次2b 之前行为一致）。
            if (controlledInTopLevel) {
                return null;
            }
            throw new IllegalStateException("data scope controlled table only in subquery, unsupported");
        } catch (JSQLParserException | RuntimeException exception) {
            // 批次4（R4-1.50）：fail-closed——解析/改写失败一律拒绝执行，而非静默放行
            log.error("Data scope rewrite failed for sql (rejecting): {}", sql, exception);
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                    "数据权限改写失败，拒绝执行该查询");
        }
    }

    private List<Table> tables(PlainSelect plainSelect) {
        List<Table> tables = new ArrayList<>(4);
        if (plainSelect.getFromItem() instanceof Table table) {
            tables.add(table);
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getRightItem() instanceof Table table) {
                    tables.add(table);
                }
            }
        }
        return tables;
    }

    private Expression buildCondition(Table table, String tableName, DataScopeContext.Filter filter) {
        DataPermissionRuleResolver.Rule rule = resolver().resolve(tableName);
        if (rule == null) {
            // 未配置数据权限规则的表不受行级过滤
            return null;
        }
        String prefix = table.getAlias() != null ? table.getAlias().getName() : tableName;
        if (filter.empty()) {
            return new EqualsTo(new Column(prefix + ".id"), new LongValue(-1));
        }
        String column;
        List<Expression> values;
        if (rule.usernameColumn() != null) {
            // 配置了用户名列：按当前用户可见的用户名集合过滤
            if (filter.usernames() == null) {
                return null;
            }
            column = rule.usernameColumn();
            values = filter.usernames().stream().map(StringValue::new).map(expression -> (Expression) expression).toList();
        } else if (rule.userColumn() != null) {
            // 配置了用户ID列：按当前用户可见的用户ID集合过滤
            if (filter.userIds() == null) {
                return null;
            }
            column = rule.userColumn();
            values = filter.userIds().stream().map(LongValue::new).map(expression -> (Expression) expression).toList();
        } else {
            return null;
        }
        if (values.isEmpty()) {
            return new EqualsTo(new Column(prefix + ".id"), new LongValue(-1));
        }
        return new InExpression(new Column(prefix + "." + column), new ParenthesedExpressionList<>(values));
    }

    private DataPermissionRuleResolver resolver() {
        DataPermissionRuleResolver resolved = this.ruleResolver;
        if (resolved == null) {
            resolved = this.ruleResolver = ruleResolverSupplier.get();
        }
        return resolved;
    }
}
