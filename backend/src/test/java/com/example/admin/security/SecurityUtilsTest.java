package com.example.admin.security;

import com.example.admin.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLoginUserReturnsPrincipalAndUserId() {
        LoginUser loginUser = LoginUser.builder().userId(7L).username("admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
        assertSame(loginUser, SecurityUtils.getLoginUser());
        assertEquals(7L, SecurityUtils.getUserId());
    }

    @Test
    void getLoginUserThrowsWhenUnauthenticated() {
        assertThrows(BusinessException.class, SecurityUtils::getLoginUser);
    }

    @Test
    void getLoginUserThrowsWhenPrincipalIsNotLoginUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of()));
        assertThrows(BusinessException.class, SecurityUtils::getLoginUser);
    }

    @Test
    void tryGetUserIdReturnsNullWhenUnauthenticated() {
        assertNull(SecurityUtils.tryGetUserId());
    }
}
