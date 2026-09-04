package cn.admin.scaffold.module.ai;

import cn.hutool.core.util.HexUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.ai.dto.AiCallbackRequest;
import cn.admin.scaffold.module.ai.dto.AiTaskCreateRequest;
import cn.admin.scaffold.module.ai.dto.AiTaskQuery;
import cn.admin.scaffold.module.ai.entity.AiServiceConfigDO;
import cn.admin.scaffold.module.ai.entity.AiTaskDO;
import cn.admin.scaffold.module.ai.entity.AiTaskResultDO;
import cn.admin.scaffold.module.ai.vo.AiTaskRetryResult;
import cn.admin.scaffold.module.ai.vo.AiTaskVo;
import cn.admin.scaffold.module.ai.mapper.AiServiceConfigMapper;
import cn.admin.scaffold.module.ai.mapper.AiTaskMapper;
import cn.admin.scaffold.module.ai.mapper.AiTaskResultMapper;
import cn.admin.scaffold.module.ai.manager.AiTaskManager;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.module.system.DataScopeHelper;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import cn.admin.scaffold.security.SecurityUtils;
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
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SecretCipher secretCipher;

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

        // R4-1.40：create 提交后置 QUEUED 改条件更新（前置 status=PENDING），避免与并发回调互踩
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(taskMapper).update(isNull(), wrapperCaptor.capture());
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(AiTaskStatus.QUEUED.name()), "params=" + params);
    }

    /**
     * R4-1.40：cancel 同样用条件更新（前置 status ∈ PENDING/QUEUED/RUNNING），
     * 影响 0 行即被并发抢占终态，取消无效静默返回——不得用无条件 updateById 覆盖终态。
     */
    @Test
    void cancelUsesConditionalUpdate() {
        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setTenantId(1L);
        task.setCreatedBy(1L);
        task.setStatus(AiTaskStatus.RUNNING.name());
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(dataScopeHelper.isAdmin()).thenReturn(true);

        aiTaskService.cancel(1L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(taskMapper).update(isNull(), wrapperCaptor.capture());
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertThat(params).containsValue(AiTaskStatus.CANCELLED.name());
        // 条件前置必须含非终态集合，而非无条件覆盖
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("IN");
        verify(taskMapper, never()).updateById(any(AiTaskDO.class));
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
        // R4-1.40：apiKey 落库为密文，回调 HMAC 校验前先解密回明文
        when(secretCipher.decrypt("secret")).thenReturn("secret");
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

    /**
     * R4-1.20：AI 侧失败分类（reason=timeout）必须随回调契约透传并落库 ai_task.error_type。
     * 若回退到修复前契约（回调不携带 reason），条件 UPDATE 的 set 参数中找不到 error_type 值，
     * 后端系统记录无从区分瞬时超时与确定性错误，此断言即失败。
     */
    @Test
    void callbackCarriesFailureReasonToErrorType() throws Exception {
        AiTaskDO task = new AiTaskDO();
        task.setId(3L);
        task.setTenantId(3L);
        task.setServiceCode("default");
        task.setStatus(AiTaskStatus.RUNNING.name());
        when(taskMapper.selectByTaskNoIgnoreTenant("T2")).thenReturn(task);
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setApiKey("secret");
        when(configMapper.selectOne(any())).thenReturn(config);
        when(secretCipher.decrypt("secret")).thenReturn("secret");
        when(taskMapper.update(isNull(), any(AbstractWrapper.class))).thenReturn(1);

        AiCallbackRequest request = new AiCallbackRequest();
        request.setTaskNo("T2");
        request.setStatus(AiTaskStatus.FAILED.name());
        request.setErrorType("timeout");
        request.setError("task timeout after 60s");
        request.setRetryCount(3);

        // 构造与 AI 侧 tasks/manager.py 一致的 HMAC 签名（message = timestamp + "\n" + rawBody）
        byte[] body = new ObjectMapper().writeValueAsBytes(request);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + "\n").getBytes(StandardCharsets.UTF_8));
        String signature = HexUtil.encodeHexStr(mac.doFinal(body));

        aiTaskService.handleCallback(request, body, timestamp, signature);

        // 条件 UPDATE 的 set 参数必须同时含终态值与非空 error_type 值
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(taskMapper).update(isNull(), wrapperCaptor.capture());
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(AiTaskStatus.FAILED.name()), "params=" + params);
        assertTrue(params.containsValue("timeout"), "error_type 未随条件 UPDATE 落库, params=" + params);
    }

    // ---------- R4-1.23：列表按失败分类（error_type）过滤 ----------

    /**
     * R4-1.20 落库的 error_type 分类需在任务列表可查，否则前端只能看到 status=FAILED
     * 而无法区分瞬时超时（值得重试）与确定性错误（重试无意义）。断言过滤条件注入 wrapper。
     */
    @Test
    void pageFiltersByErrorType() {
        when(taskMapper.selectPage(any(), any())).thenReturn(new Page<AiTaskDO>(1, 10));
        when(taskMapper.selectCount(any())).thenReturn(0L);

        AiTaskQuery query = new AiTaskQuery();
        query.setErrorType("timeout");
        aiTaskService.page(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AiTaskDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper<AiTaskDO> wrapper = (LambdaQueryWrapper<AiTaskDO>) captor.getValue();
        // getSqlSegment() 触发 SQL 段构建后参数表才物化；断言 error_type 条件与绑定值。
        assertThat(wrapper.getSqlSegment()).contains("error_type");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("timeout");
    }

    @Test
    void pageWithoutErrorTypeSkipsFilter() {
        when(taskMapper.selectPage(any(), any())).thenReturn(new Page<AiTaskDO>(1, 10));
        when(taskMapper.selectCount(any())).thenReturn(0L);

        aiTaskService.page(new AiTaskQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AiTaskDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper<AiTaskDO> wrapper = (LambdaQueryWrapper<AiTaskDO>) captor.getValue();
        // 未选择分类时不追加条件，避免 eq(hasText=false) 生成无意义参数
        assertThat(wrapper.getSqlSegment()).doesNotContain("error_type");
    }

    // ---------- R4-1.24：列表展示名批量解析（serviceName / createdByName） ----------

    /**
     * R4-1.24：列表须把已暴露的 serviceCode / createdBy 解析为可读展示名，否则前端只能看到
     * 无意义的服务编码与用户 ID。断言：服务名取自 ai_service_config.name，创建人姓名取自
     * sys_user.nickname，且两者均为单次批量查询（无 N+1）。
     */
    @Test
    void pageResolvesServiceAndCreatorDisplayNames() {
        Page<AiTaskDO> pageData = new Page<>(1, 10);
        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setServiceCode("default");
        task.setCreatedBy(9L);
        pageData.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(pageData);
        when(taskMapper.selectCount(any())).thenReturn(1L);

        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setCode("default");
        config.setName("文本摘要");
        when(configMapper.selectList(any())).thenReturn(List.of(config));

        SysUserDO user = new SysUserDO();
        user.setId(9L);
        user.setNickname("张三");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user));

        PageResult<AiTaskVo> result = aiTaskService.page(new AiTaskQuery());

        AiTaskVo vo = result.getRecords().get(0);
        assertThat(vo.getServiceName()).isEqualTo("文本摘要");
        assertThat(vo.getCreatedByName()).isEqualTo("张三");
        // 批量解析确为单次查询：服务名/创建人各一次，不随行数放大
        verify(configMapper).selectList(any());
        verify(userMapper).selectBatchIds(any());
    }

    /**
     * R4-1.24：服务被删或创建人被逻辑删除等未命中场景必须优雅降级——服务名回退编码，
     * 姓名保持空（前端以 '-' 兜底），不得抛异常或污染其他行。
     */
    @Test
    void pageFallsBackToRawIdsWhenLookupMisses() {
        Page<AiTaskDO> pageData = new Page<>(1, 10);
        AiTaskDO task = new AiTaskDO();
        task.setId(2L);
        task.setServiceCode("missing-code");
        task.setCreatedBy(99L);
        pageData.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(pageData);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        when(configMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());

        PageResult<AiTaskVo> result = aiTaskService.page(new AiTaskQuery());

        AiTaskVo vo = result.getRecords().get(0);
        assertThat(vo.getServiceName()).isEqualTo("missing-code");
        assertThat(vo.getCreatedByName()).isNull();
    }

    // ---------- R4-1.25：死信任务批量重试（终态失败 → AI 重新入队） ----------

    /**
     * R4-1.25：FAILED 终态任务重试须调用 AI 侧 retry 重新入队，并把本库状态条件更新回
     * QUEUED、清空 error/errorType。断言 aiTaskManager.retry 收到配置与任务号，
     * 且条件更新（前置 status=FAILED）的 set 参数含 QUEUED，避免与并发回调互踩。
     */
    @Test
    void retryResubmitsFailedTaskAndResetsStatus() {
        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setTaskNo("AI1");
        task.setServiceCode("default");
        task.setStatus(AiTaskStatus.FAILED.name());
        task.setErrorMsg("boom");
        task.setErrorType("timeout");
        when(taskMapper.selectById(1L)).thenReturn(task);
        // 重试属管理操作：显式模拟管理员数据范围上下文放行（mock 的 List 返回默认为空集合而非 null）
        when(dataScopeHelper.isAdmin()).thenReturn(true);
        when(configMapper.selectOne(any())).thenReturn(enabledConfig());
        when(taskMapper.update(isNull(), any(AbstractWrapper.class))).thenReturn(1);

        AiTaskRetryResult result = aiTaskService.retry(List.of(1L));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        verify(aiTaskManager).retry(any(), eq("AI1"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(taskMapper).update(isNull(), wrapperCaptor.capture());
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertThat(params).containsValue(AiTaskStatus.QUEUED.name());
    }

    /**
     * R4-1.25：非 FAILED 终态（如 SUCCEEDED）与已不存在的任务不应触发重试——
     * 重试语义仅针对死信失败，成功/取消/运行中任务重试无意义且会破坏状态机。
     */
    @Test
    void retrySkipsNonFailedAndMissingTasks() {
        AiTaskDO succeeded = new AiTaskDO();
        succeeded.setId(2L);
        succeeded.setTaskNo("AI2");
        succeeded.setStatus(AiTaskStatus.SUCCEEDED.name());
        when(dataScopeHelper.isAdmin()).thenReturn(true);
        when(taskMapper.selectById(2L)).thenReturn(succeeded);
        when(taskMapper.selectById(3L)).thenReturn(null);

        AiTaskRetryResult result = aiTaskService.retry(List.of(2L, 3L));

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getSkipped()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();
        verify(aiTaskManager, never()).retry(any(), any());
    }

    /**
     * R4-1.25：服务禁用（enabled=0）或 AI 调用异常（服务不可用/AI 侧任务已过期）的任务
     * 记为单条失败并收集失败 ID 供前端提示，不中断整批其余任务的重试。
     */
    @Test
    void retryCountsFailedWhenConfigUnavailableOrAiCallThrows() {
        AiTaskDO disabledTask = new AiTaskDO();
        disabledTask.setId(4L);
        disabledTask.setTaskNo("AI4");
        disabledTask.setServiceCode("disabled-code");
        disabledTask.setStatus(AiTaskStatus.FAILED.name());
        when(dataScopeHelper.isAdmin()).thenReturn(true);
        when(taskMapper.selectById(4L)).thenReturn(disabledTask);

        AiTaskDO throwTask = new AiTaskDO();
        throwTask.setId(5L);
        throwTask.setTaskNo("AI5");
        throwTask.setServiceCode("default");
        throwTask.setStatus(AiTaskStatus.FAILED.name());
        when(taskMapper.selectById(5L)).thenReturn(throwTask);

        AiServiceConfigDO disabled = new AiServiceConfigDO();
        disabled.setCode("disabled-code");
        disabled.setEnabled(0);
        when(configMapper.selectOne(any())).thenReturn(disabled, enabledConfig());
        doThrow(new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE))
                .when(aiTaskManager).retry(any(), any());

        AiTaskRetryResult result = aiTaskService.retry(List.of(4L, 5L));

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getFailedIds()).containsExactly(4L, 5L);
    }

    /**
     * R4-1.25：AI 已受理但条件更新影响 0 行（任务被并发重试/回调抢先处理）时计为跳过，
     * 不得重复入队或覆盖并发回调已写入的终态。
     */
    @Test
    void retryConditionalUpdateMissCountsAsSkipped() {
        AiTaskDO task = new AiTaskDO();
        task.setId(6L);
        task.setTaskNo("AI6");
        task.setServiceCode("default");
        task.setStatus(AiTaskStatus.FAILED.name());
        when(dataScopeHelper.isAdmin()).thenReturn(true);
        when(taskMapper.selectById(6L)).thenReturn(task);
        when(configMapper.selectOne(any())).thenReturn(enabledConfig());
        when(taskMapper.update(isNull(), any(AbstractWrapper.class))).thenReturn(0);

        AiTaskRetryResult result = aiTaskService.retry(List.of(6L));

        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
    }

    // ---------- R4-1.9：SSE 流连接访问校验（openStream） ----------

    private static SysUserDO activeUser(Long id, Long tenantId) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setStatus(1);
        return user;
    }

    private static AiTaskDO taskOf(Long id, Long tenantId, Long createdBy) {
        AiTaskDO task = new AiTaskDO();
        task.setId(id);
        task.setTenantId(tenantId);
        task.setCreatedBy(createdBy);
        return task;
    }

    @Test
    void openStreamAllowsOwnerOfTaskAndResolvesUserTenant() {
        when(userMapper.selectByIdIgnoreTenant(2L)).thenReturn(activeUser(2L, 8L));
        when(taskMapper.selectById(10L)).thenReturn(taskOf(10L, 8L, 2L));
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("user"));

        AiTaskService.TaskStreamContext context = aiTaskService.openStream(10L, 2L);

        assertThat(context.taskId()).isEqualTo(10L);
        assertThat(context.tenantId()).isEqualTo(8L);
    }

    @Test
    void openStreamAllowsAdminToViewOthersTask() {
        when(userMapper.selectByIdIgnoreTenant(2L)).thenReturn(activeUser(2L, 8L));
        when(taskMapper.selectById(10L)).thenReturn(taskOf(10L, 8L, 99L));
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("admin"));

        AiTaskService.TaskStreamContext context = aiTaskService.openStream(10L, 2L);

        assertThat(context.tenantId()).isEqualTo(8L);
    }

    @Test
    void openStreamRejectsNonOwnerNonAdmin() {
        when(userMapper.selectByIdIgnoreTenant(2L)).thenReturn(activeUser(2L, 8L));
        when(taskMapper.selectById(10L)).thenReturn(taskOf(10L, 8L, 99L));
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("user"));

        assertThatThrownBy(() -> aiTaskService.openStream(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void openStreamRejectsInactiveUser() {
        SysUserDO user = activeUser(2L, 8L);
        user.setStatus(0);
        when(userMapper.selectByIdIgnoreTenant(2L)).thenReturn(user);

        assertThatThrownBy(() -> aiTaskService.openStream(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void openStreamRejectsMissingUser() {
        when(userMapper.selectByIdIgnoreTenant(2L)).thenReturn(null);

        assertThatThrownBy(() -> aiTaskService.openStream(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void openStreamNotFoundWhenTaskOutsideUsersTenant() {
        when(userMapper.selectByIdIgnoreTenant(2L)).thenReturn(activeUser(2L, 8L));
        // 用户租户 8，但任务属于租户 9 → 租户拦截器过滤后 selectById 返回 null
        when(taskMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> aiTaskService.openStream(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.DATA_NOT_FOUND.getCode());
    }

    private AiServiceConfigDO enabledConfig() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setCode("default");
        config.setEnabled(1);
        return config;
    }
}
