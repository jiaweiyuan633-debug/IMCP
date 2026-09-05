package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.entity.SysMessageDO;
import cn.admin.scaffold.module.system.mapper.SysMessageMapper;
import cn.admin.scaffold.module.system.mapper.SysMessageReadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 富文本消息测试：sendWithType 透传 contentType，普通 send 默认 TEXT。
 */
class SystemMessageServiceRichTextTest {

    private SysMessageMapper messageMapper;
    private SysMessageReadMapper messageReadMapper;
    private ApplicationEventPublisher eventPublisher;
    private SystemMessageService service;

    @BeforeEach
    void setUp() {
        messageMapper = mock(SysMessageMapper.class);
        messageReadMapper = mock(SysMessageReadMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SystemMessageService(messageMapper, messageReadMapper, eventPublisher);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubInsert() {
        when(messageMapper.insert(any(SysMessageDO.class))).thenAnswer(invocation -> {
            ((SysMessageDO) invocation.getArgument(0)).setId(1L);
            return 1;
        });
    }

    @Test
    void sendWithTypePersistsContentType() {
        stubInsert();
        service.sendWithType(1L, "NOTICE", "公告", "<b>富文本</b>", "HTML", "biz", 7L, List.of(2L));

        ArgumentCaptor<SysMessageDO> captor = ArgumentCaptor.forClass(SysMessageDO.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("HTML");
        assertThat(captor.getValue().getContent()).isEqualTo("<b>富文本</b>");
    }

    @Test
    void plainSendDefaultsToTextContentType() {
        stubInsert();
        service.send(1L, "SYSTEM", "标题", "纯文本", "biz", 7L, List.of(2L));

        ArgumentCaptor<SysMessageDO> captor = ArgumentCaptor.forClass(SysMessageDO.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("TEXT");
    }

    @Test
    void sendWithTypeBroadcastAlsoCarriesContentType() {
        stubInsert();
        service.sendBroadcastWithType(1L, "NOTICE", "公告", "<p>html</p>", "HTML", "biz", 7L);

        ArgumentCaptor<SysMessageDO> captor = ArgumentCaptor.forClass(SysMessageDO.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("HTML");
        assertThat(captor.getValue().getReceiverId()).isNull();
    }
}
