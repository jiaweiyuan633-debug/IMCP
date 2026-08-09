package com.example.admin.module.system.warmflow;

import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WarmFlowPermissionHandler implements PermissionHandler {

    private static final String USER_PREFIX = "user:";
    private static final String ROLE_PREFIX = "role:";
    private static final String ALL_PERMISSION = "all";
    private static final String ANONYMOUS_PERMISSION = "anonymous";
    private static final String ADMIN_PERMISSION = "admin";

    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<String> permissions() {
        LoginUser user = currentUser();
        if (user == null) {
            return List.of(ANONYMOUS_PERMISSION);
        }
        List<String> permissions = new ArrayList<>();
        permissions.add(USER_PREFIX + user.getUserId());
        permissions.add(ALL_PERMISSION);
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            permissions.add(ADMIN_PERMISSION);
        }
        userRoleMapper.selectRoleIdsByUserId(user.getUserId())
                .forEach(roleId -> permissions.add(ROLE_PREFIX + roleId));
        return permissions;
    }

    @Override
    public String getHandler() {
        LoginUser user = currentUser();
        return user == null ? ANONYMOUS_PERMISSION : String.valueOf(user.getUserId());
    }

    @Override
    public List<String> convertPermissions(List<String> permissions) {
        List<String> converted = new ArrayList<>();
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                continue;
            }
            if (permission.startsWith(ROLE_PREFIX)) {
                try {
                    Long roleId = Long.valueOf(permission.substring(ROLE_PREFIX.length()));
                    userRoleMapper.selectUserIdsByRoleIds(List.of(roleId))
                            .forEach(userId -> converted.add(USER_PREFIX + userId));
                } catch (NumberFormatException ignored) {
                    converted.add(permission);
                }
            } else {
                converted.add(permission);
            }
        }
        return converted.stream().distinct().toList();
    }

    private LoginUser currentUser() {
        try {
            return SecurityUtils.getLoginUser();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
