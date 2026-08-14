package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.SecretCipher;
import com.example.admin.common.TenantContext;
import com.example.admin.module.auth.dto.OauthConfigQuery;
import com.example.admin.module.auth.dto.OauthConfigSaveRequest;
import com.example.admin.module.auth.entity.SysOauthConfigDO;
import com.example.admin.module.auth.mapper.SysOauthConfigMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R4-1.22：sys_oauth_config 平台级租户守卫。配置为平台级设置，仅租户 1 管理员可管理；
 * 非平台租户调用任一 CRUD 必须抛 FORBIDDEN 且不触碰 mapper。
 */
@ExtendWith(MockitoExtension.class)
class OauthConfigServiceTest {

    @Mock
    private SysOauthConfigMapper oauthConfigMapper;
    @Mock
    private SecretCipher secretCipher;

    @InjectMocks
    private OauthConfigService oauthConfigService;

    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysOauthConfigDO.class);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private OauthConfigSaveRequest request() {
        OauthConfigSaveRequest request = new OauthConfigSaveRequest();
        request.setProvider("github");
        request.setAppId("app");
        request.setAppSecret("secret");
        return request;
    }

    @Test
    void nonPlatformTenantCannotManagePlatformConfig() {
        // 修复前无租户守卫：租户 2 管理员可跨租户读改 appSecret 并污染租户归属。
        // 守卫须在任何 mapper 交互之前触发（含 update 的 id 非空校验），故逐一断言 FORBIDDEN。
        TenantContext.setTenantId(2L);
        assertThrows(BusinessException.class, () -> oauthConfigService.page(new OauthConfigQuery()));
        assertThrows(BusinessException.class, () -> oauthConfigService.create(request()));
        assertThrows(BusinessException.class, () -> oauthConfigService.update(request()));
        assertThrows(BusinessException.class, () -> oauthConfigService.updateStatus(1L, 0));
        assertThrows(BusinessException.class, () -> oauthConfigService.delete(1L));
        verifyNoInteractions(oauthConfigMapper);
    }

    @Test
    void createForcesPlatformTenantOwnershipAndEncryptsSecret() {
        // create 未显式设置 tenant_id 时靠 DB 默认落 1；现显式固化，防止路由歧义。
        TenantContext.setTenantId(1L);
        // R4-1.28：appSecret 落库前必须加密（明文永不入库）
        when(secretCipher.encrypt("secret")).thenReturn("enc:cipher");
        oauthConfigService.create(request());
        ArgumentCaptor<SysOauthConfigDO> captor = ArgumentCaptor.forClass(SysOauthConfigDO.class);
        verify(oauthConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getAppSecret()).isEqualTo("enc:cipher");
    }

    @Test
    void platformTenantCanManageConfig() {
        TenantContext.setTenantId(1L);
        oauthConfigService.updateStatus(1L, 0);
        verify(oauthConfigMapper).updateById(any(SysOauthConfigDO.class));
    }
}
