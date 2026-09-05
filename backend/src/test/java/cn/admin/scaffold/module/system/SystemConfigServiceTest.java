package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.dto.ConfigSaveRequest;
import cn.admin.scaffold.module.system.entity.SysConfigDO;
import cn.admin.scaffold.module.system.mapper.SysConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置缓存从 @CacheEvict(allEntries=true)（改任意租户配置清空全部租户缓存）
 * 收敛为按键+租户精确失效，避免跨租户无谓的缓存清空。
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SysConfigMapper configMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private SystemConfigService configService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createEvictsOnlyThatTenantConfigKey() {
        TenantContext.setTenantId(1L);
        when(configMapper.selectOne(any())).thenReturn(null);
        when(cacheManager.getCache("configs")).thenReturn(cache);

        ConfigSaveRequest request = new ConfigSaveRequest();
        request.setConfigKey("sys.site.title");
        configService.create(request);

        // 精确失效：仅本租户该键，而非清空全部租户缓存
        verify(cache).evict("1:sys.site.title");
        verify(cache, never()).clear();
    }

    @Test
    void updateEvictsConfigKey() {
        TenantContext.setTenantId(2L);
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.selectById(5L)).thenReturn(config("sys.site.title", "old"));
        when(cacheManager.getCache("configs")).thenReturn(cache);

        ConfigSaveRequest request = new ConfigSaveRequest();
        request.setId(5L);
        request.setConfigKey("sys.site.title");
        configService.update(request);

        verify(cache).evict("2:sys.site.title");
    }

    @Test
    void updateWithKeyChangeEvictsOldAndNewKey() {
        TenantContext.setTenantId(2L);
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.selectById(5L)).thenReturn(config("sys.site.old", "old"));
        when(cacheManager.getCache("configs")).thenReturn(cache);

        ConfigSaveRequest request = new ConfigSaveRequest();
        request.setId(5L);
        request.setConfigKey("sys.site.new");
        configService.update(request);

        verify(cache).evict("2:sys.site.old");
        verify(cache).evict("2:sys.site.new");
    }

    @Test
    void deleteEvictsExistingConfigKey() {
        TenantContext.setTenantId(3L);
        when(configMapper.selectById(8L)).thenReturn(config("sys.site.title", "v"));
        when(cacheManager.getCache("configs")).thenReturn(cache);

        configService.delete(8L);

        verify(configMapper).deleteById(8L);
        verify(cache).evict("3:sys.site.title");
    }

    private static SysConfigDO config(String key, String value) {
        SysConfigDO config = new SysConfigDO();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }
}
