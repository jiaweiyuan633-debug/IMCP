package cn.admin.scaffold.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * HTTP 语义标准化——标准异常映射精确状态码（413/400/401/403/404/405/415），
 * 业务异常保持 HTTP 200 + 业务码的既有契约。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionKeepsHttp200AndBusinessCode() {
        Result<Void> result = handler.handleBusinessException(new BusinessException(ResultCode.PARAM_ERROR));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), result.getCode());
        assertEquals(ResultCode.PARAM_ERROR.getMessage(), result.getMessage());
    }

    @Test
    void authenticationExceptionMapsTo401() {
        ResponseEntity<Result<Void>> response = handler.handleAuthenticationException(
                new AuthenticationException("bad") {
                });

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<Result<Void>> response = handler.handleAccessDeniedException(
                new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ResultCode.FORBIDDEN.getCode(), response.getBody().getCode());
    }

    @Test
    void uploadSizeExceededMapsTo413() {
        ResponseEntity<Result<Void>> response = handler.handlePayloadTooLarge(
                new MaxUploadSizeExceededException(20 * 1024 * 1024L));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals(ResultCode.PAYLOAD_TOO_LARGE.getCode(), response.getBody().getCode());
    }

    @Test
    void typeMismatchMapsTo400() {
        ResponseEntity<Result<Void>> response = handler.handleTypeMismatch(
                mock(MethodArgumentTypeMismatchException.class));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ResultCode.PARAM_ERROR.getCode(), response.getBody().getCode());
    }

    @Test
    void missingPathVariableMapsTo400() {
        ResponseEntity<Result<Void>> response = handler.handleMissingPathVariable(
                mock(MissingPathVariableException.class));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ResultCode.PARAM_ERROR.getCode(), response.getBody().getCode());
    }

    @Test
    void methodNotSupportedMapsTo405() {
        ResponseEntity<Result<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(ResultCode.METHOD_NOT_ALLOWED.getCode(), response.getBody().getCode());
    }

    @Test
    void mediaTypeNotSupportedMapsTo415() {
        ResponseEntity<Result<Void>> response = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("text/plain"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(ResultCode.MEDIA_TYPE_NOT_SUPPORTED.getCode(), response.getBody().getCode());
    }

    @Test
    void noResourceFoundMapsTo404() {
        ResponseEntity<Result<Void>> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/unknown"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ResultCode.NOT_FOUND.getCode(), response.getBody().getCode());
    }

    @Test
    void badCredentialsKeepsBusinessCode() {
        Result<Void> result = handler.handleBadCredentials(new BadCredentialsException("bad"));

        assertEquals(ResultCode.BAD_CREDENTIALS.getCode(), result.getCode());
    }

    @Test
    void jwtExceptionMapsTo401() {
        // refresh/logout 重放过期或伪造 JWT 此前落入兜底 500，语义应为 401
        @SuppressWarnings("unchecked")
        JwtException expired = new ExpiredJwtException(mock(JwsHeader.class), mock(Claims.class), "expired");

        ResponseEntity<Result<Void>> response = handler.handleJwtException(expired);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }
}
