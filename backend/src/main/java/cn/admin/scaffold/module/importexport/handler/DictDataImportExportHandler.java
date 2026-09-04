package cn.admin.scaffold.module.importexport.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.importexport.ImportExportTemplateService;
import cn.admin.scaffold.module.importexport.ImportExportTemplateService.ColumnConfig;
import cn.admin.scaffold.module.system.entity.SysDictDataDO;
import cn.admin.scaffold.module.system.entity.SysDictTypeDO;
import cn.admin.scaffold.module.system.mapper.SysDictDataMapper;
import cn.admin.scaffold.module.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置「字典数据」导入导出处理器（entityKey=dict-data）。
 *
 * <p>遵循 V46 共享字典模型：导出时合并共享层（tenant_id=0）与租户私有层（私有覆盖同名 dictValue）；
 * 导入时目标类型为共享类型则写入共享层复用全局一份，否则写入当前租户私有层（与 SystemDictService 一致）。
 * 列映射 key 对应 sys_dict_data 字段：dictLabel/dictValue/dictType/dictSort/listClass/isDefault/status/remark；
 * 导入时 dictLabel/dictValue/dictType/dictSort 为必填，值缺失报 PARAM_ERROR。
 */
@Component
@RequiredArgsConstructor
public class DictDataImportExportHandler implements ImportExportHandler {

    private static final String ENTITY_KEY = "dict-data";
    private static final int SHARED = 1;

    private final SysDictDataMapper dictDataMapper;
    private final SysDictTypeMapper dictTypeMapper;

    @Override
    public String entityKey() {
        return ENTITY_KEY;
    }

    @Override
    public List<Map<String, Object>> export(Map<String, Object> queryParams, String configJson) {
        List<ColumnConfig> columns = ImportExportTemplateService.parseColumns(configJson);
        // queryParams.get 缺失时返回 null，String.valueOf(null) 会产生字符串 "null" 导致误判为有过滤条件
        Object dictTypeValue = queryParams == null ? null : queryParams.get("dictType");
        String dictType = dictTypeValue == null ? null : String.valueOf(dictTypeValue);
        // 遵循 V46 共享字典模型：共享层（tenant_id=0，绕过租户拦截器）为底座，私有层覆盖同名 dictValue
        Map<String, SysDictDataDO> merged = new LinkedHashMap<>();
        if (StringUtils.hasText(dictType)) {
            dictDataMapper.selectSharedByType(dictType).forEach(d -> merged.putIfAbsent(keyOf(d), d));
        } else {
            dictDataMapper.selectAllShared().forEach(d -> merged.putIfAbsent(keyOf(d), d));
        }
        LambdaQueryWrapper<SysDictDataDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictType)) {
            wrapper.eq(SysDictDataDO::getDictType, dictType);
        }
        wrapper.orderByAsc(SysDictDataDO::getDictSort).orderByAsc(SysDictDataDO::getId);
        dictDataMapper.selectList(wrapper).forEach(d -> merged.put(keyOf(d), d));
        List<SysDictDataDO> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparingInt(SysDictDataDO::getDictSort).thenComparingLong(SysDictDataDO::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysDictDataDO data : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (ColumnConfig column : columns) {
                row.put(column.key(), resolveValue(data, column.key()));
            }
            rows.add(row);
        }
        return rows;
    }

    private String keyOf(SysDictDataDO data) {
        return data.getDictType() + ":" + data.getDictValue();
    }

    /** 目标字典类型是否为共享类型（is_shared=1，tenant_id=0 全局一份）。 */
    private boolean isSharedType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return false;
        }
        SysDictTypeDO type = dictTypeMapper.selectByTypeIgnoreTenant(dictType.trim());
        return type != null && type.getIsShared() != null && type.getIsShared() == SHARED;
    }

    @Override
    public int importRows(List<Map<String, Object>> rows, String configJson) {
        int success = 0;
        for (Map<String, Object> row : rows) {
            dictDataMapper.insert(toEntity(row));
            success++;
        }
        return success;
    }

    private SysDictDataDO toEntity(Map<String, Object> row) {
        String label = requireValue(row, "dictLabel", "字典标签");
        String value = requireValue(row, "dictValue", "字典键值");
        String dictType = requireValue(row, "dictType", "字典类型");
        String sort = requireValue(row, "dictSort", "显示排序");
        SysDictDataDO data = new SysDictDataDO();
        // 共享类型写入共享层（tenant_id=0）复用全局一份，避免生成租户私有重复副本（与 SystemDictService.dataCreate 一致）
        data.setTenantId(isSharedType(dictType) ? Long.valueOf(0L) : TenantContext.getTenantId());
        data.setDictLabel(label);
        data.setDictValue(value);
        data.setDictType(dictType);
        data.setDictSort(parseInt(sort, "显示排序"));
        data.setListClass(optionalValue(row, "listClass"));
        data.setIsDefault(optionalInt(row, "isDefault"));
        data.setStatus(optionalInt(row, "status", 1));
        data.setRemark(optionalValue(row, "remark"));
        return data;
    }

    private Object resolveValue(SysDictDataDO data, String key) {
        return switch (key) {
            case "dictLabel" -> data.getDictLabel();
            case "dictValue" -> data.getDictValue();
            case "dictType" -> data.getDictType();
            case "dictSort" -> data.getDictSort();
            case "listClass" -> data.getListClass();
            case "isDefault" -> data.getIsDefault();
            case "status" -> data.getStatus();
            case "remark" -> data.getRemark();
            default -> null;
        };
    }

    private String requireValue(Map<String, Object> row, String key, String label) {
        Object value = row.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value).trim())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字段「" + label + "」缺失");
        }
        return String.valueOf(value).trim();
    }

    private String optionalValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer optionalInt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value).trim())) {
            return null;
        }
        return parseInt(String.valueOf(value).trim(), key);
    }

    private Integer optionalInt(Map<String, Object> row, String key, int defaultValue) {
        Object value = row.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value).trim())) {
            return defaultValue;
        }
        return parseInt(String.valueOf(value).trim(), key);
    }

    private Integer parseInt(String text, String label) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字段「" + label + "」必须为数字");
        }
    }
}
