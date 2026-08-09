package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.ConfigQuery;
import com.example.admin.module.system.dto.ConfigSaveRequest;
import com.example.admin.module.system.entity.SysConfig;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.vo.ConfigVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final int SYSTEM_CONFIG_TYPE = 1;

    private final SysConfigMapper configMapper;

    public PageResult<ConfigVo> page(ConfigQuery query) {
        Page<SysConfig> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(query.getConfigName()), SysConfig::getConfigName, query.getConfigName())
                .like(StringUtils.hasText(query.getConfigKey()), SysConfig::getConfigKey, query.getConfigKey())
                .orderByAsc(SysConfig::getId);
        IPage<SysConfig> result = configMapper.selectPage(page, wrapper);
        List<ConfigVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    @Cacheable(value = "configs", key = "#configKey")
    public String getByKey(String configKey) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
        return config == null ? null : config.getConfigValue();
    }

    @CacheEvict(value = "configs", allEntries = true)
    public Long create(ConfigSaveRequest request) {
        checkKeyUnique(request.getConfigKey(), null);
        SysConfig config = toEntity(request);
        configMapper.insert(config);
        return config.getId();
    }

    @CacheEvict(value = "configs", allEntries = true)
    public void update(ConfigSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "参数 ID 不能为空");
        }
        checkKeyUnique(request.getConfigKey(), request.getId());
        configMapper.updateById(toEntity(request));
    }

    @CacheEvict(value = "configs", allEntries = true)
    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        SysConfig exists = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.CONFIG_KEY_EXISTS);
        }
    }

    private SysConfig toEntity(ConfigSaveRequest request) {
        SysConfig config = new SysConfig();
        config.setId(request.getId());
        config.setConfigName(request.getConfigName());
        config.setConfigKey(request.getConfigKey().trim());
        config.setConfigValue(request.getConfigValue());
        config.setConfigType(request.getConfigType() == null ? SYSTEM_CONFIG_TYPE : request.getConfigType());
        config.setRemark(request.getRemark());
        return config;
    }

    private ConfigVo toVo(SysConfig config) {
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

