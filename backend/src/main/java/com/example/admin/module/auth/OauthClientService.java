package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.auth.dto.OauthClientQuery;
import com.example.admin.module.auth.dto.OauthClientSaveRequest;
import com.example.admin.module.auth.entity.SysOauthClientDO;
import com.example.admin.module.auth.mapper.SysOauthClientMapper;
import com.example.admin.module.auth.vo.OauthClientVo;
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

    private final SysOauthClientMapper oauthClientMapper;

    public PageResult<OauthClientVo> page(OauthClientQuery query) {
        Page<SysOauthClientDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysOauthClientDO> wrapper = new LambdaQueryWrapper<SysOauthClientDO>()
                .like(StringUtils.hasText(query.getClientName()), SysOauthClientDO::getClientName, query.getClientName())
                .eq(query.getEnabled() != null, SysOauthClientDO::getEnabled, query.getEnabled())
                .orderByAsc(SysOauthClientDO::getSort)
                .orderByAsc(SysOauthClientDO::getId);
        IPage<SysOauthClientDO> result = oauthClientMapper.selectPage(page, wrapper);
        List<OauthClientVo> records = result.getRecords().stream().map(c -> toVo(c)).toList();
        return PageResult.of(result, records);
    }

    public Long create(OauthClientSaveRequest request) {
        SysOauthClientDO client = toEntity(request);
        oauthClientMapper.insert(client);
        return client.getId();
    }

    public void update(OauthClientSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "应用 ID 不能为空");
        }
        oauthClientMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer enabled) {
        SysOauthClientDO client = new SysOauthClientDO();
        client.setId(id);
        client.setEnabled(enabled);
        oauthClientMapper.updateById(client);
    }

    public void delete(Long id) {
        oauthClientMapper.deleteById(id);
    }

    private SysOauthClientDO toEntity(OauthClientSaveRequest request) {
        SysOauthClientDO client = new SysOauthClientDO();
        client.setId(request.getId());
        client.setClientName(request.getClientName());
        client.setClientId(request.getClientId());
        client.setClientSecret(request.getClientSecret());
        client.setRedirectUri(request.getRedirectUri());
        client.setScope(request.getScope());
        client.setEnabled(request.getEnabled() == null ? ENABLED : request.getEnabled());
        client.setSort(request.getSort() == null ? 0 : request.getSort());
        client.setRemark(request.getRemark());
        return client;
    }

    private OauthClientVo toVo(SysOauthClientDO client) {
        return OauthClientVo.builder()
                .id(client.getId())
                .clientName(client.getClientName())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .redirectUri(client.getRedirectUri())
                .scope(client.getScope())
                .enabled(client.getEnabled())
                .sort(client.getSort())
                .remark(client.getRemark())
                .createdAt(client.getCreatedAt())
                .build();
    }
}
