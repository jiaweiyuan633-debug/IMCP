package com.example.admin.module.system;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysNoticeDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeSseServiceTest {

    private static SysUserDO user(Long id, Long tenantId) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        return user;
    }

    /** 每个待连接用户的租户 = 其 id（connect 需按库表定位租户，R4-1.10）。 */
    private static NoticeSseService serviceWithUsers(Long... userIds) {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        for (Long userId : userIds) {
            when(userMapper.selectByIdIgnoreTenant(userId)).thenReturn(user(userId, userId));
        }
        return new NoticeSseService(mock(StringRedisTemplate.class), new ObjectMapper(), userMapper);
    }

    @Test
    void publishAllPublishesEnvelopeWithTenantToRedisWithoutLocalDuplicate() throws Exception {
        SysNoticeDO notice = new SysNoticeDO();
        notice.setId(5L);
        notice.setNoticeTitle("t");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        NoticeSseService service = spy(new NoticeSseService(redisTemplate, new ObjectMapper(), mock(SysUserMapper.class)));
        service.publishAll(1L, notice);
        // R4-1.10：Redis 频道消息携带权威目标租户信封；本地投递交给 Redis 监听器（含本实例），
        // 发布方不再重复投递（两个 publishLocal 重载都不触发）
        String expected = new ObjectMapper().writeValueAsString(new NoticeSseService.NoticeBroadcast(1L, notice));
        verify(redisTemplate).convertAndSend("notice:sse", expected);
        verify(service, never()).publishLocal(any(), any());
        verify(service, never()).publishLocal(any());
    }

    @Test
    void publishAllFallsBackToTenantLocalWhenRedisDown() {
        SysNoticeDO notice = new SysNoticeDO();
        notice.setNoticeTitle("t");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new DataAccessResourceFailureException("down"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());
        NoticeSseService service = spy(new NoticeSseService(redisTemplate, new ObjectMapper(), mock(SysUserMapper.class)));
        service.publishAll(1L, notice);
        verify(service).publishLocal(1L, notice);
    }

    @Test
    void connectRejectsUnknownUserWithoutRegisteringConnection() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByIdIgnoreTenant(9L)).thenReturn(null);
        NoticeSseService service = new NoticeSseService(mock(StringRedisTemplate.class), new ObjectMapper(), userMapper);

        assertThatThrownBy(() -> service.connect(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());

        // 未注册任何连接
        AtomicInteger frames = new AtomicInteger();
        service.broadcast(9L, emitter -> frames.incrementAndGet());
        assertThat(frames.get()).isZero();
    }

    @Test
    void broadcastFramesAllAliveConnectionsForUser() {
        NoticeSseService service = serviceWithUsers(1L);
        service.connect(1L);
        service.connect(1L);

        AtomicInteger frames = new AtomicInteger();
        service.broadcast(1L, emitter -> frames.incrementAndGet());

        assertThat(frames.get()).isEqualTo(2);
    }

    @Test
    void broadcastSweepsDeadConnectionWithoutAffectingAliveOnes() {
        NoticeSseService service = serviceWithUsers(1L);
        SseEmitter dead = service.connect(1L);
        service.connect(1L);

        // 目标连接发送失败（僵死/代理已断）→ 仅回收该连接，其余连接不受影响
        service.broadcast(1L, emitter -> {
            if (emitter == dead) {
                throw new IOException("broken pipe");
            }
        });

        AtomicInteger frames = new AtomicInteger();
        service.broadcast(1L, emitter -> frames.incrementAndGet());
        assertThat(frames.get()).isEqualTo(1);
    }

    @Test
    void connectEvictsOldestConnectionWhenPerUserLimitExceeded() {
        NoticeSseService service = serviceWithUsers(1L);
        service.setConnectionLimit(2);
        service.connect(1L);
        service.connect(1L);
        service.connect(1L);

        // 超限 → 回收最旧连接（列表头），仅保留最近 2 条
        AtomicInteger frames = new AtomicInteger();
        service.broadcast(1L, emitter -> frames.incrementAndGet());
        assertThat(frames.get()).isEqualTo(2);
    }

    @Test
    void connectionLimitZeroMeansUnlimited() {
        NoticeSseService service = serviceWithUsers(1L);
        service.setConnectionLimit(0);
        service.connect(1L);
        service.connect(1L);
        service.connect(1L);

        AtomicInteger frames = new AtomicInteger();
        service.broadcast(1L, emitter -> frames.incrementAndGet());
        assertThat(frames.get()).isEqualTo(3);
    }

    // ---------- R4-1.10：广播按租户过滤，公告内容不跨租户泄露 ----------

    /**
     * 用已关闭连接作「陷阱」：纯单元测试（无 HTTP 响应）下 complete() 不触发 onCompletion 回调，
     * 连接仍驻留列表，但此后 send 抛 IllegalStateException → 若被误推，broadcast 会回收它。
     * 据此可观测 publishLocal 确实只推送给目标租户的连接。
     */
    @Test
    void publishLocalDeliversOnlyToConnectionsOfTargetTenant() {
        NoticeSseService service = serviceWithUsers(1L, 2L);
        service.connect(1L); // 租户 1
        SseEmitter tenant2 = service.connect(2L); // 租户 2，关闭作陷阱
        tenant2.complete();

        service.publishLocal(1L, "hello");

        // 租户 1 的连接收到帧，未被回收
        AtomicInteger tenant1Frames = new AtomicInteger();
        service.broadcast(1L, emitter -> tenant1Frames.incrementAndGet());
        assertThat(tenant1Frames.get()).isEqualTo(1);
        // 租户 2 的连接未被触碰（若误推，send 抛 ISE → 回收 → 计数 0）
        AtomicInteger tenant2Frames = new AtomicInteger();
        service.broadcast(2L, emitter -> tenant2Frames.incrementAndGet());
        assertThat(tenant2Frames.get()).isEqualTo(1);
    }

    /** 目标租户的连接已关闭时被回收，其他租户连接不受影响。 */
    @Test
    void publishLocalSweepsClosedTargetTenantButLeavesOtherTenantAlone() {
        NoticeSseService service = serviceWithUsers(1L, 2L);
        service.connect(1L); // 租户 1
        SseEmitter tenant2 = service.connect(2L); // 租户 2
        tenant2.complete();

        service.publishLocal(2L, "hello");

        // 租户 1 不受影响
        AtomicInteger tenant1Frames = new AtomicInteger();
        service.broadcast(1L, emitter -> tenant1Frames.incrementAndGet());
        assertThat(tenant1Frames.get()).isEqualTo(1);
        // 目标租户的已关闭连接被回收
        AtomicInteger tenant2Frames = new AtomicInteger();
        service.broadcast(2L, emitter -> tenant2Frames.incrementAndGet());
        assertThat(tenant2Frames.get()).isZero();
    }
}
