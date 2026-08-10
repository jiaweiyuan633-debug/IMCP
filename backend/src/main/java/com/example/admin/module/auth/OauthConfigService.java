package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.auth.dto.OauthConfigQuery;
import com.example.admin.module.auth.dto.OauthConfigSaveRequest;
import com.example.admin.module.auth.entity.SysOauthConfigDO;
import com.example.admin.module.auth.mapper.SysOauthConfigMapper;
import com.example.admin.module.auth.vo.OauthConfigVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 第三方登录配置服务：微信/GitHub/Gitee 登录入口的启用与密钥管理。
 */
@Service
@RequiredArgsConstructor
public class OauthConfigService {

    private static final int ENABLED = 1;

    private final SysOauthConfigMapper oauthConfigMapper;

    public PageResult<OauthConfigVo> page(OauthConfigQuery query) {
        Page<SysOauthConfigDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysOauthConfigDO> wrapper = new LambdaQueryWrapper<SysOauthConfigDO>()
                .eq(StringUtils.hasText(query.getProvider()), SysOauthConfigDO::getProvider, query.getProvider())
                .eq(query.getEnabled() != null, SysOauthConfigDO::getEnabled, query.getEnabled())
                .orderByAsc(SysOauthConfigDO::getSort)
                .orderByAsc(SysOauthConfigDO::getId);
        IPage<SysOauthConfigDO> result = oauthConfigMapper.selectPage(page, wrapper);
        List<OauthConfigVo> records = result.getRecords().stream().map(c -> toVo(c)).toList();
        return PageResult.of(result, records);
    }

    public Long create(OauthConfigSaveRequest request) {
        validateProvider(request.getProvider());
        SysOauthConfigDO config = toEntity(request);
        oauthConfigMapper.insert(config);
        return config.getId();
    }

    public void update(OauthConfigSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "配置 ID 不能为空");
        }
        validateProvider(request.getProvider());
        oauthConfigMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer enabled) {
        SysOauthConfigDO config = new SysOauthConfigDO();
        config.setId(id);
        config.setEnabled(enabled);
        oauthConfigMapper.updateById(config);
    }

    public void delete(Long id) {
        oauthConfigMapper.deleteById(id);
    }

    private void validateProvider(String provider) {
        OauthProvider.fromCode(provider);
    }

    private SysOauthConfigDO toEntity(OauthConfigSaveRequest request) {
        SysOauthConfigDO config = new SysOauthConfigDO();
        config.setId(request.getId());
        config.setProvider(request.getProvider());
        config.setAppId(request.getAppId());
        config.setAppSecret(request.getAppSecret());
        config.setRedirectUri(request.getRedirectUri());
        config.setScope(request.getScope());
        config.setEnabled(request.getEnabled() == null ? ENABLED : request.getEnabled());
        config.setSort(request.getSort() == null ? 0 : request.getSort());
        config.setRemark(request.getRemark());
        return config;
    }

    private OauthConfigVo toVo(SysOauthConfigDO config) {
        return OauthConfigVo.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .providerLabel(providerLabel(config.getProvider()))
                .appId(config.getAppId())
                .appSecret(config.getAppSecret())
                .redirectUri(config.getRedirectUri())
                .scope(config.getScope())
                .enabled(config.getEnabled())
                .sort(config.getSort())
                .remark(config.getRemark())
                .createdAt(config.getCreatedAt())
                .build();
    }

    private String providerLabel(String providerCode) {
        try {
            return OauthProvider.fromCode(providerCode).getLabel();
        } catch (IllegalArgumentException exception) {
            return providerCode;
        }
    }
}
