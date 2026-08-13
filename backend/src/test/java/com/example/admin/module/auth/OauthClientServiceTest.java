package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.TenantContext;
import com.example.admin.module.auth.dto.OauthClientQuery;
import com.example.admin.module.auth.dto.OauthClientSaveRequest;
import com.example.admin.module.auth.entity.SysOauthClientDO;
import com.example.admin.module.auth.mapper.SysOauthClientMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4-1.22：sys_oauth_client 按租户隔离。SSO 应用为租户私有数据：page 过滤当前租户、
 * create 落当前租户、update/status/delete 校验归属；client_id 跨租户全局唯一（SSO 匿名
 * 链路按 client_id selectOne，重名会 TooManyResultsException）。
 */
@ExtendWith(MockitoExtension.class)
class OauthClientServiceTest {

    @Mock
    private SysOauthClientMapper oauthClientMapper;

    @InjectMocks
    private OauthClientService oauthClientService;

    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysOauthClientDO.class);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private OauthClientSaveRequest request() {
        OauthClientSaveRequest request = new OauthClientSaveRequest();
        request.setClientName("应用A");
        request.setClientId("app-a");
        request.setClientSecret("secret");
        return request;
    }

    @Test
    void pageFiltersByCurrentTenant() {
        // 修复前 page 无租户条件，租户 2 管理员能看到全租户应用（含 client_secret）。
        TenantContext.setTenantId(2L);
        when(oauthClientMapper.selectPage(any(), any())).thenReturn(new Page<SysOauthClientDO>(1, 10));

        oauthClientService.page(new OauthClientQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysOauthClientDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(oauthClientMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper<SysOauthClientDO> wrapper = (LambdaQueryWrapper<SysOauthClientDO>) captor.getValue();
        // getSqlSegment() 触发 SQL 段构建后参数表才物化；断言 tenant_id 条件与绑定值。
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(2L);
    }

    @Test
    void createAssignsCurrentTenant() {
        TenantContext.setTenantId(2L);
        when(oauthClientMapper.selectCount(any())).thenReturn(0L);

        oauthClientService.create(request());

        ArgumentCaptor<SysOauthClientDO> captor = ArgumentCaptor.forClass(SysOauthClientDO.class);
        verify(oauthClientMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(2L);
    }

    @Test
    void createRejectsClientIdUsedByOtherTenant() {
        // client_id 须跨租户全局唯一，否则 SSO 匿名解析 selectOne 抛 TooManyResultsException。
        TenantContext.setTenantId(2L);
        when(oauthClientMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> oauthClientService.create(request()));
        verify(oauthClientMapper, never()).insert(any(SysOauthClientDO.class));
    }

    @Test
    void updateRejectsRowOwnedByOtherTenant() {
        TenantContext.setTenantId(2L);
        when(oauthClientMapper.selectOne(any())).thenReturn(null);

        OauthClientSaveRequest request = request();
        request.setId(99L);
        assertThrows(BusinessException.class, () -> oauthClientService.update(request));
        verify(oauthClientMapper, never()).updateById(any(SysOauthClientDO.class));
    }

    @Test
    void updateOwnedRowRejectsDuplicateClientId() {
        TenantContext.setTenantId(2L);
        SysOauthClientDO owned = new SysOauthClientDO();
        owned.setId(99L);
        owned.setTenantId(2L);
        when(oauthClientMapper.selectOne(any())).thenReturn(owned);
        when(oauthClientMapper.selectCount(any())).thenReturn(1L);

        OauthClientSaveRequest request = request();
        request.setId(99L);
        assertThrows(BusinessException.class, () -> oauthClientService.update(request));
        verify(oauthClientMapper, never()).updateById(any(SysOauthClientDO.class));
    }

    @Test
    void updateStatusRejectsRowOwnedByOtherTenant() {
        TenantContext.setTenantId(2L);
        when(oauthClientMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> oauthClientService.updateStatus(99L, 0));
        verify(oauthClientMapper, never()).updateById(any(SysOauthClientDO.class));
    }

    @Test
    void deleteRejectsRowOwnedByOtherTenant() {
        TenantContext.setTenantId(2L);
        when(oauthClientMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> oauthClientService.delete(99L));
        verify(oauthClientMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteAllowsOwnedRow() {
        TenantContext.setTenantId(2L);
        SysOauthClientDO owned = new SysOauthClientDO();
        owned.setId(99L);
        owned.setTenantId(2L);
        when(oauthClientMapper.selectOne(any())).thenReturn(owned);

        oauthClientService.delete(99L);

        verify(oauthClientMapper).deleteById(99L);
    }
}
