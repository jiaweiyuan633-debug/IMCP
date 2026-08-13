package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
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
        requirePlatformTenant();
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
        requirePlatformTenant();
        validateProvider(request.getProvider());
        SysOauthConfigDO config = toEntity(request);
        // 平台级配置归属恒为租户 1：登录链路按 provider 全局解析（OauthLoginService.requireEnabled），
        // 回调按本配置 tenant_id 路由绑定身份；此前未显式设置，靠 DB 默认落 1，现显式固化。
        config.setTenantId(1L);
        oauthConfigMapper.insert(config);
        return config.getId();
    }

    public void update(OauthConfigSaveRequest request) {
        requirePlatformTenant();
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "配置 ID 不能为空");
        }
        validateProvider(request.getProvider());
        oauthConfigMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer enabled) {
        requirePlatformTenant();
        SysOauthConfigDO config = new SysOauthConfigDO();
        config.setId(id);
        config.setEnabled(enabled);
        oauthConfigMapper.updateById(config);
    }

    public void delete(Long id) {
        requirePlatformTenant();
        oauthConfigMapper.deleteById(id);
    }

    /** 平台租户守卫：sys_oauth_config 为平台级设置，仅租户 1（平台）管理员可管理。
     *
     * 为什么不能像 sys_mcp_server 那样直接进租户白名单：登录/授权解析在匿名上下文
     * 按 provider 全局 selectOne（OauthLoginService.requireEnabled），一旦注入租户条件
     * 恒落租户 1，非平台租户配置既查不到也无法按 provider 跨租户区分——隔离只能在服务层
     * 做归属约束，而非 SQL 拦截器。
     */
    private void requirePlatformTenant() {
        if (TenantContext.getTenantId() != 1L) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
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
