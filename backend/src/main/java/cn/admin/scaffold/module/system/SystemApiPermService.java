package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.system.dto.ApiPermQuery;
import cn.admin.scaffold.module.system.dto.ApiPermSaveRequest;
import cn.admin.scaffold.module.system.entity.SysApiPermDO;
import cn.admin.scaffold.module.system.mapper.SysApiPermMapper;
import cn.admin.scaffold.module.system.vo.ApiPermVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * API 资源权限映射管理：CRUD + 热重载注册表。
 * 变更立即 {@link ApiPermRegistry#reload()}，URL 层权限无需重启即生效。
 */
@Service
@RequiredArgsConstructor
public class SystemApiPermService {

    private static final int ENABLED = 1;

    private final SysApiPermMapper apiPermMapper;
    private final ApiPermRegistry apiPermRegistry;

    public PageResult<ApiPermVo> page(ApiPermQuery query) {
        Page<SysApiPermDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysApiPermDO> wrapper = new LambdaQueryWrapper<SysApiPermDO>()
                .eq(StringUtils.hasText(query.getMethod()), SysApiPermDO::getMethod,
                        StringUtils.hasText(query.getMethod()) ? query.getMethod().toUpperCase(Locale.ROOT) : null)
                .like(StringUtils.hasText(query.getPathPattern()), SysApiPermDO::getPathPattern, query.getPathPattern())
                .eq(query.getEnabled() != null, SysApiPermDO::getEnabled, query.getEnabled())
                .orderByAsc(SysApiPermDO::getId);
        IPage<SysApiPermDO> result = apiPermMapper.selectPage(page, wrapper);
        List<ApiPermVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public Long create(ApiPermSaveRequest request) {
        checkUnique(request.getMethod(), request.getPathPattern(), null);
        SysApiPermDO entity = toEntity(request);
        apiPermMapper.insert(entity);
        apiPermRegistry.reload();
        return entity.getId();
    }

    public void update(ApiPermSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "映射 ID 不能为空");
        }
        checkUnique(request.getMethod(), request.getPathPattern(), request.getId());
        apiPermMapper.updateById(toEntity(request));
        apiPermRegistry.reload();
    }

    public void delete(Long id) {
        apiPermMapper.deleteById(id);
        apiPermRegistry.reload();
    }

    public void reload() {
        apiPermRegistry.reload();
    }

    private void checkUnique(String method, String pathPattern, Long excludeId) {
        SysApiPermDO exists = apiPermMapper.selectOne(new LambdaQueryWrapper<SysApiPermDO>()
                .eq(SysApiPermDO::getMethod, method.toUpperCase(Locale.ROOT))
                .eq(SysApiPermDO::getPathPattern, pathPattern));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "方法+路径映射已存在");
        }
    }

    private SysApiPermDO toEntity(ApiPermSaveRequest request) {
        SysApiPermDO perm = new SysApiPermDO();
        perm.setId(request.getId());
        perm.setMethod(request.getMethod().toUpperCase(Locale.ROOT));
        perm.setPathPattern(request.getPathPattern());
        perm.setPermCode(request.getPermCode());
        perm.setEnabled(request.getEnabled() == null ? Integer.valueOf(ENABLED) : request.getEnabled());
        perm.setRemark(request.getRemark());
        return perm;
    }

    private ApiPermVo toVo(SysApiPermDO perm) {
        return ApiPermVo.builder()
                .id(perm.getId())
                .method(perm.getMethod())
                .pathPattern(perm.getPathPattern())
                .permCode(perm.getPermCode())
                .enabled(perm.getEnabled())
                .remark(perm.getRemark())
                .createdAt(perm.getCreatedAt())
                .build();
    }
}
