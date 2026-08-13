package com.example.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.example.admin.common.DataScopeInnerInterceptor;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.DataPermissionRuleResolver;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    /**
     * 数据权限规则解析器。用 ObjectProvider 懒取：resolver 依赖 mapper，mapper 依赖
     * SqlSessionFactory，而 SqlSessionFactory 依赖本拦截器——若直接注入会构成 Bean 循环依赖，
     * 拦截器首次查询时才解析真实实例。
     */
    private final ObjectProvider<DataPermissionRuleResolver> dataPermissionRuleResolver;

    public MybatisPlusConfig(ObjectProvider<DataPermissionRuleResolver> dataPermissionRuleResolver) {
        this.dataPermissionRuleResolver = dataPermissionRuleResolver;
    }

    /** 租户表名单：MyBatis-Plus 租户拦截器按此表注入 tenant_id；报表执行引擎白名单同源复用。 */
    public static final Set<String> TENANT_TABLES = Set.of(
            "sys_user",
            "sys_role",
            "sys_dept",
            "sys_post",
            "sys_dict_type",
            "sys_dict_data",
            "sys_config",
            "sys_file",
            "sys_notice",
            "sys_notice_read",
            "sys_message",
            "sys_message_read",
            "sys_job",
            "sys_job_log",
            "sys_workflow",
            "sys_workflow_log",
            "sys_process_def",
            "sys_process_node",
            "flow_definition",
            "flow_node",
            "flow_skip",
            "flow_instance",
            "flow_task",
            "flow_his_task",
            "flow_user",
            "sys_login_log",
            "sys_oper_log",
            "sys_audit_log",
            "sys_sql_log",
            "sys_alert_rule",
            "ai_service_config",
            "ai_task",
            "ai_task_result",
            "sys_device",
            "sys_channel_config",
            "sys_channel_log",
            // 批次4：报表定义化 / 设备物模型与遥测 / 导入导出中心 / 低代码表单引擎
            "report_definition",
            "device_thing_model",
            "device_telemetry",
            "import_export_template",
            "import_export_job",
            "form_definition",
            "form_instance",
            // 批次5：AI 知识库 / 提示词模板、消息模板、字段级审计日志
            // （R1-1.5 租户白名单补漏：上述表均带 tenant_id 列，此前漏加入名单，
            //  BaseMapper 标准 CRUD 不注入租户条件，租户间存在越权读写风险）
            "ai_knowledge_base",
            "ai_knowledge_doc",
            "ai_prompt_template",
            "sys_message_template",
            "sys_field_audit_log");

    /** 租户列名：租户拦截器注入条件与字段审计直查过滤共用同源，避免硬编码两处分叉。 */
    public static final String TENANT_ID_COLUMN = "tenant_id";

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // R1-1.6 拦截器顺序修正：条件注入类（租户/数据权限）必须在分页之前。
        // 根因：PaginationInnerInterceptor 的 COUNT 查询在 willDoQuery 中直接经真实
        // Executor 执行（不重入拦截器链），COUNT SQL 只取自「当前时刻」的 boundSql。
        // 分页在前时 COUNT 在租户/数据权限条件注入前生成，分页 total 会统计到全租户/越权行。
        // 顺序对齐官方推荐「多租户 → 分页 → 乐观锁」。
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContext.getTenantId());
            }

            @Override
            public String getTenantIdColumn() {
                return TENANT_ID_COLUMN;
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return !TENANT_TABLES.contains(tableName.toLowerCase());
            }
        }));
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor(dataPermissionRuleResolver::getObject));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}

