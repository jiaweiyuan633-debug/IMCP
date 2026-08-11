package com.example.admin.module.system;

import com.example.admin.common.BusinessException;
import com.example.admin.module.system.dto.DataPermissionSaveRequest;
import com.example.admin.module.system.entity.SysDataPermissionDO;
import com.example.admin.module.system.mapper.SysDataPermissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemDataPermissionServiceTest {

    @Mock
    private SysDataPermissionMapper mapper;

    @Mock
    private DataPermissionRuleResolver ruleResolver;

    @InjectMocks
    private SystemDataPermissionService service;

    private DataPermissionSaveRequest request() {
        DataPermissionSaveRequest request = new DataPermissionSaveRequest();
        request.setTableName("SYS_Order");
        request.setUserColumn("owner_id");
        request.setEnabled(1);
        return request;
    }

    @Test
    void createRejectsWhenNoColumnConfigured() {
        DataPermissionSaveRequest request = request();
        request.setUserColumn(null);
        request.setUsernameColumn(" ");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少配置一个");
        verify(mapper, never()).insert(any(SysDataPermissionDO.class));
        verify(ruleResolver, never()).reload();
    }

    @Test
    void createLowercasesTableAndReloadsCache() {
        when(mapper.insert(any(SysDataPermissionDO.class))).thenReturn(1);

        service.create(request());

        ArgumentCaptor<SysDataPermissionDO> captor = ArgumentCaptor.forClass(SysDataPermissionDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTableName()).isEqualTo("sys_order");
        assertThat(captor.getValue().getUserColumn()).isEqualTo("owner_id");
        verify(ruleResolver).reload();
    }

    @Test
    void updateThrowsWhenConfigNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        DataPermissionSaveRequest request = request();
        request.setId(99L);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        verify(ruleResolver, never()).reload();
    }

    @Test
    void deleteReloadsCache() {
        service.delete(1L);
        verify(mapper).deleteById(1L);
        verify(ruleResolver).reload();
    }
}
