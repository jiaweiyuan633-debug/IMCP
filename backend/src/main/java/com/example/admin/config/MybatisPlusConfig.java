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
            "form_instance");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
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
                return !TENANT_TABLES.contains(tableName.toLowerCase());
            }
        }));
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor(dataPermissionRuleResolver::getObject));
        return interceptor;
    }
}

