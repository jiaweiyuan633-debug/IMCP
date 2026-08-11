package com.example.admin.module.form;

import com.example.admin.AbstractIntegrationTest;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.form.dto.FormDefinitionQuery;
import com.example.admin.module.form.dto.FormDefinitionSaveRequest;
import com.example.admin.module.form.vo.FormDefinitionVo;
import com.example.admin.module.form.vo.FormSchemaVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 表单定义集成测试：定义→发布→查询往返 + schema 校验 + 租户隔离（依赖集成阶段把 form_definition 补进 TENANT_TABLES）。
 */
class FormDefinitionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private FormDefinitionService formDefinitionService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createPublishAndQueryRoundTrip() {
        Long id = formDefinitionService.create(request("IT-FORM-001", "集成请假单"));

        FormDefinitionQuery query = new FormDefinitionQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setCode("IT-FORM-001");
        PageResult<FormDefinitionVo> result = formDefinitionService.page(query);
        assertThat(result.getRecords()).extracting(FormDefinitionVo::getCode).contains("IT-FORM-001");

        // 草稿不可获取渲染结构
        assertThatThrownBy(() -> formDefinitionService.getSchema(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());

        // 发布后 version 递增、返回渲染结构
        formDefinitionService.publish(id);
        FormDefinitionVo published = formDefinitionService.getById(id);
        assertThat(published.getStatus()).isEqualTo(1);
        assertThat(published.getVersion()).isGreaterThanOrEqualTo(1);

        FormSchemaVo schema = formDefinitionService.getSchema(id);
        assertThat(schema.getFields()).isNotEmpty();
        assertThat(schema.getFields()).extracting(f -> f.getKey()).contains("name");
    }

    @Test
    void duplicateCodeRejectedWithinTenant() {
        formDefinitionService.create(request("IT-FORM-002", "表单A"));

        assertThatThrownBy(() -> formDefinitionService.create(request("IT-FORM-002", "表单B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORM_CODE_EXISTS.getMessage());
    }

    @Test
    void invalidSchemaRejectedOnCreate() {
        FormDefinitionSaveRequest request = request("IT-FORM-003", "非法表单");
        request.setSchemaJson("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"upload\"}]");

        assertThatThrownBy(() -> formDefinitionService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORM_SCHEMA_INVALID.getMessage());
    }

    @Test
    void tenantIsolationKeepsRecordsApart() {
        TenantContext.setTenantId(1L);
        formDefinitionService.create(request("IT-FORM-100", "租户1表单"));

        TenantContext.setTenantId(2L);
        // 另一租户可用同编码（唯一性按租户隔离）
        formDefinitionService.create(request("IT-FORM-100", "租户2表单"));

        TenantContext.setTenantId(2L);
        FormDefinitionQuery query = new FormDefinitionQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setCode("IT-FORM-100");
        assertThat(formDefinitionService.page(query).getRecords())
                .extracting(FormDefinitionVo::getName)
                .containsExactly("租户2表单");
    }

    private FormDefinitionSaveRequest request(String code, String name) {
        FormDefinitionSaveRequest request = new FormDefinitionSaveRequest();
        request.setCode(code);
        request.setName(name);
        request.setSchemaJson("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"input\",\"required\":true,\"maxLength\":20},"
                + "{\"key\":\"reason\",\"label\":\"事由\",\"type\":\"textarea\",\"required\":false}]");
        return request;
    }
}
