package cn.admin.scaffold.module.importexport.handler;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.entity.SysDictDataDO;
import cn.admin.scaffold.module.system.entity.SysDictTypeDO;
import cn.admin.scaffold.module.system.mapper.SysDictDataMapper;
import cn.admin.scaffold.module.system.mapper.SysDictTypeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典数据导入导出处理器单测：行映射（Map → SysDictDataDO）、必填校验（PARAM_ERROR）。
 */
class DictDataImportExportHandlerTest {

    private static final String CONFIG = "{\"sheetName\":\"字典数据\",\"columns\":["
            + "{\"key\":\"dictLabel\",\"header\":\"字典标签\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictValue\",\"header\":\"字典键值\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictType\",\"header\":\"字典类型\",\"required\":true,\"dataType\":\"string\"},"
            + "{\"key\":\"dictSort\",\"header\":\"显示排序\",\"required\":true,\"dataType\":\"int\"}]}";

    private final SysDictDataMapper dictDataMapper = mock(SysDictDataMapper.class);
    private final SysDictTypeMapper dictTypeMapper = mock(SysDictTypeMapper.class);
    private final DictDataImportExportHandler handler = new DictDataImportExportHandler(dictDataMapper, dictTypeMapper);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Map<String, Object> row(String label, String value, String dictType, String sort) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dictLabel", label);
        row.put("dictValue", value);
        row.put("dictType", dictType);
        row.put("dictSort", sort);
        return row;
    }

    @Test
    void importRowsMapsToEntity() {
        int success = handler.importRows(
                List.of(row("正常", "0", "sys_status", "1"), row("停用", "1", "sys_status", "2")),
                CONFIG);

        assertEquals(2, success);
        ArgumentCaptor<SysDictDataDO> captor = ArgumentCaptor.forClass(SysDictDataDO.class);
        verify(dictDataMapper, times(2)).insert(captor.capture());
        SysDictDataDO first = captor.getAllValues().get(0);
        assertEquals("正常", first.getDictLabel());
        assertEquals("0", first.getDictValue());
        assertEquals("sys_status", first.getDictType());
        assertEquals(Integer.valueOf(1), first.getDictSort());
        assertEquals(Integer.valueOf(1), first.getStatus());
        assertEquals(1L, first.getTenantId());
    }

    @Test
    void missingLabelRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> handler.importRows(List.of(row(null, "0", "sys_status", "1")), CONFIG));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void missingSortRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> handler.importRows(List.of(row("正常", "0", "sys_status", null)), CONFIG));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void importRowsToSharedTypeWritesSharedLayer() {
        SysDictTypeDO sharedType = new SysDictTypeDO();
        sharedType.setIsShared(1);
        when(dictTypeMapper.selectByTypeIgnoreTenant("common_status")).thenReturn(sharedType);

        int success = handler.importRows(
                List.of(row("启用", "1", "common_status", "1")), CONFIG);

        assertEquals(1, success);
        ArgumentCaptor<SysDictDataDO> captor = ArgumentCaptor.forClass(SysDictDataDO.class);
        verify(dictDataMapper).insert(captor.capture());
        // 共享类型导入写入共享层（tenant_id=0），复用全局一份而非生成租户私有副本
        assertEquals(0L, captor.getValue().getTenantId());
    }

    @Test
    void exportMergesSharedLayerWithPrivateRows() {
        SysDictDataDO shared = new SysDictDataDO();
        shared.setId(1L);
        shared.setDictType("sys_status");
        shared.setDictLabel("启用");
        shared.setDictValue("0");
        shared.setDictSort(1);
        SysDictDataDO priv = new SysDictDataDO();
        priv.setId(2L);
        priv.setDictType("sys_status");
        priv.setDictLabel("停用");
        priv.setDictValue("1");
        priv.setDictSort(2);
        when(dictDataMapper.selectAllShared()).thenReturn(List.of(shared));
        when(dictDataMapper.selectList(any())).thenReturn(List.of(priv));

        List<Map<String, Object>> rows = handler.export(Map.of(), CONFIG);

        assertEquals(2, rows.size());
        assertEquals("启用", rows.get(0).get("dictLabel"));
        assertEquals("停用", rows.get(1).get("dictLabel"));
    }

    @Test
    void exportFavorsPrivateRowOnSameDictValue() {
        SysDictDataDO shared = new SysDictDataDO();
        shared.setId(1L);
        shared.setDictType("sys_status");
        shared.setDictLabel("共享层");
        shared.setDictValue("0");
        shared.setDictSort(1);
        SysDictDataDO priv = new SysDictDataDO();
        priv.setId(2L);
        priv.setDictType("sys_status");
        priv.setDictLabel("私有覆盖");
        priv.setDictValue("0");
        priv.setDictSort(1);
        when(dictDataMapper.selectAllShared()).thenReturn(List.of(shared));
        when(dictDataMapper.selectList(any())).thenReturn(List.of(priv));

        List<Map<String, Object>> rows = handler.export(Map.of(), CONFIG);

        assertEquals(1, rows.size());
        assertEquals("私有覆盖", rows.get(0).get("dictLabel"));
    }
}
