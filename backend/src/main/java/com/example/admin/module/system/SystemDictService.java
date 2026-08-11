package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.dto.DictDataQuery;
import com.example.admin.module.system.dto.DictDataSaveRequest;
import com.example.admin.module.system.dto.DictTypeQuery;
import com.example.admin.module.system.dto.DictTypeSaveRequest;
import com.example.admin.module.system.entity.SysDictDataDO;
import com.example.admin.module.system.entity.SysDictTypeDO;
import com.example.admin.module.system.mapper.SysDictDataMapper;
import com.example.admin.module.system.mapper.SysDictTypeMapper;
import com.example.admin.module.system.vo.DictDataVo;
import com.example.admin.module.system.vo.DictTypeVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典服务（租户深化）：
 * <ul>
 *   <li><b>共享字典</b>：{@code sys_dict_type.is_shared=1} 表示 tenant_id=0 全局一份，所有租户可读；</li>
 *   <li><b>覆盖模型</b>：{@link #dataByType} 先取共享层，再以租户私有数据覆盖同名 dict_value；</li>
 *   <li><b>租户粒度缓存失效</b>：私有数据变更仅失效本租户缓存，共享数据变更清空全租户缓存。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SystemDictService {

    private static final int ENABLED = 1;
    private static final int SHARED = 1;
    private static final int DEFAULT_SORT = 0;
    private static final int NOT_DEFAULT = 0;
    private static final String CACHE_NAME = "dictData";

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;
    private final CacheManager cacheManager;

    public PageResult<DictTypeVo> typePage(DictTypeQuery query) {
        Page<SysDictTypeDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysDictTypeDO> wrapper = new LambdaQueryWrapper<SysDictTypeDO>()
                .like(StringUtils.hasText(query.getDictName()), SysDictTypeDO::getDictName, query.getDictName())
                .like(StringUtils.hasText(query.getDictType()), SysDictTypeDO::getDictType, query.getDictType())
                .eq(query.getStatus() != null, SysDictTypeDO::getStatus, query.getStatus())
                .orderByAsc(SysDictTypeDO::getId);
        IPage<SysDictTypeDO> result = typeMapper.selectPage(page, wrapper);

        // 合并全局共享类型（tenant_id=0，绕过租户拦截器）：共享在前，租户私有在后
        List<SysDictTypeDO> shared = typeMapper.selectSharedTypes().stream()
                .filter(t -> matchesTypeQuery(t, query))
                .toList();
        List<DictTypeVo> records = new ArrayList<>(shared.size() + result.getRecords().size());
        shared.forEach(t -> records.add(toTypeVo(t)));
        result.getRecords().forEach(t -> records.add(toTypeVo(t)));
        return new PageResult<>(records, result.getTotal() + shared.size(), page.getCurrent(), page.getSize());
    }

    public Long typeCreate(DictTypeSaveRequest request) {
        boolean shared = isSharedRequest(request);
        checkTypeUnique(request.getDictType(), null, shared);
        SysDictTypeDO type = toTypeEntity(request);
        if (shared) {
            type.setTenantId(0L);
        }
        try {
            typeMapper.insert(type);
        } catch (DuplicateKeyException exception) {
            // 并发同类型创建：预检通过但唯一键先被他人占用，转精确业务码而非泛化 500
            throw new BusinessException(ResultCode.DICT_TYPE_EXISTS);
        }
        return type.getId();
    }

    public void typeUpdate(DictTypeSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字典类型 ID 不能为空");
        }
        boolean shared = isSharedRequest(request) || isSharedType(request.getDictType());
        checkTypeUnique(request.getDictType(), request.getId(), shared);
        SysDictTypeDO type = toTypeEntity(request);
        if (shared) {
            type.setTenantId(0L);
            typeMapper.updateByIdIgnoreTenant(type);
            evictAllDictCache();
        } else {
            typeMapper.updateById(type);
            evictDictCache(type.getDictType());
        }
    }

    @Transactional
    public void typeDelete(Long id) {
        SysDictTypeDO type = typeMapper.selectById(id);
        if (type != null) {
            typeMapper.deleteById(id);
            dataMapper.delete(new LambdaQueryWrapper<SysDictDataDO>()
                    .eq(SysDictDataDO::getDictType, type.getDictType()));
            evictDictCache(type.getDictType());
            return;
        }
        // 当前租户查不到：可能是共享类型（tenant_id=0 被租户拦截），按共享路径删除
        SysDictTypeDO shared = typeMapper.selectByIdIgnoreTenant(id);
        if (shared == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        typeMapper.deleteByIdIgnoreTenant(id);
        dataMapper.deleteSharedByType(shared.getDictType());
        // 共享类型影响所有租户
        evictAllDictCache();
    }

    public PageResult<DictDataVo> dataPage(DictDataQuery query) {
        Page<SysDictDataDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysDictDataDO> wrapper = new LambdaQueryWrapper<SysDictDataDO>()
                .eq(StringUtils.hasText(query.getDictType()), SysDictDataDO::getDictType, query.getDictType())
                .like(StringUtils.hasText(query.getDictLabel()), SysDictDataDO::getDictLabel, query.getDictLabel())
                .eq(query.getStatus() != null, SysDictDataDO::getStatus, query.getStatus())
                .orderByAsc(SysDictDataDO::getDictSort)
                .orderByAsc(SysDictDataDO::getId);
        IPage<SysDictDataDO> result = dataMapper.selectPage(page, wrapper);
        List<DictDataVo> records = result.getRecords().stream().map(this::toDataVo).toList();
        return PageResult.of(result, records);
    }

    /**
     * 按类型取字典数据（覆盖模型）：共享层（tenant_id=0）为底座，租户私有数据覆盖同名 dict_value。
     * 缓存键含租户维度，避免跨租户串扰；共享数据变更时清空全部租户缓存。
     */
    @Cacheable(value = CACHE_NAME, key = "T(com.example.admin.common.TenantContext).getTenantId() + ':' + #dictType")
    public List<DictDataVo> dataByType(String dictType) {
        Map<String, DictDataVo> merged = new LinkedHashMap<>();
        dataMapper.selectSharedByType(dictType).stream()
                .map(this::toDataVo)
                .forEach(v -> merged.putIfAbsent(v.getDictValue(), v));
        dataMapper.selectList(new LambdaQueryWrapper<SysDictDataDO>()
                        .eq(SysDictDataDO::getDictType, dictType)
                        .eq(SysDictDataDO::getStatus, ENABLED)
                        .orderByAsc(SysDictDataDO::getDictSort))
                .stream()
                .map(this::toDataVo)
                .forEach(v -> merged.put(v.getDictValue(), v));
        return new ArrayList<>(merged.values());
    }

    public Long dataCreate(DictDataSaveRequest request) {
        SysDictDataDO data = toDataEntity(request);
        if (isSharedType(request.getDictType())) {
            data.setTenantId(0L);
        }
        dataMapper.insert(data);
        evictDictCache(request.getDictType());
        return data.getId();
    }

    public void dataUpdate(DictDataSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字典数据 ID 不能为空");
        }
        SysDictDataDO data = toDataEntity(request);
        if (isSharedType(request.getDictType())) {
            data.setTenantId(0L);
            dataMapper.updateByIdIgnoreTenant(data);
        } else {
            dataMapper.updateById(data);
        }
        evictDictCache(request.getDictType());
    }

    public void dataDelete(Long id) {
        SysDictDataDO data = dataMapper.selectById(id);
        if (data != null) {
            dataMapper.deleteById(id);
            evictDictCache(data.getDictType());
            return;
        }
        // 当前租户查不到：可能是共享数据（tenant_id=0 被租户拦截），按共享路径删除
        SysDictDataDO shared = dataMapper.selectByIdIgnoreTenant(id);
        if (shared == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        dataMapper.deleteByIdIgnoreTenant(id);
        evictDictCache(shared.getDictType());
    }

    private boolean matchesTypeQuery(SysDictTypeDO type, DictTypeQuery query) {
        return (StringUtils.hasText(query.getDictName())
                && !type.getDictName().contains(query.getDictName())) ? false
                : (StringUtils.hasText(query.getDictType())
                && !type.getDictType().contains(query.getDictType())) ? false
                : query.getStatus() == null || query.getStatus().equals(type.getStatus());
    }

    private void checkTypeUnique(String dictType, Long excludeId, boolean shared) {
        String type = dictType.trim();
        if (shared) {
            // 共享类型全局唯一
            SysDictTypeDO exists = typeMapper.selectByTypeIgnoreTenant(type);
            if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
                throw new BusinessException(ResultCode.DICT_TYPE_EXISTS);
            }
        } else {
            // 私有类型租户内唯一（租户拦截器自动限定当前租户）
            SysDictTypeDO exists = typeMapper.selectOne(new LambdaQueryWrapper<SysDictTypeDO>()
                    .eq(SysDictTypeDO::getDictType, type));
            if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
                throw new BusinessException(ResultCode.DICT_TYPE_EXISTS);
            }
        }
    }

    private boolean isSharedRequest(DictTypeSaveRequest request) {
        return request.getIsShared() != null && request.getIsShared() == SHARED;
    }

    private boolean isSharedType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return false;
        }
        SysDictTypeDO type = typeMapper.selectByTypeIgnoreTenant(dictType.trim());
        return type != null && type.getIsShared() != null && type.getIsShared() == SHARED;
    }

    /**
     * 租户粒度缓存失效：私有数据仅失效本租户该类型的缓存；共享数据失效全部租户缓存。
     */
    private void evictDictCache(String dictType) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return;
        }
        if (isSharedType(dictType)) {
            cache.clear();
        } else {
            cache.evict(TenantContext.getTenantId() + ":" + dictType);
        }
    }

    private void evictAllDictCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    private SysDictTypeDO toTypeEntity(DictTypeSaveRequest request) {
        SysDictTypeDO type = new SysDictTypeDO();
        type.setId(request.getId());
        type.setDictName(request.getDictName());
        type.setDictType(request.getDictType().trim());
        type.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        type.setIsShared(request.getIsShared() == null ? 0 : request.getIsShared());
        type.setRemark(request.getRemark());
        return type;
    }

    private SysDictDataDO toDataEntity(DictDataSaveRequest request) {
        SysDictDataDO data = new SysDictDataDO();
        data.setId(request.getId());
        data.setDictType(request.getDictType());
        data.setDictLabel(request.getDictLabel());
        data.setDictValue(request.getDictValue());
        data.setDictSort(request.getDictSort() == null ? DEFAULT_SORT : request.getDictSort());
        data.setListClass(request.getListClass());
        data.setIsDefault(request.getIsDefault() == null ? NOT_DEFAULT : request.getIsDefault());
        data.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        data.setRemark(request.getRemark());
        return data;
    }

    private DictTypeVo toTypeVo(SysDictTypeDO type) {
        return DictTypeVo.builder()
                .id(type.getId())
                .dictName(type.getDictName())
                .dictType(type.getDictType())
                .status(type.getStatus())
                .isShared(type.getIsShared())
                .remark(type.getRemark())
                .createdAt(type.getCreatedAt())
                .build();
    }

    private DictDataVo toDataVo(SysDictDataDO data) {
        return DictDataVo.builder()
                .id(data.getId())
                .dictType(data.getDictType())
                .dictLabel(data.getDictLabel())
                .dictValue(data.getDictValue())
                .dictSort(data.getDictSort())
                .listClass(data.getListClass())
                .isDefault(data.getIsDefault())
                .status(data.getStatus())
                .remark(data.getRemark())
                .createdAt(data.getCreatedAt())
                .build();
    }
}
