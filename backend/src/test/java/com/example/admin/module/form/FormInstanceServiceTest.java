package com.example.admin.module.form;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.form.dto.FormInstanceQuery;
import com.example.admin.module.form.dto.FormInstanceSubmitRequest;
import com.example.admin.module.form.entity.FormDefinitionDO;
import com.example.admin.module.form.entity.FormInstanceDO;
import com.example.admin.module.form.mapper.FormDefinitionMapper;
import com.example.admin.module.form.mapper.FormInstanceMapper;
import com.example.admin.module.form.vo.FormInstanceVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单提交服务单测：数据校验传导、未发布拒绝、状态流转。
 */
@ExtendWith(MockitoExtension.class)
class FormInstanceServiceTest {

    @Mock
    private FormInstanceMapper formInstanceMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormSchemaValidator schemaValidator;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FormInstanceService formInstanceService;

    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        registerTableInfo(FormDefinitionDO.class);
        registerTableInfo(FormInstanceDO.class);
    }

    private static void registerTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void submitToPublishedDefinitionPersists() throws JsonProcessingException {
        when(formDefinitionMapper.selectOne(any())).thenReturn(publishedDef(1L, "FORM-SUB"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"name\":\"张三\"}");
        doAnswer(invocation -> {
            ((FormInstanceDO) invocation.getArgument(0)).setId(21L);
            return 1;
        }).when(formInstanceMapper).insert(any(FormInstanceDO.class));

        Long id = formInstanceService.submit(submitRequest("BIZ-001", "FORM-SUB", Map.of("name", "张三")));

        assertThat(id).isEqualTo(21L);
        ArgumentCaptor<FormInstanceDO> captor = ArgumentCaptor.forClass(FormInstanceDO.class);
        verify(formInstanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUBMITTED");
        assertThat(captor.getValue().getFormCode()).isEqualTo("FORM-SUB");
        assertThat(captor.getValue().getFormId()).isEqualTo(1L);
    }

    @Test
    void submitToDraftDefinitionRejected() {
        FormDefinitionDO draft = publishedDef(2L, "FORM-DRAFT");
        draft.setStatus(0);
        when(formDefinitionMapper.selectOne(any())).thenReturn(draft);

        assertThatThrownBy(() -> formInstanceService.submit(submitRequest("BIZ-002", "FORM-DRAFT", Map.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void submitToMissingDefinitionRejected() {
        when(formDefinitionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> formInstanceService.submit(submitRequest("BIZ-003", "FORM-NONE", Map.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void submitPropagatesDataValidationError() {
        when(formDefinitionMapper.selectOne(any())).thenReturn(publishedDef(3L, "FORM-VAL"));
        doThrow(new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(), "字段【姓名】为必填项"))
                .when(schemaValidator).validateData(any(), any());

        assertThatThrownBy(() -> formInstanceService.submit(submitRequest("BIZ-004", "FORM-VAL", Map.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("姓名");
    }

    @Test
    void approveMovesSubmittedToApproved() {
        when(formInstanceMapper.casStatus(5L, "APPROVED")).thenReturn(1);

        formInstanceService.approve(5L, "APPROVED");

        verify(formInstanceMapper).casStatus(5L, "APPROVED");
    }

    @Test
    void approveRejectsInvalidTargetStatus() {
        assertThatThrownBy(() -> formInstanceService.approve(5L, "PROCESSING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法审批状态");
    }

    @Test
    void approveRejectsAlreadyProcessedState() {
        // CAS 命中 0 行 = 记录不存在或已被并发流转 → 拒绝，防止重复/覆盖审批
        when(formInstanceMapper.casStatus(6L, "REJECTED")).thenReturn(0);

        assertThatThrownBy(() -> formInstanceService.approve(6L, "REJECTED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待审批");
    }

    @Test
    void pageFiltersByFormCode() throws JsonProcessingException {
        FormInstanceDO ins = new FormInstanceDO();
        ins.setId(1L);
        ins.setFormCode("FORM-P");
        ins.setDataJson("{\"name\":\"张三\"}");
        ins.setStatus("SUBMITTED");
        @SuppressWarnings("unchecked")
        IPage<FormInstanceDO> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(ins));
        when(page.getTotal()).thenReturn(1L);
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(10L);
        when(formInstanceMapper.selectPage(any(), any())).thenReturn(page);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("name", "张三"));

        FormInstanceQuery query = new FormInstanceQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setFormCode("FORM-P");
        PageResult<FormInstanceVo> result = formInstanceService.page(query);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getData()).containsEntry("name", "张三");
    }

    private FormDefinitionDO publishedDef(Long id, String code) {
        FormDefinitionDO def = new FormDefinitionDO();
        def.setId(id);
        def.setCode(code);
        def.setStatus(1);
        return def;
    }

    private FormInstanceSubmitRequest submitRequest(String bizNo, String formCode, Map<String, Object> data) {
        FormInstanceSubmitRequest request = new FormInstanceSubmitRequest();
        request.setBizNo(bizNo);
        request.setFormCode(formCode);
        request.setData(data);
        return request;
    }
}
