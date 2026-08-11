package com.example.admin.module.report;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.report.dto.ScreenTemplateSaveRequest;
import com.example.admin.module.report.entity.ScreenTemplateDO;
import com.example.admin.module.report.mapper.ScreenTemplateMapper;
import com.example.admin.module.report.vo.ScreenTemplateVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 大屏模板服务：内置模板全租户可见且不可删除/直接覆盖；自定义模板租户隔离、编码唯一。
 */
class ScreenTemplateServiceTest {

    private ScreenTemplateMapper mapper;
    private ScreenTemplateService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ScreenTemplateMapper.class);
        service = new ScreenTemplateService(mapper);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ScreenTemplateDO template(Long id, Long tenantId, int builtin, String name) {
        ScreenTemplateDO template = new ScreenTemplateDO();
        template.setId(id);
        template.setTenantId(tenantId);
        template.setBuiltin(builtin);
        template.setName(name);
        template.setLayout("{\"widgets\":[]}");
        template.setStatus(1);
        return template;
    }

    @Test
    void listIncludesBuiltinAndTenantTemplates() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(template(1L, null, 1, "综合态势"), template(2L, 1L, 0, "我的大屏")));

        List<ScreenTemplateVo> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isBuiltin()).isTrue();
        assertThat(result.get(1).isBuiltin()).isFalse();
        assertThat(result.get(1).getName()).isEqualTo("我的大屏");
    }

    @Test
    void createSetsTenantAndNonBuiltinAndGeneratesCode() {
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        ScreenTemplateSaveRequest request = new ScreenTemplateSaveRequest();
        request.setName("自定义大屏");
        request.setLayout("{\"widgets\":[]}");

        service.create(request);

        ArgumentCaptor<ScreenTemplateDO> captor = ArgumentCaptor.forClass(ScreenTemplateDO.class);
        verify(mapper).insert(captor.capture());
        ScreenTemplateDO saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getBuiltin()).isZero();
        assertThat(saved.getCode()).startsWith("tpl-");
        assertThat(saved.getName()).isEqualTo("自定义大屏");
    }

    @Test
    void createRejectsDuplicateCodeWithinTenant() {
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        ScreenTemplateSaveRequest request = new ScreenTemplateSaveRequest();
        request.setName("自定义大屏");
        request.setCode("dup");
        request.setLayout("{\"widgets\":[]}");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(ResultCode.SCREEN_TEMPLATE_CODE_EXISTS.getCode()));
    }

    @Test
    void updateRejectsBuiltinTemplate() {
        when(mapper.selectById(1L)).thenReturn(template(1L, null, 1, "综合态势"));
        ScreenTemplateSaveRequest request = new ScreenTemplateSaveRequest();
        request.setId(1L);
        request.setName("覆盖内置");
        request.setLayout("{\"widgets\":[]}");

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verify(mapper, never()).updateById(any(ScreenTemplateDO.class));
    }

    @Test
    void deleteRejectsForeignTenantTemplate() {
        when(mapper.selectById(1L)).thenReturn(template(1L, 2L, 0, "他人模板"));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verify(mapper, never()).deleteById(1L);
    }

    @Test
    void updateSavesOwnCustomTemplate() {
        when(mapper.selectById(1L)).thenReturn(template(1L, 1L, 0, "我的大屏"));
        ScreenTemplateSaveRequest request = new ScreenTemplateSaveRequest();
        request.setId(1L);
        request.setName("改名大屏");
        request.setLayout("{\"widgets\":[{\"id\":\"w1\"}]}");

        service.update(request);

        ArgumentCaptor<ScreenTemplateDO> captor = ArgumentCaptor.forClass(ScreenTemplateDO.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("改名大屏");
        assertThat(captor.getValue().getLayout()).contains("w1");
    }
}
