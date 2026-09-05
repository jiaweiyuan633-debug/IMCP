package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.dto.DictDataSaveRequest;
import cn.admin.scaffold.module.system.dto.DictTypeQuery;
import cn.admin.scaffold.module.system.entity.SysDictDataDO;
import cn.admin.scaffold.module.system.entity.SysDictTypeDO;
import cn.admin.scaffold.module.system.mapper.SysDictDataMapper;
import cn.admin.scaffold.module.system.mapper.SysDictTypeMapper;
import cn.admin.scaffold.module.system.vo.DictDataVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典租户深化测试：共享字典覆盖模型 + 租户粒度缓存失效。
 */
class SystemDictServiceTest {

    private SysDictTypeMapper typeMapper;
    private SysDictDataMapper dataMapper;
    private CacheManager cacheManager;
    private Cache cache;
    private SystemDictService service;

    @BeforeEach
    void setUp() {
        typeMapper = mock(SysDictTypeMapper.class);
        dataMapper = mock(SysDictDataMapper.class);
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache("dictData")).thenReturn(cache);
        service = new SystemDictService(typeMapper, dataMapper, cacheManager);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SysDictDataDO data(Long id, String dictType, String label, String value) {
        SysDictDataDO data = new SysDictDataDO();
        data.setId(id);
        data.setDictType(dictType);
        data.setDictLabel(label);
        data.setDictValue(value);
        data.setStatus(1);
        data.setDictSort(1);
        return data;
    }

    private SysDictTypeDO sharedType(String dictType) {
        SysDictTypeDO type = new SysDictTypeDO();
        type.setDictType(dictType);
        type.setIsShared(1);
        type.setStatus(1);
        return type;
    }

    @Test
    void dataByTypeOverridesSharedWithTenantValues() {
        when(dataMapper.selectSharedByType("common_status")).thenReturn(
                List.of(data(1L, "common_status", "共享-启用", "1"),
                        data(2L, "common_status", "共享-停用", "0")));
        when(dataMapper.selectList(any())).thenReturn(
                List.of(data(3L, "common_status", "租户-启用", "1")));

        List<DictDataVo> result = service.dataByType("common_status");

        assertThat(result).hasSize(2);
        // 租户覆盖共享：dictValue=1 显示租户标签；共享的 0 保留
        assertThat(result.get(0).getDictLabel()).isEqualTo("租户-启用");
        assertThat(result.get(1).getDictLabel()).isEqualTo("共享-停用");
    }

    @Test
    void dataByTypeFallsBackToSharedWhenNoTenantData() {
        when(dataMapper.selectSharedByType("common_status")).thenReturn(
                List.of(data(1L, "common_status", "共享-启用", "1")));
        when(dataMapper.selectList(any())).thenReturn(new ArrayList<>());

        List<DictDataVo> result = service.dataByType("common_status");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDictLabel()).isEqualTo("共享-启用");
    }

    @Test
    void dataByTypeReturnsTenantOnlyWhenNoSharedLayer() {
        when(dataMapper.selectSharedByType("private_status")).thenReturn(new ArrayList<>());
        when(dataMapper.selectList(any())).thenReturn(
                List.of(data(3L, "private_status", "私有-启用", "1")));

        List<DictDataVo> result = service.dataByType("private_status");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDictLabel()).isEqualTo("私有-启用");
    }

    @Test
    void dataCreateForSharedTypeWritesTenantZeroAndClearsAllCache() {
        when(typeMapper.selectByTypeIgnoreTenant("common_status")).thenReturn(sharedType("common_status"));
        when(dataMapper.insert(any(SysDictDataDO.class))).thenAnswer(invocation -> {
            ((SysDictDataDO) invocation.getArgument(0)).setId(10L);
            return 1;
        });

        service.dataCreate(dataRequest("common_status", "启用", "1"));

        ArgumentCaptor<SysDictDataDO> captor = ArgumentCaptor.forClass(SysDictDataDO.class);
        verify(dataMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(0L);
        verify(cache).clear();
    }

    @Test
    void dataCreateForPrivateTypeEvictsOnlyCurrentTenantKey() {
        when(typeMapper.selectByTypeIgnoreTenant("private_status")).thenReturn(null);
        when(dataMapper.insert(any(SysDictDataDO.class))).thenAnswer(invocation -> {
            ((SysDictDataDO) invocation.getArgument(0)).setId(11L);
            return 1;
        });

        service.dataCreate(dataRequest("private_status", "启用", "1"));

        verify(cache).evict("1:private_status");
        verify(cache, never()).clear();
    }

    @Test
    void dataDeleteSharedDataUsesIgnoreTenantPath() {
        // 当前租户查不到（共享数据被租户拦截），走共享路径
        when(dataMapper.selectById(1L)).thenReturn(null);
        SysDictDataDO shared = data(1L, "common_status", "共享-启用", "1");
        when(dataMapper.selectByIdIgnoreTenant(1L)).thenReturn(shared);
        when(typeMapper.selectByTypeIgnoreTenant("common_status")).thenReturn(sharedType("common_status"));

        service.dataDelete(1L);

        verify(dataMapper).deleteByIdIgnoreTenant(1L);
        verify(dataMapper, never()).deleteById(any(Serializable.class));
        verify(cache).clear();
    }

    @Test
    void dataDeletePrivateDataEvictsTenantKey() {
        SysDictDataDO data = data(2L, "private_status", "私有-启用", "1");
        when(dataMapper.selectById(2L)).thenReturn(data);
        when(typeMapper.selectByTypeIgnoreTenant("private_status")).thenReturn(null);

        service.dataDelete(2L);

        verify(dataMapper).deleteById(any(Serializable.class));
        verify(cache).evict("1:private_status");
    }

    @Test
    void typePageMergesSharedTypesBeforeTenantTypes() {
        when(typeMapper.selectSharedTypes()).thenReturn(
                List.of(sharedType("common_status")));
        SysDictTypeDO tenantType = new SysDictTypeDO();
        tenantType.setId(5L);
        tenantType.setDictName("私有类型");
        tenantType.setDictType("private_status");
        tenantType.setStatus(1);
        tenantType.setIsShared(0);
        @SuppressWarnings("unchecked")
        IPage<SysDictTypeDO> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(tenantType));
        when(page.getTotal()).thenReturn(1L);
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(10L);
        when(typeMapper.selectPage(any(), any())).thenReturn(page);

        DictTypeQuery query = new DictTypeQuery();
        var result = service.typePage(query);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getIsShared()).isEqualTo(1);
        assertThat(result.getRecords().get(1).getDictType()).isEqualTo("private_status");
    }

    @Test
    void typePageSharedTypesAreFilteredByQuery() {
        when(typeMapper.selectSharedTypes()).thenReturn(
                List.of(sharedType("common_status")));
        SysDictTypeDO tenantType = new SysDictTypeDO();
        tenantType.setId(6L);
        tenantType.setDictName("订单类型");
        tenantType.setDictType("order_type");
        tenantType.setStatus(1);
        @SuppressWarnings("unchecked")
        IPage<SysDictTypeDO> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(tenantType));
        when(page.getTotal()).thenReturn(1L);
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(10L);
        when(typeMapper.selectPage(any(), any())).thenReturn(page);

        DictTypeQuery query = new DictTypeQuery();
        query.setDictType("order_type");
        var result = service.typePage(query);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getDictType()).isEqualTo("order_type");
        assertThat(result.getTotal()).isEqualTo(1);
    }

    private DictDataSaveRequest dataRequest(String dictType, String label, String value) {
        DictDataSaveRequest request = new DictDataSaveRequest();
        request.setDictType(dictType);
        request.setDictLabel(label);
        request.setDictValue(value);
        return request;
    }
}
