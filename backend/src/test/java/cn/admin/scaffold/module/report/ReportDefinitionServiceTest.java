package cn.admin.scaffold.module.report;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.report.dto.ReportDefinitionSaveRequest;
import cn.admin.scaffold.module.report.dto.ReportExecuteRequest;
import cn.admin.scaffold.module.report.entity.ReportDefinitionDO;
import cn.admin.scaffold.module.report.mapper.ReportDefinitionMapper;
import cn.admin.scaffold.module.report.vo.ReportExecuteResultVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * 报表定义服务单元测试：SQL 只读校验、code 唯一性、execute 命名参数绑定。
 */
class ReportDefinitionServiceTest {

    private ReportDefinitionMapper mapper;
    private JdbcTemplate jdbcTemplate;
    private ReportDefinitionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReportDefinitionMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ReportDefinitionService(mapper, jdbcTemplate, new ReportSqlGuard(5000));
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createAcceptsReadOnlySelect() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ReportDefinitionDO.class))).thenAnswer(invocation -> {
            ((ReportDefinitionDO) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        Long id = service.create(request("T-001", "SELECT id, name FROM sys_device WHERE tenant_id = :tid"));

        assertThat(id).isEqualTo(1L);
        verify(mapper).insert(any(ReportDefinitionDO.class));
    }

    @Test
    void createRejectsSqlContainingSemicolon() {
        assertThatThrownBy(() -> service.create(request("T-002",
                "SELECT id FROM sys_device; DROP TABLE sys_device")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @Test
    void createRejectsSqlContainingDrop() {
        assertThatThrownBy(() -> service.create(request("T-003",
                "SELECT id FROM sys_device WHERE name = 'x' DROP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @Test
    void createRejectsNonSelectSql() {
        assertThatThrownBy(() -> service.create(request("T-004",
                "UPDATE sys_device SET name = 'x' WHERE id = 1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @Test
    void createAcceptsCommentMarker() {
        // 解析器级校验取代旧的黑名单：-- 行注释由解析器安全剥离，不再被误拒
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ReportDefinitionDO.class))).thenAnswer(invocation -> {
            ((ReportDefinitionDO) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        Long id = service.create(request("T-005", "SELECT id FROM sys_device -- 说明"));

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void duplicateCodeRejected() {
        ReportDefinitionDO exists = new ReportDefinitionDO();
        exists.setId(10L);
        exists.setCode("T-DUP");
        when(mapper.selectOne(any())).thenReturn(exists);

        assertThatThrownBy(() -> service.create(request("T-DUP", "SELECT 1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_CODE_EXISTS.getMessage());
    }

    @Test
    void executeBindsNamedParamsToQuestionMarks() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(1L);
        definition.setStatus(1);
        definition.setDataSource(
                "SELECT device_code FROM sys_device WHERE device_type = :type AND status = :status");
        when(mapper.selectById(1L)).thenReturn(definition);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("device_code", "DEV-001")));

        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(Map.of("type", "IT", "status", 1));
        ReportExecuteResultVo result = service.execute(1L, request);

        // 执行期守卫注入 tenant_id 并追加行数上限（JdbcTemplate 直查绕过了 MyBatis 租户拦截器）
        verify(jdbcTemplate).queryForList(
                "SELECT device_code FROM sys_device WHERE device_type = ? AND status = ? "
                        + "AND tenant_id = 1 LIMIT 5000", "IT", 1);
        assertThat(result.getColumns()).contains("device_code");
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).get("device_code")).isEqualTo("DEV-001");
    }

    @Test
    void executeRejectsMissingRequiredParam() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(2L);
        definition.setStatus(1);
        definition.setDataSource("SELECT * FROM sys_device WHERE device_type = :type");
        when(mapper.selectById(2L)).thenReturn(definition);

        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(Map.of());

        assertThatThrownBy(() -> service.execute(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    void executeRejectsWriteStatementFromStoredDefinition() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(3L);
        definition.setStatus(1);
        definition.setDataSource("DELETE FROM sys_device WHERE id = 1");
        when(mapper.selectById(3L)).thenReturn(definition);

        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(Map.of());

        assertThatThrownBy(() -> service.execute(3L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.REPORT_SQL_INVALID.getMessage());
    }

    @Test
    void executeRejectsDisabledReport() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(4L);
        definition.setStatus(0);
        when(mapper.selectById(4L)).thenReturn(definition);

        assertThatThrownBy(() -> service.execute(4L, new ReportExecuteRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
    }

    // ---------- R4-1.37：执行参数防御校验（数量/类型/长度） ----------

    @Test
    void executeRejectsTooManyParams() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(20L);
        definition.setStatus(1);
        definition.setDataSource("SELECT 1");
        when(mapper.selectById(20L)).thenReturn(definition);

        Map<String, Object> tooMany = new java.util.HashMap<>();
        for (int i = 0; i < 65; i++) {
            tooMany.put("p" + i, i);
        }
        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(tooMany);

        assertThatThrownBy(() -> service.execute(20L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数过多");
    }

    @Test
    void executeRejectsCollectionParamValue() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(21L);
        definition.setStatus(1);
        definition.setDataSource("SELECT 1");
        when(mapper.selectById(21L)).thenReturn(definition);

        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(Map.of("nested", Map.of("k", "v")));

        assertThatThrownBy(() -> service.execute(21L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持字符串/数字/布尔值");
    }

    @Test
    void executeRejectsOverlongStringParam() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(22L);
        definition.setStatus(1);
        definition.setDataSource("SELECT 1");
        when(mapper.selectById(22L)).thenReturn(definition);

        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(Map.of("big", "x".repeat(5001)));

        assertThatThrownBy(() -> service.execute(22L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过长");
    }

    @Test
    void executeAcceptsBaseTypeParams() {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(23L);
        definition.setStatus(1);
        definition.setDataSource(
                "SELECT device_code FROM sys_device WHERE enabled = :enabled AND type = :type AND count = :count");
        when(mapper.selectById(23L)).thenReturn(definition);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        ReportExecuteRequest request = new ReportExecuteRequest();
        request.setParams(Map.of("enabled", true, "type", "IT", "count", 3));

        ReportExecuteResultVo result = service.execute(23L, request);

        assertThat(result.getRows()).isEmpty();
    }

    @Test
    void createDuplicateKeyRejectedWithBusinessCode() {
        // 并发同码创建：预检通过但 insert 命中唯一键 → 转精确业务码而非泛化 500
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ReportDefinitionDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        assertThatThrownBy(() -> service.create(request("T-DUP-RACE", "SELECT 1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.REPORT_CODE_EXISTS.getCode());
    }

    @Test
    void updatePassesThroughVersion() {
        when(mapper.selectById(10L)).thenReturn(definition(10L));
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.updateById(any(ReportDefinitionDO.class))).thenReturn(1);

        ReportDefinitionSaveRequest request = request("T-UPD", "SELECT 1");
        request.setId(10L);
        request.setVersion(2);
        service.update(request);

        ArgumentCaptor<ReportDefinitionDO> captor = ArgumentCaptor.forClass(ReportDefinitionDO.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void updateConflictOnStaleVersion() {
        when(mapper.selectById(10L)).thenReturn(definition(10L));
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.updateById(any(ReportDefinitionDO.class))).thenReturn(0);

        ReportDefinitionSaveRequest request = request("T-CONFLICT", "SELECT 1");
        request.setId(10L);
        request.setVersion(1);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
    }

    private ReportDefinitionDO definition(Long id) {
        ReportDefinitionDO definition = new ReportDefinitionDO();
        definition.setId(id);
        definition.setCode("T-" + id);
        return definition;
    }

    private ReportDefinitionSaveRequest request(String code, String dataSource) {
        ReportDefinitionSaveRequest request = new ReportDefinitionSaveRequest();
        request.setCode(code);
        request.setName("测试报表");
        request.setCategory("IT");
        request.setDataSource(dataSource);
        request.setStatus(1);
        return request;
    }
}
