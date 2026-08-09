package com.example.admin.module.ai;

import com.example.admin.common.TenantContext;
import com.example.admin.module.ai.dto.AiCallbackRequest;
import com.example.admin.module.ai.dto.AiTaskCreateRequest;
import com.example.admin.module.ai.entity.AiServiceConfigDO;
import com.example.admin.module.ai.entity.AiTaskDO;
import com.example.admin.module.ai.entity.AiTaskResultDO;
import com.example.admin.module.ai.mapper.AiServiceConfigMapper;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.module.ai.mapper.AiTaskResultMapper;
import com.example.admin.module.ai.manager.AiTaskManager;
import com.example.admin.module.system.DataScopeHelper;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskServiceTest {

    @Mock
    private AiTaskMapper taskMapper;

    @Mock
    private AiTaskResultMapper resultMapper;

    @Mock
    private AiServiceConfigMapper configMapper;

    @Mock
    private AiTaskManager aiTaskManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DataScopeHelper dataScopeHelper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private AiTaskService aiTaskService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createQueuesTaskAfterSubmit() {
        ReflectionTestUtils.setField(aiTaskService, "callbackBaseUrl", "http://localhost:8080");
        when(configMapper.selectOne(any())).thenReturn(enabledConfig());
        when(aiTaskManager.submit(any(), any(), any(), any(), any())).thenReturn(Map.of());
        doAnswer(invocation -> {
            AiTaskDO task = invocation.getArgument(0);
            task.setId(11L);
            return 1;
        }).when(taskMapper).insert(any(AiTaskDO.class));

        AiTaskCreateRequest request = new AiTaskCreateRequest();
        request.setBizType("OCR");
        request.setServiceCode("default");
        request.setParams(Map.of("file", "a.png"));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            Long id = aiTaskService.create(request);
            assertEquals(11L, id);
        }

        ArgumentCaptor<AiTaskDO> captor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(AiTaskStatus.QUEUED.name(), captor.getValue().getStatus());
    }

    @Test
    void callbackMarksTaskSucceededAndSavesResult() throws Exception {
        AiTaskDO task = new AiTaskDO();
        task.setId(2L);
        task.setTenantId(3L);
        task.setServiceCode("default");
        task.setStatus(AiTaskStatus.RUNNING.name());
        when(taskMapper.selectByTaskNoIgnoreTenant("T1")).thenReturn(task);
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setApiKey("secret");
        when(configMapper.selectOne(any())).thenReturn(config);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        AiCallbackRequest request = new AiCallbackRequest();
        request.setTaskNo("T1");
        request.setStatus(AiTaskStatus.SUCCEEDED.name());
        request.setRetryCount(2);
        request.setResult(Map.of("ok", true));

        aiTaskService.handleCallback(request, "secret");

        ArgumentCaptor<AiTaskDO> taskCaptor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals(AiTaskStatus.SUCCEEDED.name(), taskCaptor.getValue().getStatus());
        assertEquals(2, taskCaptor.getValue().getRetryCount());
        verify(resultMapper).insert(any(AiTaskResultDO.class));
    }

    private AiServiceConfigDO enabledConfig() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setCode("default");
        config.setEnabled(1);
        return config;
    }
}
