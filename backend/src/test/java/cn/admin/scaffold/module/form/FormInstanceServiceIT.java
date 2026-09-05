package cn.admin.scaffold.module.form;

import cn.admin.scaffold.AbstractIntegrationTest;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.form.dto.FormDefinitionSaveRequest;
import cn.admin.scaffold.module.form.dto.FormInstanceQuery;
import cn.admin.scaffold.module.form.dto.FormInstanceSubmitRequest;
import cn.admin.scaffold.module.form.vo.FormInstanceVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 表单提交集成测试：定义→发布→提交→查询往返 + 数据校验 + 审批流转 + 租户隔离。
 */
class FormInstanceServiceIT extends AbstractIntegrationTest {

    @Autowired
    private FormDefinitionService formDefinitionService;

    @Autowired
    private FormInstanceService formInstanceService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void definitionPublishSubmitQueryRoundTrip() {
        Long defId = formDefinitionService.create(definition("IT-FORM-200"));
        formDefinitionService.publish(defId);

        FormInstanceSubmitRequest submit = new FormInstanceSubmitRequest();
        submit.setBizNo("IT-BIZ-200");
        submit.setFormCode("IT-FORM-200");
        submit.setData(Map.of("name", "张三", "reason", "事假"));
        Long instanceId = formInstanceService.submit(submit);

        FormInstanceVo vo = formInstanceService.getById(instanceId);
        assertThat(vo.getStatus()).isEqualTo("SUBMITTED");
        assertThat(vo.getFormCode()).isEqualTo("IT-FORM-200");
        assertThat(vo.getData()).containsEntry("name", "张三");

        // 按表单编码分页查询
        FormInstanceQuery query = new FormInstanceQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setFormCode("IT-FORM-200");
        assertThat(formInstanceService.page(query).getRecords())
                .extracting(FormInstanceVo::getFormCode)
                .containsExactly("IT-FORM-200");

        // 审批流转：SUBMITTED → APPROVED
        formInstanceService.approve(instanceId, "APPROVED");
        assertThat(formInstanceService.getById(instanceId).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void submitToDraftDefinitionRejected() {
        formDefinitionService.create(definition("IT-FORM-201")); // 仅建定义，未发布

        FormInstanceSubmitRequest submit = new FormInstanceSubmitRequest();
        submit.setBizNo("IT-BIZ-201");
        submit.setFormCode("IT-FORM-201");
        submit.setData(Map.of("name", "张三"));

        assertThatThrownBy(() -> formInstanceService.submit(submit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void submitRejectsMissingRequiredData() {
        Long defId = formDefinitionService.create(definition("IT-FORM-202"));
        formDefinitionService.publish(defId);

        FormInstanceSubmitRequest submit = new FormInstanceSubmitRequest();
        submit.setBizNo("IT-BIZ-202");
        submit.setFormCode("IT-FORM-202");
        submit.setData(Map.of()); // 缺必填字段 name

        assertThatThrownBy(() -> formInstanceService.submit(submit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("姓名");
    }

    @Test
    void tenantIsolationKeepsRecordsApart() {
        TenantContext.setTenantId(1L);
        Long defId = formDefinitionService.create(definition("IT-FORM-300"));
        formDefinitionService.publish(defId);

        FormInstanceSubmitRequest submit = new FormInstanceSubmitRequest();
        submit.setBizNo("IT-BIZ-300");
        submit.setFormCode("IT-FORM-300");
        submit.setData(Map.of("name", "租户1提交"));
        formInstanceService.submit(submit);

        // 另一租户看不到租户1的提交记录
        TenantContext.setTenantId(2L);
        FormInstanceQuery query = new FormInstanceQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setFormCode("IT-FORM-300");
        assertThat(formInstanceService.page(query).getRecords()).isEmpty();
    }

    private FormDefinitionSaveRequest definition(String code) {
        FormDefinitionSaveRequest request = new FormDefinitionSaveRequest();
        request.setCode(code);
        request.setName("集成表单-" + code);
        request.setSchemaJson("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"input\",\"required\":true,\"maxLength\":20},"
                + "{\"key\":\"reason\",\"label\":\"事由\",\"type\":\"textarea\",\"required\":false}]");
        return request;
    }
}
