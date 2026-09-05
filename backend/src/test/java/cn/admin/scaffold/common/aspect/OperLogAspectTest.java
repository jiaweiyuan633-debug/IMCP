package cn.admin.scaffold.common.aspect;

import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.notice.dto.ChannelSendRequest;
import cn.admin.scaffold.module.system.entity.SysAuditLogDO;
import cn.admin.scaffold.module.system.entity.SysOperLogDO;
import cn.admin.scaffold.module.system.mapper.SysAuditLogMapper;
import cn.admin.scaffold.module.system.mapper.SysOperLogMapper;
import cn.admin.scaffold.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperLogAspectTest {

    private final SysOperLogMapper operLogMapper = mock(SysOperLogMapper.class);
    private final SysAuditLogMapper auditLogMapper = mock(SysAuditLogMapper.class);
    private final BusinessMetrics businessMetrics = mock(BusinessMetrics.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private ProceedingJoinPoint mockJoinPoint() {
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn("cn.admin.scaffold.SomeController");
        when(signature.getName()).thenReturn("addUser");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        return joinPoint;
    }

    private OperLog mockOperLog() {
        OperLog operLog = mock(OperLog.class);
        when(operLog.module()).thenReturn("system");
        when(operLog.action()).thenReturn("addUser");
        return operLog;
    }

    /** 捕获提交到执行器的任务并手动执行，模拟异步线程落库。 */
    private TaskExecutor capturingExecutor(AtomicReference<Runnable> captured) {
        return captured::set;
    }

    @Test
    void buildEntitiesExtractsThreadLocalContext() {
        TenantContext.setTenantId(1L);
        LoginUser loginUser = LoginUser.builder().userId(42L).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/system/user/add");
        request.setRemoteAddr("1.2.3.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        OperLogAspect aspect = new OperLogAspect(operLogMapper, auditLogMapper, new ObjectMapper(), businessMetrics, task -> {
        });
        OperLogAspect.LogEntities entities = aspect.buildEntities(mockJoinPoint(), mockOperLog(), 100L, null, null);

        // 上下文必须在调用线程提取完毕：租户、用户、请求信息
        assertThat(entities.operLog().getTenantId()).isEqualTo(1L);
        assertThat(entities.operLog().getUserId()).isEqualTo(42L);
        assertThat(entities.operLog().getModule()).isEqualTo("system");
        assertThat(entities.operLog().getRequestUrl()).isEqualTo("/api/system/user/add");
        assertThat(entities.operLog().getRequestMethod()).isEqualTo("POST");
        assertThat(entities.operLog().getIp()).isEqualTo("1.2.3.4");
        assertThat(entities.operLog().getStatus()).isEqualTo(1);
        // 审计日志与操作日志同源字段一致
        assertThat(entities.auditLog().getTenantId()).isEqualTo(1L);
        assertThat(entities.auditLog().getUserId()).isEqualTo(42L);
        assertThat(entities.auditLog().getStatus()).isEqualTo(1);
    }

    @Test
    void asyncSaveLogWritesViaExecutorWithoutBlockingCaller() {
        AtomicReference<Runnable> captured = new AtomicReference<>();
        OperLogAspect aspect = new OperLogAspect(operLogMapper, auditLogMapper, new ObjectMapper(),
                businessMetrics, capturingExecutor(captured));

        aspect.asyncSaveLog(mockJoinPoint(), mockOperLog(), 100L, null, null);

        // 调用线程只提交任务，不直接写库
        verifyNoInteractions(operLogMapper);
        verifyNoInteractions(auditLogMapper);
        assertThat(captured.get()).isNotNull();
        // 异步线程落库：操作日志 + 审计日志 + 指标
        captured.get().run();
        verify(operLogMapper).insert(any(SysOperLogDO.class));
        verify(auditLogMapper).insert(any(SysAuditLogDO.class));
        verify(businessMetrics).operLogWritten();
    }

    @Test
    void asyncWriteFailureIsSwallowedAndDoesNotPropagate() {
        doThrow(new RuntimeException("db down")).when(operLogMapper).insert(any(SysOperLogDO.class));
        AtomicReference<Runnable> captured = new AtomicReference<>();
        OperLogAspect aspect = new OperLogAspect(operLogMapper, auditLogMapper, new ObjectMapper(),
                businessMetrics, capturingExecutor(captured));

        aspect.asyncSaveLog(mockJoinPoint(), mockOperLog(), 100L, null, null);
        // 异步写库失败不得向上抛出影响任何调用方
        captured.get().run();
        verify(operLogMapper).insert(any(SysOperLogDO.class));
    }

    // ---------- @OperLog.maskFields 发送类正文/参数脱敏 ----------

    /** 发送渠道消息：content/target 按注解 maskFields 整体打码入操作/审计日志，title 等定位信息保留。 */
    @Test
    void maskFieldsMaskDeclaredSendFieldsInParams() {
        OperLog operLog = mock(OperLog.class);
        when(operLog.module()).thenReturn("消息渠道");
        when(operLog.action()).thenReturn("发送渠道消息");
        when(operLog.maskFields()).thenReturn(new String[]{"content", "target"});

        Signature signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn("cn.admin.scaffold.ChannelController");
        when(signature.getName()).thenReturn("send");
        ChannelSendRequest request = new ChannelSendRequest();
        request.setChannelId(1L);
        request.setTarget("13800138000");
        request.setTitle("验证码");
        request.setContent("您的验证码是 123456");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});

        OperLogAspect aspect = new OperLogAspect(operLogMapper, auditLogMapper, new ObjectMapper(),
                businessMetrics, task -> {
        });
        OperLogAspect.LogEntities entities = aspect.buildEntities(joinPoint, operLog, 100L, null, null);

        String params = entities.operLog().getParams();
        assertThat(params).contains("\"content\":\"******\"");
        assertThat(params).contains("\"target\":\"******\"");
        assertThat(params).doesNotContain("123456");
        assertThat(params).doesNotContain("13800138000");
        assertThat(params).contains("\"title\":\"验证码\"");
        assertThat(params).contains("\"channelId\":1");
        // 审计日志与操作日志 params 同源，同样脱敏
        assertThat(entities.auditLog().getParams()).isEqualTo(params);
    }

    // ---------- 上传接口 MultipartFile 只记元信息，不序列化二进制 ----------

    /** MultipartFile 入参替换为元信息快照（文件名/大小/类型），文件内容不进入日志 JSON。 */
    @Test
    void multipartFileArgIsReducedToMetadataWithoutContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", "binary-content-xyz".getBytes());
        // buildEntities 需读 signature 的类名/方法名拼 method，复用 mockJoinPoint 的签名再覆盖入参
        ProceedingJoinPoint joinPoint = mockJoinPoint();
        when(joinPoint.getArgs()).thenReturn(new Object[]{"avatar", file});

        OperLogAspect aspect = new OperLogAspect(operLogMapper, auditLogMapper, new ObjectMapper(),
                businessMetrics, task -> {
        });
        OperLogAspect.LogEntities entities = aspect.buildEntities(joinPoint, mockOperLog(), 100L, null, null);

        String params = entities.operLog().getParams();
        assertThat(params).contains("\"originalFilename\":\"a.png\"");
        assertThat(params).contains("\"size\":18");
        assertThat(params).contains("\"contentType\":\"image/png\"");
        // 二进制内容与 base64 均不得入日志
        assertThat(params).doesNotContain("binary-content-xyz");
    }
}
