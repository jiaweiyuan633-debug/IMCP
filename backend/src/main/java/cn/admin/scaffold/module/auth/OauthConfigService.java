package cn.admin.scaffold.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.UniqueKeyRelease;
import cn.admin.scaffold.module.auth.dto.OauthConfigQuery;
import cn.admin.scaffold.module.auth.dto.OauthConfigSaveRequest;
import cn.admin.scaffold.module.auth.entity.SysOauthConfigDO;
import cn.admin.scaffold.module.auth.mapper.SysOauthConfigMapper;
import cn.admin.scaffold.module.auth.vo.OauthConfigVo;
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

    /** 返回给前端的密钥掩码占位：编辑不重输即视为保持不变（resolveSecret 识别）。 */
    private static final String SECRET_MASK = "********";

    private final SysOauthConfigMapper oauthConfigMapper;
    private final SecretCipher secretCipher;

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
        if (!StringUtils.hasText(request.getAppSecret())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "客户端密钥不能为空");
        }
        SysOauthConfigDO config = toEntity(request);
        config.setAppSecret(secretCipher.encrypt(request.getAppSecret()));
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
        SysOauthConfigDO existing = oauthConfigMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        SysOauthConfigDO config = toEntity(request);
        // 编辑不重输密钥（空/掩码/已是密文）视为保持不变：沿用库中既有密文
        config.setAppSecret(resolveSecret(existing.getAppSecret(), request.getAppSecret()));
        oauthConfigMapper.updateById(config);
    }

    /** 新密钥 → AES-GCM 加密；空/掩码占位/已是密文 → 沿用既有存储值。 */
    private String resolveSecret(String stored, String presented) {
        if (!StringUtils.hasText(presented) || SECRET_MASK.equals(presented) || secretCipher.isEncrypted(presented)) {
            return stored;
        }
        return secretCipher.encrypt(presented);
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
        // 逻辑删除前先释放 provider 唯一键（(tenant_id, provider)）：删除后同 provider 可重新配置
        SysOauthConfigDO config = oauthConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        config.setProvider(UniqueKeyRelease.releaseCode(config.getProvider()));
        oauthConfigMapper.updateById(config);
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
        config.setRedirectUri(request.getRedirectUri());
        config.setScope(request.getScope());
        config.setEnabled(request.getEnabled() == null ? Integer.valueOf(ENABLED) : request.getEnabled());
        config.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        config.setRemark(request.getRemark());
        return config;
    }

    private OauthConfigVo toVo(SysOauthConfigDO config) {
        return OauthConfigVo.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .providerLabel(providerLabel(config.getProvider()))
                .appId(config.getAppId())
                // 密钥永不明文回传：掩码占位供前端回显，编辑不重输即保持不变
                .appSecret(config.getAppSecret() == null ? null : SECRET_MASK)
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
