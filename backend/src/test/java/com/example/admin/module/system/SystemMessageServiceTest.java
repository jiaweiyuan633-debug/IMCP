package com.example.admin.module.system;

import com.example.admin.common.BusinessException;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysMessageDO;
import com.example.admin.module.system.mapper.SysMessageMapper;
import com.example.admin.module.system.mapper.SysMessageReadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemMessageServiceTest {

    @Mock
    private SysMessageMapper messageMapper;

    @Mock
    private SysMessageReadMapper messageReadMapper;

    @Mock
    private MessageRealtimeService messageRealtimeService;

    private SystemMessageService messageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageService = new SystemMessageService(messageMapper, messageReadMapper, messageRealtimeService);
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
    void sendBroadcastPersistsAndPushes() {
        when(messageMapper.insert(any(SysMessageDO.class))).thenAnswer(invocation -> {
            SysMessageDO message = invocation.getArgument(0);
            message.setId(10L);
            return 1;
        });
        Long id = messageService.sendBroadcast(1L, "SYSTEM", "公告", "内容", "test", 7L);
        assertEquals(10L, id);
        verify(messageRealtimeService).broadcast(any());
    }

    @Test
    void sendToUserIgnoresNullReceiver() {
        when(messageMapper.insert(any(SysMessageDO.class))).thenAnswer(invocation -> {
            SysMessageDO message = invocation.getArgument(0);
            message.setId(11L);
            return 1;
        });
        Long id = messageService.send(1L, "TODO", "待办", "内容", "workflow", 8L, Arrays.asList(null, 2L));
        assertEquals(11L, id);
        verify(messageRealtimeService).pushToUser(eq(2L), any());
    }
}
