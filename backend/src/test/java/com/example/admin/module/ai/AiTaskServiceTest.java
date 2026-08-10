package com.example.admin.module.ai;

import cn.hutool.core.util.HexUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

    // 服务内部使用 LambdaQueryWrapper/LambdaUpdateWrapper，其列名解析依赖 MyBatis-Plus
    // 的 TableInfo 缓存；mapper 被 mock 时缓存不会自动注册，需显式初始化，否则抛
    // "MybatisPlus can not find lambda cache for this entity"。
    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        registerTableInfo(AiTaskDO.class);
        registerTableInfo(AiServiceConfigDO.class);
        registerTableInfo(AiTaskResultDO.class);
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
        // handleCallback 用条件 UPDATE 抢占终态，mock 需返回 1 才能继续走结果入库与通知
        when(taskMapper.update(isNull(), any(AbstractWrapper.class))).thenReturn(1);

        AiCallbackRequest request = new AiCallbackRequest();
        request.setTaskNo("T1");
        request.setStatus(AiTaskStatus.SUCCEEDED.name());
        request.setRetryCount(2);
        request.setResult(Map.of("ok", true));

        // 构造与 AI 侧 tasks/manager.py 一致的 HMAC 签名：message = timestamp + "\n" + rawBody，key = apiKey
        byte[] body = new ObjectMapper().writeValueAsBytes(request);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + "\n").getBytes(StandardCharsets.UTF_8));
        String signature = HexUtil.encodeHexStr(mac.doFinal(body));

        aiTaskService.handleCallback(request, body, timestamp, signature);

        // 条件 UPDATE 仅对非终态任务生效，且 set 的参数映射含终态值
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(taskMapper).update(isNull(), wrapperCaptor.capture());
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(AiTaskStatus.SUCCEEDED.name()), "params=" + params);

        // 成功回调写入任务结果，字段来自被抢占的任务
        ArgumentCaptor<AiTaskResultDO> resultCaptor = ArgumentCaptor.forClass(AiTaskResultDO.class);
        verify(resultMapper).insert(resultCaptor.capture());
        assertEquals(2L, resultCaptor.getValue().getTaskId());
        assertEquals(3L, resultCaptor.getValue().getTenantId());
    }

    private AiServiceConfigDO enabledConfig() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setCode("default");
        config.setEnabled(1);
        return config;
    }
}
