package cn.admin.scaffold.module.form;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.form.dto.FormDefinitionQuery;
import cn.admin.scaffold.module.form.dto.FormDefinitionSaveRequest;
import cn.admin.scaffold.module.form.entity.FormDefinitionDO;
import cn.admin.scaffold.module.form.mapper.FormDefinitionMapper;
import cn.admin.scaffold.module.form.vo.FormDefinitionVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单定义服务单测：code 唯一校验、发布前 schema 校验、乐观锁冲突。
 */
@ExtendWith(MockitoExtension.class)
class FormDefinitionServiceTest {

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormSchemaValidator schemaValidator;

    @InjectMocks
    private FormDefinitionService formDefinitionService;

    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), FormDefinitionDO.class);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(formDefinitionMapper.selectOne(any())).thenReturn(existing("FORM-A"));

        assertThatThrownBy(() -> formDefinitionService.create(request("FORM-A", "请假单", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORM_CODE_EXISTS.getMessage());
    }

    @Test
    void createInsertsDraftDefinition() {
        when(formDefinitionMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ((FormDefinitionDO) invocation.getArgument(0)).setId(7L);
            return 1;
        }).when(formDefinitionMapper).insert(any(FormDefinitionDO.class));

        Long id = formDefinitionService.create(request("FORM-B", "报销单", null));

        assertThat(id).isEqualTo(7L);
        ArgumentCaptor<FormDefinitionDO> captor = ArgumentCaptor.forClass(FormDefinitionDO.class);
        verify(formDefinitionMapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("FORM-B");
        // 新增默认草稿
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void createRejectsInvalidSchema() {
        when(formDefinitionMapper.selectOne(any())).thenReturn(null);
        when(schemaValidator.validateSchema(any())).thenThrow(
                new BusinessException(ResultCode.FORM_SCHEMA_INVALID));

        assertThatThrownBy(() -> formDefinitionService.create(request("FORM-C", "非法表单", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORM_SCHEMA_INVALID.getMessage());
    }

    @Test
    void createDuplicateKeyRejectedWithBusinessCode() {
        // 并发同码创建：预检通过但 insert 命中唯一键 → 转精确业务码而非泛化 500
        when(formDefinitionMapper.selectOne(any())).thenReturn(null);
        when(formDefinitionMapper.insert(any(FormDefinitionDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        assertThatThrownBy(() -> formDefinitionService.create(request("FORM-RACE", "竞态", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORM_CODE_EXISTS.getMessage());
    }

    @Test
    void publishValidatesSchemaBeforeUpdating() {
        FormDefinitionDO definition = new FormDefinitionDO();
        definition.setId(1L);
        definition.setCode("FORM-D");
        definition.setSchemaJson("[]");
        definition.setStatus(0);
        definition.setVersion(1);
        when(formDefinitionMapper.selectById(1L)).thenReturn(definition);
        when(formDefinitionMapper.updateById(any(FormDefinitionDO.class))).thenReturn(1);

        formDefinitionService.publish(1L);

        verify(schemaValidator).validateSchema("[]");
        ArgumentCaptor<FormDefinitionDO> captor = ArgumentCaptor.forClass(FormDefinitionDO.class);
        verify(formDefinitionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void getSchemaRejectsDraft() {
        FormDefinitionDO definition = new FormDefinitionDO();
        definition.setId(2L);
        definition.setCode("FORM-E");
        definition.setStatus(0);
        when(formDefinitionMapper.selectById(2L)).thenReturn(definition);

        assertThatThrownBy(() -> formDefinitionService.getSchema(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void updateRequiresId() {
        assertThatThrownBy(() -> formDefinitionService.update(request("FORM-F", "工单", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID");
    }

    @Test
    void updateConflictOnStaleVersion() {
        when(formDefinitionMapper.selectOne(any())).thenReturn(null);
        when(formDefinitionMapper.updateById(any(FormDefinitionDO.class))).thenReturn(0);

        FormDefinitionSaveRequest request = request("FORM-G", "需求单", 3L);
        request.setVersion(1);

        assertThatThrownBy(() -> formDefinitionService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
    }

    @Test
    void pageMapsRecordsToVo() {
        FormDefinitionDO def = new FormDefinitionDO();
        def.setId(1L);
        def.setName("请假单");
        def.setCode("FORM-H");
        def.setStatus(1);
        def.setVersion(2);
        @SuppressWarnings("unchecked")
        IPage<FormDefinitionDO> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(def));
        when(page.getTotal()).thenReturn(1L);
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(10L);
        when(formDefinitionMapper.selectPage(any(), any())).thenReturn(page);

        FormDefinitionQuery query = new FormDefinitionQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<FormDefinitionVo> result = formDefinitionService.page(query);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getCode()).isEqualTo("FORM-H");
    }

    private FormDefinitionDO existing(String code) {
        FormDefinitionDO def = new FormDefinitionDO();
        def.setId(99L);
        def.setCode(code);
        return def;
    }

    private FormDefinitionSaveRequest request(String code, String name, Long id) {
        FormDefinitionSaveRequest request = new FormDefinitionSaveRequest();
        request.setId(id);
        request.setCode(code);
        request.setName(name);
        request.setSchemaJson("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"input\",\"required\":true}]");
        return request;
    }
}
