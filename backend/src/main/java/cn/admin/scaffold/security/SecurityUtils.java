package cn.admin.scaffold.security;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    public static final String BEARER_PREFIX = "Bearer ";

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static Long tryGetUserId() {
        try {
            return getUserId();
        } catch (BusinessException exception) {
            return null;
        }
    }
}

