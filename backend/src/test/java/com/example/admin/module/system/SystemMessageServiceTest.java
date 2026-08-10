package com.example.admin.module.system;

import com.example.admin.common.BusinessException;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysMessageDO;
import com.example.admin.module.system.mapper.SysMessageMapper;
import com.example.admin.module.system.mapper.SysMessageReadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemMessageServiceTest {

    @Mock
    private SysMessageMapper messageMapper;

    @Mock
    private SysMessageReadMapper messageReadMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SystemMessageService messageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageService = new SystemMessageService(messageMapper, messageReadMapper, eventPublisher);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void markReadThrowsWhenMessageNotVisible() {
        when(messageMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> messageService.markRead(2L, 99L));
        verify(messageReadMapper, never()).markRead(anyLong(), anyLong(), anyLong());
    }

    @Test
    void sendBroadcastPersistsAndPublishesBroadcastEvent() {
        when(messageMapper.insert(any(SysMessageDO.class))).thenAnswer(invocation -> {
            SysMessageDO message = invocation.getArgument(0);
            message.setId(10L);
            return 1;
        });
        Long id = messageService.sendBroadcast(1L, "SYSTEM", "公告", "内容", "test", 7L);
        assertEquals(10L, id);
        ArgumentCaptor<MessagePushEvent> eventCaptor = ArgumentCaptor.forClass(MessagePushEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertNull(eventCaptor.getValue().userId());
        assertEquals("公告", eventCaptor.getValue().payload().get("title"));
    }

    @Test
    void sendToUserPublishesEventWithUserId() {
        when(messageMapper.insert(any(SysMessageDO.class))).thenAnswer(invocation -> {
            SysMessageDO message = invocation.getArgument(0);
            message.setId(11L);
            return 1;
        });
        Long id = messageService.send(1L, "TODO", "待办", "内容", "workflow", 8L, Arrays.asList(null, 2L));
        assertEquals(11L, id);
        ArgumentCaptor<MessagePushEvent> eventCaptor = ArgumentCaptor.forClass(MessagePushEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(2L, eventCaptor.getValue().userId());
        assertEquals("待办", eventCaptor.getValue().payload().get("title"));
    }

    @Test
    void sendToEmptyReceiversFallsBackToBroadcast() {
        when(messageMapper.insert(any(SysMessageDO.class))).thenAnswer(invocation -> {
            SysMessageDO message = invocation.getArgument(0);
            message.setId(12L);
            return 1;
        });
        Long id = messageService.send(1L, "SYSTEM", "群发", "内容", "test", 9L, Arrays.asList());
        assertEquals(12L, id);
        ArgumentCaptor<MessagePushEvent> eventCaptor = ArgumentCaptor.forClass(MessagePushEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertNull(eventCaptor.getValue().userId());
    }
}
