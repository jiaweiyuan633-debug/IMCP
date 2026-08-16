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
            "sys_field_audit_log",
            // R4-1.17：外部 MCP Server 配置（V41 新增表漏进租户名单——此前跨租户可列出/读写/删除
            // 他租户服务并携带其 authToken 连接调用外部工具，create 还会落到默认租户 1 造成数据污染）
            "sys_mcp_server");

    /**
     * R4-1.29：带 tenant_id 却【有意不在】白名单的四张表——均已由服务层显式隔离，禁止误加：
     * <ul>
     *   <li>sys_oauth_client：SSO 授权码链路按 client_id 全局唯一查询（匿名端点无租户上下文，
     *       注入默认 tenant_id=1 会挡住租户 2 应用认证）；OauthClientService 已显式按租户
     *       page/create/校验归属，并有 OauthClientServiceTest 覆盖。</li>
     *   <li>sys_oauth_config：平台级配置（仅租户 1 管理员可管理），OauthConfigService 入口即
     *       FORBIDDEN 守卫；OauthLoginService 匿名链路按 provider 查询，加白名单会破坏三方登录。</li>
     *   <li>sys_user_oauth：仅在 OauthLoginService 内读写且全部显式携带 tenantId（匿名绑定端点
     *       以绑定凭证租户为准）；加白名单后 INSERT 会被拦截器强制覆盖为默认租户 1，破坏租户 2 绑定。</li>
     *   <li>screen_template：tenant_id IS NULL 表示内置全局模板（全租户可见），非空为租户自定义；
     *       ScreenTemplateService 已用 (tenantId = ?) OR (tenant_id IS NULL) 显式过滤，加白名单会
     *       过滤掉内置模板。</li>
     * </ul>
     * 其余全部带 tenant_id 的业务表均已纳入上方名单，无遗漏。
     */

    /** 租户列名：租户拦截器注入条件与字段审计直查过滤共用同源，避免硬编码两处分叉。 */
    public static final String TENANT_ID_COLUMN = "tenant_id";

    /** 分页 pageSize 全局硬上限：客户端可传任意 pageSize 直达 SQL LIMIT，封顶防全表分页/OOM（R4-1.39）。 */
    public static final long MAX_PAGE_SIZE = 200L;

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
        // R4-1.39：分页 pageSize 全局封顶 200——此前客户端可传任意 pageSize 直达 SQL LIMIT，
        // 全部 37 处分页入口无上界，?pageSize=100000000 可触发全表扫描拖垮 DB。
        // 3.5.7 setMaxLimit 返回 void，先建分页拦截器再封顶
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}

