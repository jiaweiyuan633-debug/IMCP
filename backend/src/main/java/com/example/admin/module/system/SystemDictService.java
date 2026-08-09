package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
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
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemDictService {

    private static final int ENABLED = 1;
    private static final int DEFAULT_SORT = 0;
    private static final int NOT_DEFAULT = 0;

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    public PageResult<DictTypeVo> typePage(DictTypeQuery query) {
        Page<SysDictTypeDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysDictTypeDO> wrapper = new LambdaQueryWrapper<SysDictTypeDO>()
                .like(StringUtils.hasText(query.getDictName()), SysDictTypeDO::getDictName, query.getDictName())
                .like(StringUtils.hasText(query.getDictType()), SysDictTypeDO::getDictType, query.getDictType())
                .eq(query.getStatus() != null, SysDictTypeDO::getStatus, query.getStatus())
                .orderByAsc(SysDictTypeDO::getId);
        IPage<SysDictTypeDO> result = typeMapper.selectPage(page, wrapper);
        List<DictTypeVo> records = result.getRecords().stream().map(this::toTypeVo).toList();
        return PageResult.of(result, records);
    }

    public Long typeCreate(DictTypeSaveRequest request) {
        checkTypeUnique(request.getDictType(), null);
        SysDictTypeDO type = toTypeEntity(request);
        typeMapper.insert(type);
        return type.getId();
    }

    public void typeUpdate(DictTypeSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字典类型 ID 不能为空");
        }
        checkTypeUnique(request.getDictType(), request.getId());
        typeMapper.updateById(toTypeEntity(request));
    }

    @Transactional
    public void typeDelete(Long id) {
        SysDictTypeDO type = typeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        typeMapper.deleteById(id);
        dataMapper.delete(new LambdaQueryWrapper<SysDictDataDO>()
                .eq(SysDictDataDO::getDictType, type.getDictType()));
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

    @Cacheable(value = "dictData", key = "#dictType")
    public List<DictDataVo> dataByType(String dictType) {
        return dataMapper.selectList(new LambdaQueryWrapper<SysDictDataDO>()
                        .eq(SysDictDataDO::getDictType, dictType)
                        .eq(SysDictDataDO::getStatus, ENABLED)
                        .orderByAsc(SysDictDataDO::getDictSort))
                .stream()
                .map(this::toDataVo)
                .toList();
    }

    @CacheEvict(value = "dictData", allEntries = true)
    public Long dataCreate(DictDataSaveRequest request) {
        SysDictDataDO data = toDataEntity(request);
        dataMapper.insert(data);
        return data.getId();
    }

    @CacheEvict(value = "dictData", allEntries = true)
    public void dataUpdate(DictDataSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字典数据 ID 不能为空");
        }
        dataMapper.updateById(toDataEntity(request));
    }

    @CacheEvict(value = "dictData", allEntries = true)
    public void dataDelete(Long id) {
        dataMapper.deleteById(id);
    }

    private void checkTypeUnique(String dictType, Long excludeId) {
        SysDictTypeDO exists = typeMapper.selectOne(new LambdaQueryWrapper<SysDictTypeDO>()
                .eq(SysDictTypeDO::getDictType, dictType.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.DICT_TYPE_EXISTS);
        }
    }

    private SysDictTypeDO toTypeEntity(DictTypeSaveRequest request) {
        SysDictTypeDO type = new SysDictTypeDO();
        type.setId(request.getId());
        type.setDictName(request.getDictName());
        type.setDictType(request.getDictType().trim());
        type.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
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

