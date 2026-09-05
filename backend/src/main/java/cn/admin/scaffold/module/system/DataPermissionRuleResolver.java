package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.module.system.entity.SysDataPermissionDO;
import cn.admin.scaffold.module.system.mapper.SysDataPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据权限规则缓存（行级表达式可配置化）。
 *
 * <p>DataScopeInnerInterceptor 每次查询通过 {@link #resolve(String)} 取「受控表 -> 用户关联列」映射，
 * 不再使用硬编码 if/else。映射来源为 {@code sys_data_permission} 表，由管理端维护：
 * <ul>
 *     <li>启动时（ApplicationReadyEvent）加载一次；</li>
 *     <li>管理端 CRUD 后调用 {@link #reload()} 立即生效；</li>
 *     <li>每分钟兜底刷新，保证多实例各自缓存与数据库一致。</li>
 * </ul>
 * 缓存以不可变 Map 存储，reload 整体替换，读写无锁安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionRuleResolver {

    private final SysDataPermissionMapper mapper;

    private volatile Map<String, Rule> rules = Map.of();

    /** 表->列映射规则。usernameColumn 优先于 userColumn。 */
    public record Rule(String tableName, String userColumn, String usernameColumn) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        reload();
    }

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        reload();
    }

    /** 从配置表全量重载规则并原子替换缓存；失败时保留旧缓存，避免权限短暂失效。 */
    public synchronized void reload() {
        try {
            List<SysDataPermissionDO> rows = mapper.selectList(new LambdaQueryWrapper<SysDataPermissionDO>()
                    .eq(SysDataPermissionDO::getEnabled, 1));
            Map<String, Rule> next = new HashMap<>(rows.size() * 2);
            for (SysDataPermissionDO row : rows) {
                next.put(row.getTableName().toLowerCase(Locale.ROOT),
                        new Rule(row.getTableName(), row.getUserColumn(), row.getUsernameColumn()));
            }
            this.rules = Map.copyOf(next);
            log.info("数据权限规则已重载: {} 条", next.size());
        } catch (Exception exception) {
            log.error("数据权限规则重载失败（保留旧缓存）", exception);
        }
    }

    /** 按表名（忽略大小写）取规则；未配置该表的权限时返回 null，表示不施加行级过滤。 */
    public Rule resolve(String tableName) {
        return tableName == null ? null : rules.get(tableName.toLowerCase(Locale.ROOT));
    }
}
