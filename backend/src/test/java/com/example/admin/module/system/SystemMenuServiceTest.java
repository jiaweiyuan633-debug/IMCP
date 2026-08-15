package com.example.admin.module.system;

import com.example.admin.common.BusinessException;
import com.example.admin.module.system.dto.MenuSaveRequest;
import com.example.admin.module.system.entity.SysMenuDO;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.security.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R4-1.31：菜单变更权限缓存失效覆盖——权限编码（perm）变更或菜单删除须清空全部权限缓存
 * （影响面无法精确反查）；仅改名称/图标等不触发，避免无谓的全局缓存清空。
 */
@ExtendWith(MockitoExtension.class)
class SystemMenuServiceTest {

    @Mock
    private SysMenuMapper menuMapper;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private SystemMenuService menuService;

    @Test
    void updateWithoutPermChangeSkipsEviction() {
        when(menuMapper.selectById(1L)).thenReturn(menu("system:user:list"));

        menuService.update(request(1L, "system:user:list"));

        verify(menuMapper).updateById(any(SysMenuDO.class));
        verifyNoInteractions(tokenService);
    }

    @Test
    void updateWithPermChangeEvictsAllPermissions() {
        when(menuMapper.selectById(1L)).thenReturn(menu("system:user:list"));

        menuService.update(request(1L, "system:user:manage"));

        verify(tokenService).evictAllPermissionsAfterCommit();
    }

    @Test
    void updateNotFoundThrowsWithoutEviction() {
        when(menuMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> menuService.update(request(1L, "system:x")));
        verifyNoInteractions(tokenService);
    }

    @Test
    void deleteEvictsAllPermissions() {
        menuService.delete(2L);

        verify(menuMapper).deleteById(2L);
        verify(tokenService).evictAllPermissionsAfterCommit();
    }

    private static SysMenuDO menu(String perm) {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(1L);
        menu.setPerm(perm);
        return menu;
    }

    private static MenuSaveRequest request(Long id, String perm) {
        MenuSaveRequest request = new MenuSaveRequest();
        request.setId(id);
        request.setPerm(perm);
        return request;
    }
}
