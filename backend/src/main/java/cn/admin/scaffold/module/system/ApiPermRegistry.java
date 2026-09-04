package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.module.system.entity.SysApiPermDO;
import cn.admin.scaffold.module.system.mapper.SysApiPermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * API 资源权限注册表：将 sys_api_perm 表加载进内存，按 method+Ant path 匹配解析出所需权限编码。
 *
 * <p>URL 层权限从"仅认证"升级为"认证 + 资源权限"：{@link cn.admin.scaffold.security.ApiPermAuthorizationFilter}
 * 对命中的 URL 校验当前用户是否持有对应权限。规则可管理端增删改并热加载；内存态用 volatile + 不可变 List 保证无锁读。
 */
@Component
@RequiredArgsConstructor
public class ApiPermRegistry {

    private final SysApiPermMapper apiPermMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 启用的规则快照（不可变，volatile 保证读线程可见）。 */
    private volatile List<Rule> rules = List.of();

    /** 记录：method 大写（* 表示任意方法）+ path 模式 + 所需权限。 */
    record Rule(String method, String pathPattern, String permCode) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    /** 周期兜底同步：管理端改库后立即 reload，此处兜底漏改导致的漂移。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduledReload() {
        reload();
    }

    /** 重载规则：失败保留旧快照，避免启动/更新瞬间权限失效。 */
    public void reload() {
        try {
            List<Rule> loaded = apiPermMapper.selectList(
                            new LambdaQueryWrapper<SysApiPermDO>().eq(SysApiPermDO::getEnabled, 1))
                    .stream()
                    .filter(p -> p.getEnabled() != null && p.getEnabled() == 1)
                    .map(p -> new Rule(
                            StringUtils.hasText(p.getMethod()) ? p.getMethod().toUpperCase(Locale.ROOT) : "*",
                            p.getPathPattern(),
                            p.getPermCode()))
                    .toList();
            this.rules = loaded;
        } catch (RuntimeException exception) {
            // 保留旧快照
        }
    }

    /** 解析请求所需权限：无匹配规则返回 null（放行，仅要求已认证）。 */
    public String resolve(String requestMethod, String path) {
        String method = requestMethod == null ? "*" : requestMethod;
        for (Rule rule : rules) {
            if (matches(rule, method, path)) {
                return rule.permCode();
            }
        }
        return null;
    }

    private boolean matches(Rule rule, String requestMethod, String path) {
        if (!"*".equals(rule.method()) && !rule.method().equalsIgnoreCase(requestMethod)) {
            return false;
        }
        return pathMatcher.match(rule.pathPattern(), path);
    }
}
