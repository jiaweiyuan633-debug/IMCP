package com.example.admin.module.report;

import com.example.admin.AbstractIntegrationTest;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.report.dto.ReportDefinitionQuery;
import com.example.admin.module.report.dto.ReportDefinitionSaveRequest;
import com.example.admin.module.report.dto.ReportExecuteRequest;
import com.example.admin.module.report.vo.ReportDefinitionVo;
import com.example.admin.module.report.vo.ReportExecuteResultVo;
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
