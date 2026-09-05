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
import cn.admin.scaffold.module.auth.dto.OauthClientQuery;
import cn.admin.scaffold.module.auth.dto.OauthClientSaveRequest;
import cn.admin.scaffold.module.auth.entity.SysOauthClientDO;
import cn.admin.scaffold.module.auth.mapper.SysOauthClientMapper;
import cn.admin.scaffold.module.auth.vo.OauthClientVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SSO 应用服务：本平台作为 OAuth2 授权服务时注册的客户端管理。
 */
@Service
@RequiredArgsConstructor
public class OauthClientService {

    private static final int ENABLED = 1;

    /** 返回给前端的密钥掩码占位：列表/编辑回显恒为该值，编辑不重输即视为保持不变（resolveSecret 识别）。 */
    private static final String SECRET_MASK = "********";

    private final SysOauthClientMapper oauthClientMapper;
    private final SecretCipher secretCipher;

    public PageResult<OauthClientVo> page(OauthClientQuery query) {
        Page<SysOauthClientDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysOauthClientDO> wrapper = new LambdaQueryWrapper<SysOauthClientDO>()
                .eq(SysOauthClientDO::getTenantId, TenantContext.getTenantId())
                .like(StringUtils.hasText(query.getClientName()), SysOauthClientDO::getClientName, query.getClientName())
                .eq(query.getEnabled() != null, SysOauthClientDO::getEnabled, query.getEnabled())
                .orderByAsc(SysOauthClientDO::getSort)
                .orderByAsc(SysOauthClientDO::getId);
        IPage<SysOauthClientDO> result = oauthClientMapper.selectPage(page, wrapper);
        List<OauthClientVo> records = result.getRecords().stream().map(c -> toVo(c)).toList();
        return PageResult.of(result, records);
    }

    public Long create(OauthClientSaveRequest request) {
        // client_id 跨租户全局唯一：SSO 授权链路匿名按 client_id 解析（SsoAuthService.requireEnabledClient），
        // 两租户重名会 selectOne 抛 TooManyResultsException——创建时即拦截，避免数据模型允许但运行时必然炸。
        ensureClientIdUnique(request.getClientId(), null);
        if (!StringUtils.hasText(request.getClientSecret())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "client_secret 不能为空");
        }
        SysOauthClientDO client = toEntity(request);
        client.setClientSecret(secretCipher.encrypt(request.getClientSecret()));
        client.setTenantId(TenantContext.getTenantId());
        oauthClientMapper.insert(client);
        return client.getId();
    }

    public void update(OauthClientSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "应用 ID 不能为空");
        }
        SysOauthClientDO existing = requireOwned(request.getId());
        ensureClientIdUnique(request.getClientId(), request.getId());
        SysOauthClientDO client = toEntity(request);
        // 编辑不重输密钥（空/掩码/已是密文）视为保持不变：沿用库中既有密文，避免明文覆盖或双次加密
        client.setClientSecret(resolveSecret(existing.getClientSecret(), request.getClientSecret()));
        oauthClientMapper.updateById(client);
    }

    /** 新密钥 → AES-GCM 加密；空/掩码占位/已是密文 → 沿用既有存储值（无前缀存量明文在此自动升级为密文）。 */
    private String resolveSecret(String stored, String presented) {
        if (!StringUtils.hasText(presented) || SECRET_MASK.equals(presented) || secretCipher.isEncrypted(presented)) {
            return stored;
        }
        return secretCipher.encrypt(presented);
    }

    public void updateStatus(Long id, Integer enabled) {
        requireOwned(id);
        SysOauthClientDO client = new SysOauthClientDO();
        client.setId(id);
        client.setEnabled(enabled);
        oauthClientMapper.updateById(client);
    }

    public void delete(Long id) {
        // requireOwned 已确认租户归属；删除前释放 client_id 唯一键（(tenant_id, client_id)），
        // 删除后同 client_id 可重新注册（已签发的授权码/票据在 Redis 且短 TTL，随其自然过期）
        SysOauthClientDO client = requireOwned(id);
        client.setClientId(UniqueKeyRelease.releaseCode(client.getClientId()));
        oauthClientMapper.updateById(client);
        oauthClientMapper.deleteById(id);
    }

    /** 归属校验：按 id 的写操作前先确认行属于当前租户，跨租户视为不存在（不暴露存在性）。 */
    private SysOauthClientDO requireOwned(Long id) {
        SysOauthClientDO client = oauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientDO>()
                .eq(SysOauthClientDO::getId, id)
                .eq(SysOauthClientDO::getTenantId, TenantContext.getTenantId()));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return client;
    }

    private void ensureClientIdUnique(String clientId, Long excludeId) {
        Long count = oauthClientMapper.selectCount(new LambdaQueryWrapper<SysOauthClientDO>()
                .eq(SysOauthClientDO::getClientId, clientId)
                .ne(excludeId != null, SysOauthClientDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "client_id 已被其他应用占用");
        }
    }

    private SysOauthClientDO toEntity(OauthClientSaveRequest request) {
        SysOauthClientDO client = new SysOauthClientDO();
        client.setId(request.getId());
        client.setClientName(request.getClientName());
        client.setClientId(request.getClientId());
        client.setRedirectUri(request.getRedirectUri());
        client.setScope(request.getScope());
        client.setEnabled(request.getEnabled() == null ? Integer.valueOf(ENABLED) : request.getEnabled());
        client.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        client.setRemark(request.getRemark());
        return client;
    }

    private OauthClientVo toVo(SysOauthClientDO client) {
        return OauthClientVo.builder()
                .id(client.getId())
                .clientName(client.getClientName())
                .clientId(client.getClientId())
                // 密钥永不明文回传：掩码占位供前端回显，编辑不重输即保持不变
                .clientSecret(client.getClientSecret() == null ? null : SECRET_MASK)
                .redirectUri(client.getRedirectUri())
                .scope(client.getScope())
                .enabled(client.getEnabled())
                .sort(client.getSort())
                .remark(client.getRemark())
                .createdAt(client.getCreatedAt())
                .build();
    }
}
