package cn.admin.scaffold.security;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.monitor.vo.OnlineUserVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ACCESS_KEY = "login:token:";
    private static final String REFRESH_KEY = "login:refresh:";
    private static final String BLACKLIST_KEY = "login:blacklist:";
    private static final String ONLINE_KEY = "login:online:";
    private static final String PERMS_KEY = "auth:perms:";
    /** 每用户会话集合：成员为 access jti，供「按用户吊销全部会话」（改密/重置/停用/删除）精确寻址，
     *  替代全局 KEYS 通配扫描；与 login:token:/login:refresh: 保持最终一致（登出/吊销时删除成员）。 */
    private static final String SESSION_SET_PREFIX = "auth:sessions:user:";

    /** 权限缓存基础 TTL 与抖动上限（批8e）：TTL = 基础值 + 随机 [0, 抖动上限]。 */
    private static final long PERMS_TTL_BASE_MINUTES = 30;
    private static final long PERMS_TTL_JITTER_MAX_MINUTES = 5;

    // 缓存管理页允许清理的前缀白名单：仅限可自愈的业务缓存；
    // 认证令牌/授权码/限流计数/分布式锁等关键 key 禁止通过缓存页删除，避免造成会话失效或 DoS
    private static final Set<String> CACHE_DELETE_ALLOWED_PREFIXES = Set.of(
            "captcha:", "auth:perms:", "login:online:");

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void clearPermissionCacheOnStartup() {
        evictAllPermissions();
    }

    /** 双参版（无 userId，不登记 per-user 会话集合），兼容 SSO 旧调用；见三参版说明。 */
    public void saveAccessToken(String accessJti, String refreshJti) {
        saveAccessToken(accessJti, refreshJti, null);
    }

    /**
     * 保存 access token 并登记到 per-user 会话集合（按用户吊销全部会话的依据）。
     *
     * <p>登出/吊销时从集合移除成员；集合 TTL 对齐 refresh token 有效期，即使个别成员漏删
     * 也会随集合过期自愈，不产生永久残留。
     */
    public void saveAccessToken(String accessJti, String refreshJti, String userId) {
        redisTemplate.opsForValue().set(
                ACCESS_KEY + accessJti,
                refreshJti,
                Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
        if (StringUtils.hasText(userId)) {
            registerSession(accessJti, userId);
        }
    }

    private void registerSession(String accessJti, String userId) {
        String key = SESSION_SET_PREFIX + userId;
        redisTemplate.opsForSet().add(key, accessJti);
        redisTemplate.expire(key, Duration.ofDays(properties.getRefreshTokenExpireDays()));
    }

    public void saveRefreshToken(String refreshJti, String userId) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY + refreshJti,
                userId,
                Duration.ofDays(properties.getRefreshTokenExpireDays()));
    }

    public boolean hasValidAccessToken(String accessJti) {
        Boolean exists = redisTemplate.hasKey(ACCESS_KEY + accessJti);
        Boolean blacklisted = redisTemplate.hasKey(BLACKLIST_KEY + accessJti);
        return Boolean.TRUE.equals(exists) && !Boolean.TRUE.equals(blacklisted);
    }

    /**
     * 原子消费 refresh token：GETDEL 删除并返回旧值（即签发时存入的 userId）。
     *
     * <p>返回 null 说明 token 不存在或已被并发消费，调用方应拒绝本次刷新。原
     * {@code hasRefreshToken + revokeRefreshToken} 两步存在 check-then-act 竞态：并发用同一
     * 被窃 refresh token 刷新时，两个请求均通过存在性检查后各自签发新令牌对，轮换形同虚设。
     * GETDEL 需要 Redis ≥ 6.2（部署基线为 Redis 7）。
     */
    public String consumeRefreshToken(String refreshJti) {
        String key = REFRESH_KEY + refreshJti;
        // StringRedisTemplate 默认序列化器即 UTF-8 StringRedisSerializer，直接编码等价。
        // 显式声明 RedisCallback<byte[]> 消解 execute(RedisCallback) / execute(SessionCallback)
        // 双重重载歧义；同时避免 getKeySerializer()/getValueSerializer() 泛型推断在部分
        // IDE 编译器（ECJ）下误报编译错误并产出错误类污染 target/classes。
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] raw = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().getDel(keyBytes));
        if (raw == null) {
            return null;
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * 吊销单个 access token（登出/强制下线）：删除 access+refresh 键、写入黑名单、清理在线记录，
     * 并从 per-user 会话集合移除成员。refresh token 键可能已被原子消费（轮换后旧 refresh 不存在），
     * 此时无法反查属主，成员由 revokeAllUserSessions 删除集合整体自愈，不影响正确性。
     */
    public void revokeAccessToken(String accessJti) {
        String accessKey = ACCESS_KEY + accessJti;
        String refreshJti = redisTemplate.opsForValue().get(accessKey);
        if (refreshJti != null && StringUtils.hasText(refreshJti)) {
            String ownerUserId = redisTemplate.opsForValue().get(REFRESH_KEY + refreshJti);
            redisTemplate.delete(REFRESH_KEY + refreshJti);
            if (ownerUserId != null) {
                redisTemplate.opsForSet().remove(SESSION_SET_PREFIX + ownerUserId, accessJti);
            }
        }
        boolean existed = Boolean.TRUE.equals(redisTemplate.hasKey(accessKey));
        redisTemplate.delete(accessKey);
        if (existed) {
            // access 键已不存在（过期/并发已删）时无需写黑名单：hasValidAccessToken 要求键存在
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY + accessJti,
                    "1",
                    Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
        }
        removeOnlineUser(accessJti);
    }

    /**
     * 按用户吊销全部会话：遍历 per-user 会话集合逐个吊销 access/refresh/在线记录并写黑名单，
     * 最后删除集合本身（精确寻址，替代全局 KEYS 扫描）。供本人改密（含当前会话，强制重登）、
     * 管理员重置口令/停用/删除等路径调用。
     *
     * <p>与并发刷新存在极窄竞态：SMEMBERS 快照后、新签发的令牌对可能未被吊销，将保留至其自然
     * 过期（access ≤ 2h / refresh ≤ 7d）。对改密/停用场景，旧口令已失效或账号已禁用，残留会话
     * 无法继续业务操作（认证链每请求校验账号状态），风险可控；后续若需严格即时性，可在签发路径
     * 增加会话代际号并随请求比对。
     */
    public void revokeAllUserSessions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String setKey = SESSION_SET_PREFIX + userId;
        Set<String> members = redisTemplate.opsForSet().members(setKey);
        redisTemplate.delete(setKey);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (String accessJti : members) {
            revokeAccessToken(accessJti);
        }
    }

    /** 事务提交后按用户吊销全部会话（事务内先落库、提交成功后再吊销，避免回滚导致误杀会话）。 */
    public void revokeAllUserSessionsAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        evictAfterCommit(() -> revokeAllUserSessions(String.valueOf(userId)));
    }

    public void saveOnlineUser(String accessJti, OnlineUserVo onlineUser) {
        try {
            onlineUser.setLoginTime(LocalDateTime.now());
            redisTemplate.opsForValue().set(
                    ONLINE_KEY + accessJti,
                    objectMapper.writeValueAsString(onlineUser),
                    Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save online user", exception);
        }
    }

    public void removeOnlineUser(String accessJti) {
        redisTemplate.delete(ONLINE_KEY + accessJti);
    }

    public List<OnlineUserVo> listOnlineUsers() {
        Set<String> keys = scanKeys(ONLINE_KEY + "*");
        List<OnlineUserVo> onlineUsers = new ArrayList<>(16);
        if (keys.isEmpty()) {
            return onlineUsers;
        }
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            try {
                OnlineUserVo onlineUser = objectMapper.readValue(value, new TypeReference<>() {
                });
                onlineUser.setTokenId(key.substring(ONLINE_KEY.length()));
                onlineUsers.add(onlineUser);
            } catch (JsonProcessingException ignored) {
                // skip malformed online record
            }
        }
        onlineUsers.sort(Comparator.comparing(OnlineUserVo::getLoginTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return onlineUsers;
    }

    /**
     * 清理缓存 key（支持尾部通配符 *）：仅允许白名单内的可自愈缓存前缀。
     */
    public void deleteCacheKey(String key) {
        if (!StringUtils.hasText(key)
                || CACHE_DELETE_ALLOWED_PREFIXES.stream().noneMatch(key::startsWith)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "仅允许清理白名单缓存前缀：" + String.join(", ", CACHE_DELETE_ALLOWED_PREFIXES));
        }
        if (key.endsWith("*")) {
            Set<String> keys = scanKeys(key);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } else {
            redisTemplate.delete(key);
        }
    }

    /**
     * SCAN 分批遍历匹配 key（替代阻塞式 KEYS）：KEYS 在 key 量大时会长时间阻塞 Redis 主线程，
     * 生产禁止使用。count 仅为单批提示值，遍历完整性由游标保证。
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>(64);
        redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(500).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
        return keys;
    }

    public List<String> getCachedPermissions(Long userId) {
        String value = redisTemplate.opsForValue().get(PERMS_KEY + userId);
        if (value == null || value.isBlank()) {
            return null;
        }
        return List.of(value.split(","));
    }

    public void cachePermissions(Long userId, List<String> perms) {
        // 批8e：权限缓存 TTL 加随机抖动（30min ± 0~5min）。此前所有用户权限缓存统一 30 分钟
        // 过期，登录高峰（如上班早高峰）集中写入后会在同一时刻批量过期、集中回查数据库重建，
        // 形成权限缓存雪崩。抖动仅影响各用户重建时机，不涉及正确性。
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, PERMS_TTL_JITTER_MAX_MINUTES * 60_000 + 1);
        redisTemplate.opsForValue().set(
                PERMS_KEY + userId,
                String.join(",", perms),
                Duration.ofMinutes(PERMS_TTL_BASE_MINUTES).plusMillis(jitterMillis));
    }

    // ---------- 角色缓存（与 perms 同 TTL 抖动、同失效时机）----------
    // 此前 JwtAuthenticationFilter 每请求直查 sys_user_role 取角色编码——sys_user 是全库最热表，
    // 高 QPS 下每个请求 2 次 DB 往返（角色 + 用户行）。角色随权限一并进 Redis 缓存，
    // 角色变更走既有 evictUserPermissions/evictPermissionsByUserIds 失效点（同一 key 前缀）。

    private static final String ROLES_KEY = "auth:roles:";

    public List<String> getCachedRoles(Long userId) {
        String value = redisTemplate.opsForValue().get(ROLES_KEY + userId);
        if (value == null || value.isBlank()) {
            return null;
        }
        return List.of(value.split(","));
    }

    public void cacheRoles(Long userId, List<String> roles) {
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, PERMS_TTL_JITTER_MAX_MINUTES * 60_000 + 1);
        redisTemplate.opsForValue().set(
                ROLES_KEY + userId,
                String.join(",", roles),
                Duration.ofMinutes(PERMS_TTL_BASE_MINUTES).plusMillis(jitterMillis));
    }

    /** 事务提交后失效单个用户角色+权限缓存（角色变更主路径）。 */
    public void evictUserRolesAndPermissionsAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        evictAfterCommit(() -> {
            redisTemplate.delete(ROLES_KEY + userId);
            redisTemplate.delete(PERMS_KEY + userId);
        });
    }

    /** 事务提交后批量失效角色+权限缓存（角色删除/批量授权）。 */
    public void evictRolesAndPermissionsByUserIdsAfterCommit(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> keys = userIds.stream()
                .flatMap(id -> java.util.stream.Stream.of(ROLES_KEY + id, PERMS_KEY + id))
                .toList();
        evictAfterCommit(() -> redisTemplate.delete(keys));
    }

    public void evictAllPermissions() {
        Set<String> keys = scanKeys(PERMS_KEY + "*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 事务提交后清空全部权限缓存：菜单删除/权限编码变更影响所有绑定该菜单的用户且无法精确反查，
     * 全清最安全（菜单变更低频，KEYS 扫描代价可接受）。
     */
    public void evictAllPermissionsAfterCommit() {
        evictAfterCommit(this::evictAllPermissions);
    }

    /** 精准失效单个用户的权限缓存（避免角色变更时 KEYS 全扫与全局缓存雪崩）。 */
    public void evictUserPermissions(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(PERMS_KEY + userId);
    }

    /** 批量失效指定用户的权限缓存。 */
    public void evictPermissionsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> keys = userIds.stream().map(id -> PERMS_KEY + id).toList();
        redisTemplate.delete(keys);
    }

    /**
     * 事务提交后失效单个用户权限缓存。
     *
     * <p>在事务提交前删除 Redis 键存在竞态：并发请求在 evict 之后、commit 之前读库
     * （仍是旧角色）会把旧权限重新缓存（TTL 30 分钟），撤销的权限最长残留 30 分钟。
     * 改为注册事务同步，提交成功后再删除；无事务上下文（非事务调用点）退化为立即失效。
     */
    public void evictUserPermissionsAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        evictAfterCommit(() -> redisTemplate.delete(PERMS_KEY + userId));
    }

    /** 批量版：事务提交后失效指定用户的权限缓存（语义同上）。 */
    public void evictPermissionsByUserIdsAfterCommit(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> keys = userIds.stream().map(id -> PERMS_KEY + id).toList();
        evictAfterCommit(() -> redisTemplate.delete(keys));
    }

    private void evictAfterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }
}

