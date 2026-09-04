package cn.admin.scaffold.module.notice;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.notice.dto.MessageTemplateSaveRequest;
import cn.admin.scaffold.module.notice.dto.MessageTemplateSendRequest;
import cn.admin.scaffold.module.notice.entity.SysMessageTemplateDO;
import cn.admin.scaffold.module.notice.mapper.SysMessageTemplateMapper;
import cn.admin.scaffold.module.system.SystemMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageTemplateServiceTest {

    private SysMessageTemplateMapper templateMapper;
    private MessageTemplateRenderer renderer;
    private SystemMessageService systemMessageService;
    private MessageTemplateService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(SysMessageTemplateMapper.class);
        renderer = new MessageTemplateRenderer();
        systemMessageService = mock(SystemMessageService.class);
        service = new MessageTemplateService(templateMapper, renderer, systemMessageService);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createReturnsIdAfterInsert() {
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(templateMapper.insert(any(SysMessageTemplateDO.class))).thenAnswer(invocation -> {
            ((SysMessageTemplateDO) invocation.getArgument(0)).setId(9L);
            return 1;
        });

        Long id = service.create(request("order_notice"));

        assertThat(id).isEqualTo(9L);
    }

    @Test
    void createRejectsDuplicateCode() {
        SysMessageTemplateDO existing = new SysMessageTemplateDO();
        existing.setId(1L);
        existing.setTemplateCode("order_notice");
        when(templateMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.create(request("order_notice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板编码已存在");
    }

    @Test
    void sendByTemplateRendersAndForwardsContentType() {
        SysMessageTemplateDO template = new SysMessageTemplateDO();
        template.setId(1L);
        template.setTemplateCode("order_notice");
        template.setMessageType("NOTICE");
        template.setTitleTemplate("订单 ${orderNo} 已创建");
        template.setContentTemplate("<p>金额 <b>${amount}</b></p>");
        template.setContentType("HTML");
        template.setStatus(1);
        when(templateMapper.selectOne(any())).thenReturn(template);
        when(systemMessageService.sendWithType(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(7L);

        MessageTemplateSendRequest sendRequest = new MessageTemplateSendRequest();
        sendRequest.setTemplateCode("order_notice");
        sendRequest.setParams(Map.of("orderNo", "SO-1", "amount", "100.50"));
        sendRequest.setReceiverIds(List.of(1L, 2L));
        sendRequest.setBizType("order");
        sendRequest.setBizId(5L);

        Long messageId = service.sendByTemplate(sendRequest);

        assertThat(messageId).isEqualTo(7L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> receiversCaptor = ArgumentCaptor.forClass(List.class);
        verify(systemMessageService).sendWithType(
                any(), eq("NOTICE"),
                eq("订单 SO-1 已创建"),
                eq("<p>金额 <b>100.50</b></p>"),
                eq("HTML"), eq("order"), eq(5L), receiversCaptor.capture());
        assertThat(receiversCaptor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    void sendByTemplateDefaultsContentTypeToText() {
        SysMessageTemplateDO template = new SysMessageTemplateDO();
        template.setTemplateCode("notice");
        template.setMessageType("SYSTEM");
        template.setTitleTemplate("${title}");
        template.setContentTemplate("${content}");
        template.setContentType(null);
        template.setStatus(1);
        when(templateMapper.selectOne(any())).thenReturn(template);

        MessageTemplateSendRequest sendRequest = new MessageTemplateSendRequest();
        sendRequest.setTemplateCode("notice");
        sendRequest.setParams(Map.of("title", "T", "content", "C"));
        service.sendByTemplate(sendRequest);

        verify(systemMessageService).sendWithType(any(), anyString(), anyString(), anyString(),
                eq("TEXT"), any(), any(), any());
    }

    @Test
    void sendByTemplateRejectsWhenDisabled() {
        SysMessageTemplateDO template = new SysMessageTemplateDO();
        template.setTemplateCode("notice");
        template.setStatus(0);
        when(templateMapper.selectOne(any())).thenReturn(template);

        MessageTemplateSendRequest sendRequest = new MessageTemplateSendRequest();
        sendRequest.setTemplateCode("notice");

        assertThatThrownBy(() -> service.sendByTemplate(sendRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板已停用");
    }

    @Test
    void sendByTemplateRejectsWhenMissing() {
        when(templateMapper.selectOne(any())).thenReturn(null);
        MessageTemplateSendRequest sendRequest = new MessageTemplateSendRequest();
        sendRequest.setTemplateCode("not-exists");
        assertThatThrownBy(() -> service.sendByTemplate(sendRequest))
                .isInstanceOf(BusinessException.class);
    }

    private MessageTemplateSaveRequest request(String code) {
        MessageTemplateSaveRequest request = new MessageTemplateSaveRequest();
        request.setTemplateCode(code);
        request.setTemplateName("订单通知");
        request.setTitleTemplate("${title}");
        request.setContentTemplate("${content}");
        return request;
    }
}
