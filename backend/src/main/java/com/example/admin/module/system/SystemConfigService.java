package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.common.annotation.FieldAudit;
import com.example.admin.module.system.dto.ConfigQuery;
import com.example.admin.module.system.dto.ConfigSaveRequest;
import com.example.admin.module.system.entity.SysConfigDO;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.vo.ConfigVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final int SYSTEM_CONFIG_TYPE = 1;
    private static final String CACHE_NAME = "configs";

    private final SysConfigMapper configMapper;
    private final CacheManager cacheManager;

    public PageResult<ConfigVo> page(ConfigQuery query) {
        Page<SysConfigDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysConfigDO> wrapper = new LambdaQueryWrapper<SysConfigDO>()
                .like(StringUtils.hasText(query.getConfigName()), SysConfigDO::getConfigName, query.getConfigName())
                .like(StringUtils.hasText(query.getConfigKey()), SysConfigDO::getConfigKey, query.getConfigKey())
                .orderByAsc(SysConfigDO::getId);
        IPage<SysConfigDO> result = configMapper.selectPage(page, wrapper);
        List<ConfigVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    /**
     * 缓存键含租户维度：sys_config 受租户拦截器过滤，键只取 configKey 会造成跨租户配置串扰。
     */
    @Cacheable(value = CACHE_NAME, key = "T(com.example.admin.common.TenantContext).getTenantId() + ':' + #configKey")
    public String getByKey(String configKey) {
        SysConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, configKey));
        return config == null ? null : config.getConfigValue();
    }

    public Long create(ConfigSaveRequest request) {
        checkKeyUnique(request.getConfigKey(), null);
        SysConfigDO config = toEntity(request);
        configMapper.insert(config);
        // R4-1.31：原 @CacheEvict(allEntries=true) 改任意租户配置会清空全部租户缓存，改为按键+租户精确失效
        evictConfig(request.getConfigKey());
        return config.getId();
    }

    @FieldAudit(entity = SysConfigDO.class, action = "UPDATE", module = "参数配置")
    public void update(ConfigSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "参数 ID 不能为空");
        }
        checkKeyUnique(request.getConfigKey(), request.getId());
        SysConfigDO existing = configMapper.selectById(request.getId());
        configMapper.updateById(toEntity(request));
        // configKey 可能被修改：旧键与新键一并失效，避免残留旧键缓存
        if (existing != null && StringUtils.hasText(existing.getConfigKey())
                && !existing.getConfigKey().equals(request.getConfigKey())) {
            evictConfig(existing.getConfigKey());
        }
        evictConfig(request.getConfigKey());
    }

    public void delete(Long id) {
        SysConfigDO existing = configMapper.selectById(id);
        configMapper.deleteById(id);
        if (existing != null && StringUtils.hasText(existing.getConfigKey())) {
            evictConfig(existing.getConfigKey());
        }
    }

    /** 按键 + 租户精确失效缓存（键与 {@link #getByKey} 的 @Cacheable key 一致）。 */
    private void evictConfig(String configKey) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null && StringUtils.hasText(configKey)) {
            cache.evict(TenantContext.getTenantId() + ":" + configKey.trim());
        }
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        SysConfigDO exists = configMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, configKey.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.CONFIG_KEY_EXISTS);
        }
    }

    private SysConfigDO toEntity(ConfigSaveRequest request) {
        SysConfigDO config = new SysConfigDO();
        config.setId(request.getId());
        config.setConfigName(request.getConfigName());
        config.setConfigKey(request.getConfigKey().trim());
        config.setConfigValue(request.getConfigValue());
        config.setConfigType(request.getConfigType() == null ? SYSTEM_CONFIG_TYPE : request.getConfigType());
        config.setRemark(request.getRemark());
        return config;
    }

    private ConfigVo toVo(SysConfigDO config) {
        return ConfigVo.builder()
                .id(config.getId())
                .configName(config.getConfigName())
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .configType(config.getConfigType())
                .remark(config.getRemark())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
