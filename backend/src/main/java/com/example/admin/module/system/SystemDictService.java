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
import com.example.admin.module.system.entity.SysDictData;
import com.example.admin.module.system.entity.SysDictType;
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

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    public PageResult<DictTypeVo> typePage(DictTypeQuery query) {
        Page<SysDictType> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .like(StringUtils.hasText(query.getDictName()), SysDictType::getDictName, query.getDictName())
                .like(StringUtils.hasText(query.getDictType()), SysDictType::getDictType, query.getDictType())
                .eq(query.getStatus() != null, SysDictType::getStatus, query.getStatus())
                .orderByAsc(SysDictType::getId);
        IPage<SysDictType> result = typeMapper.selectPage(page, wrapper);
        List<DictTypeVo> records = result.getRecords().stream().map(this::toTypeVo).toList();
        return PageResult.of(result, records);
    }

    public Long typeCreate(DictTypeSaveRequest request) {
        checkTypeUnique(request.getDictType(), null);
        SysDictType type = toTypeEntity(request);
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
        SysDictType type = typeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        typeMapper.deleteById(id);
        dataMapper.delete(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, type.getDictType()));
    }

    public PageResult<DictDataVo> dataPage(DictDataQuery query) {
        Page<SysDictData> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(StringUtils.hasText(query.getDictType()), SysDictData::getDictType, query.getDictType())
                .like(StringUtils.hasText(query.getDictLabel()), SysDictData::getDictLabel, query.getDictLabel())
                .eq(query.getStatus() != null, SysDictData::getStatus, query.getStatus())
                .orderByAsc(SysDictData::getDictSort)
                .orderByAsc(SysDictData::getId);
        IPage<SysDictData> result = dataMapper.selectPage(page, wrapper);
        List<DictDataVo> records = result.getRecords().stream().map(this::toDataVo).toList();
        return PageResult.of(result, records);
    }

    @Cacheable(value = "dictData", key = "#dictType")
    public List<DictDataVo> dataByType(String dictType) {
        return dataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictType, dictType)
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getDictSort))
                .stream()
                .map(this::toDataVo)
                .toList();
    }

    @CacheEvict(value = "dictData", allEntries = true)
    public Long dataCreate(DictDataSaveRequest request) {
        SysDictData data = toDataEntity(request);
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
        SysDictType exists = typeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.DICT_TYPE_EXISTS);
        }
    }

    private SysDictType toTypeEntity(DictTypeSaveRequest request) {
        SysDictType type = new SysDictType();
        type.setId(request.getId());
        type.setDictName(request.getDictName());
        type.setDictType(request.getDictType().trim());
        type.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        type.setRemark(request.getRemark());
        return type;
    }

    private SysDictData toDataEntity(DictDataSaveRequest request) {
        SysDictData data = new SysDictData();
        data.setId(request.getId());
        data.setDictType(request.getDictType());
        data.setDictLabel(request.getDictLabel());
        data.setDictValue(request.getDictValue());
        data.setDictSort(request.getDictSort() == null ? 0 : request.getDictSort());
        data.setListClass(request.getListClass());
        data.setIsDefault(request.getIsDefault() == null ? 0 : request.getIsDefault());
        data.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        data.setRemark(request.getRemark());
        return data;
    }

    private DictTypeVo toTypeVo(SysDictType type) {
        return DictTypeVo.builder()
                .id(type.getId())
                .dictName(type.getDictName())
                .dictType(type.getDictType())
                .status(type.getStatus())
                .remark(type.getRemark())
                .createdAt(type.getCreatedAt())
                .build();
    }

    private DictDataVo toDataVo(SysDictData data) {
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

