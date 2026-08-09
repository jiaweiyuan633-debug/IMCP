package com.example.admin.common;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
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
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DataScopeInnerInterceptor implements InnerInterceptor {

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
                return null;
            }
            Expression where = plainSelect.getWhere();
            for (Table table : tables(plainSelect)) {
                String tableName = table.getName().toLowerCase();
                if (!filter.tables().contains(tableName)) {
                    continue;
                }
                Expression condition = buildCondition(table, tableName, filter);
                if (condition != null) {
                    where = where == null ? condition : new AndExpression(where, condition);
                }
            }
            if (where != null) {
                plainSelect.setWhere(where);
                return select.toString();
            }
        } catch (JSQLParserException | RuntimeException exception) {
            log.warn("Data scope rewrite failed for sql: {}", sql, exception);
        }
        return null;
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
        String prefix = table.getAlias() != null ? table.getAlias().getName() : tableName;
        if (filter.empty()) {
            return new EqualsTo(new Column(prefix + ".id"), new LongValue(-1));
        }
        String column;
        List<Expression> values;
        if ("sys_user".equals(tableName)) {
            column = "id";
            values = filter.userIds().stream().map(LongValue::new).map(expression -> (Expression) expression).toList();
        } else if ("ai_task".equals(tableName)) {
            column = "created_by";
            values = filter.userIds().stream().map(LongValue::new).map(expression -> (Expression) expression).toList();
        } else if ("sys_oper_log".equals(tableName)) {
            column = "user_id";
            values = filter.userIds().stream().map(LongValue::new).map(expression -> (Expression) expression).toList();
        } else if ("sys_login_log".equals(tableName)) {
            column = "username";
            values = filter.usernames().stream().map(StringValue::new).map(expression -> (Expression) expression).toList();
        } else {
            return null;
        }
        if (values.isEmpty()) {
            return new EqualsTo(new Column(prefix + ".id"), new LongValue(-1));
        }
        return new InExpression(new Column(prefix + "." + column), new ParenthesedExpressionList<>(values));
    }
}
