package cn.admin.scaffold.module.report;

import cn.admin.scaffold.AbstractIntegrationTest;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.report.dto.ReportDefinitionQuery;
import cn.admin.scaffold.module.report.dto.ReportDefinitionSaveRequest;
import cn.admin.scaffold.module.report.dto.ReportExecuteRequest;
import cn.admin.scaffold.module.report.vo.ReportDefinitionVo;
import cn.admin.scaffold.module.report.vo.ReportExecuteResultVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 报表定义服务集成测试：验证 MyBatis-Plus 租户拦截器 + 唯一编码校验 + 只读执行引擎在真实 MySQL 上生效。
 */
class ReportDefinitionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private ReportDefinitionService reportDefinitionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createPersistsAndListable() {
        Long id = reportDefinitionService.create(request("IT-REP-001", "集成测试报表", "SELECT 1 AS one"));

        assertThat(id).isNotNull();
        ReportDefinitionQuery query = new ReportDefinitionQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setCode("IT-REP-001");
        PageResult<ReportDefinitionVo> result = reportDefinitionService.page(query);
        assertThat(result.getRecords()).extracting(ReportDefinitionVo::getCode).contains("IT-REP-001");
    }

    @Test
    void duplicateCodeRejectedWithinTenant() {
        reportDefinitionService.create(request("IT-REP-002", "报表A", "SELECT 1 AS one"));

        assertThatThrownBy(() -> reportDefinitionService.create(request("IT-REP-002", "报表B", "SELECT 2 AS two")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_CODE_EXISTS.getMessage());
    }

    @Test
    void updateAndDetailRoundTrip() {
        Long id = reportDefinitionService.create(request("IT-REP-003", "原始报表", "SELECT 1 AS one"));

        ReportDefinitionSaveRequest update = request("IT-REP-003", "更新后的报表", "SELECT 2 AS two");
        update.setId(id);
        reportDefinitionService.update(update);

        ReportDefinitionVo detail = reportDefinitionService.detail(id);
        assertThat(detail.getName()).isEqualTo("更新后的报表");
        assertThat(detail.getDataSource()).isEqualTo("SELECT 2 AS two");
    }

    @Test
    void deleteIsLogicalAndDetailNotFound() {
        Long id = reportDefinitionService.create(request("IT-REP-004", "待删除报表", "SELECT 1 AS one"));

        reportDefinitionService.delete(id);

        assertThatThrownBy(() -> reportDefinitionService.detail(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void executeRunsReadOnlyQueryAndReturnsColumnsAndRows() {
        Long id = reportDefinitionService.create(
                request("IT-REP-005", "执行报表", "SELECT :val AS result, :other AS second"));

        ReportExecuteRequest executeRequest = new ReportExecuteRequest();
        executeRequest.setParams(Map.of("val", "hello", "other", 42));
        ReportExecuteResultVo result = reportDefinitionService.execute(id, executeRequest);

        assertThat(result.getColumns()).contains("result", "second");
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).get("result")).isEqualTo("hello");
        assertThat(((Number) result.getRows().get(0).get("second")).intValue()).isEqualTo(42);
    }

    @Test
    void executeRejectsWriteStatementEvenIfStoredDirectly() {
        Long id = reportDefinitionService.create(request("IT-REP-006", "注入报表", "SELECT 1 AS one"));
        // 直接改库绕过保存校验，验证执行期只读拦截仍生效
        jdbcTemplate.update("UPDATE report_definition SET data_source = ? WHERE id = ?",
                "DELETE FROM sys_device WHERE id = 1", id);

        ReportExecuteRequest executeRequest = new ReportExecuteRequest();
        executeRequest.setParams(Map.of());
        assertThatThrownBy(() -> reportDefinitionService.execute(id, executeRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @Test
    void tenantIsolationKeepsRecordsApart() {
        TenantContext.setTenantId(1L);
        reportDefinitionService.create(request("IT-REP-100", "租户1报表", "SELECT 1 AS one"));

        TenantContext.setTenantId(2L);
        // 另一租户可用同编码（唯一性按租户隔离）
        reportDefinitionService.create(request("IT-REP-100", "租户2报表", "SELECT 1 AS one"));

        TenantContext.setTenantId(2L);
        ReportDefinitionQuery query = new ReportDefinitionQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        // 租户2 只看到自己的记录
        assertThat(reportDefinitionService.page(query).getRecords())
                .extracting(ReportDefinitionVo::getName)
                .containsExactly("租户2报表");
    }

    // ---------- 批次7（R4-1.53）：报表执行引擎真实 MySQL 补强测试 ----------

    @Test
    void executeRejectsSensitiveColumnQuery() {
        // 报表查询命中凭据列（sys_user.password）必须拒绝——先建合法报表再直改库，
        // 验证执行期守卫拦截（保存期校验同样会拦，但这里验证执行路径的纵深）
        Long id = reportDefinitionService.create(request("IT-REP-200", "敏感列报表", "SELECT 1 AS one"));
        jdbcTemplate.update("UPDATE report_definition SET data_source = ? WHERE id = ?",
                "SELECT password FROM sys_user", id);

        ReportExecuteRequest executeRequest = new ReportExecuteRequest();
        executeRequest.setParams(Map.of());
        assertThatThrownBy(() -> reportDefinitionService.execute(id, executeRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @Test
    void executeCapsResultRowsToConfiguredLimit() {
        // 批量插入验证 LIMIT 封顶：guard 追加/收紧 app.report.max-rows（默认 5000，IT 用默认配置）
        TenantContext.setTenantId(1L);
        jdbcTemplate.update("INSERT INTO sys_device (tenant_id, device_code, device_name, status) VALUES (1, 'IT-DEV-700', 'cap测试', 1)");
        for (int i = 1; i <= 3; i++) {
            jdbcTemplate.update("INSERT INTO sys_device (tenant_id, device_code, device_name, status) VALUES (1, ?, 'cap测试', 1)",
                    "IT-DEV-70" + i);
        }
        Long id = reportDefinitionService.create(
                request("IT-REP-202", "行数封顶", "SELECT id FROM sys_device WHERE device_name = 'cap测试'"));
        // 直接改库写入超大 LIMIT，验证 guard 收紧到配置上限
        jdbcTemplate.update("UPDATE report_definition SET data_source = ? WHERE id = ?",
                "SELECT id FROM sys_device WHERE device_name = 'cap测试' LIMIT 1000000", id);

        ReportExecuteRequest executeRequest = new ReportExecuteRequest();
        executeRequest.setParams(Map.of());
        ReportExecuteResultVo result = reportDefinitionService.execute(id, executeRequest);

        // 存在上限配置（默认 5000）且实际只有 4 行——断言结果不超上限且能取到数据
        assertThat(result.getRows().size()).isLessThanOrEqualTo(5000);
        assertThat(result.getRows().size()).isGreaterThanOrEqualTo(1);
        // 清理测试数据
        jdbcTemplate.update("DELETE FROM sys_device WHERE device_code LIKE 'IT-DEV-70%'");
    }

    @Test
    void executeRejectsMultiStatementInjection() {
        // 多语句注入：即使绕过保存校验直改库，执行期守卫必须拒绝（防拼接写库）
        Long id = reportDefinitionService.create(request("IT-REP-203", "多语句", "SELECT 1 AS one"));
        jdbcTemplate.update("UPDATE report_definition SET data_source = ? WHERE id = ?",
                "SELECT 1; DROP TABLE sys_device", id);

        ReportExecuteRequest executeRequest = new ReportExecuteRequest();
        executeRequest.setParams(Map.of());
        assertThatThrownBy(() -> reportDefinitionService.execute(id, executeRequest))
                .isInstanceOf(BusinessException.class);
    }

    private ReportDefinitionSaveRequest request(String code, String name, String dataSource) {
        ReportDefinitionSaveRequest request = new ReportDefinitionSaveRequest();
        request.setCode(code);
        request.setName(name);
        request.setCategory("IT");
        request.setDataSource(dataSource);
        request.setStatus(1);
        return request;
    }
}
