package com.example.admin.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.dromara.warm.flow.core.exception.FlowException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：返回 HTTP 200 + 业务码（该项目既有契约，前端 axios 拦截器按 Result.code 统一处理，
     * 业务码 10xx 不映射 HTTP 状态码以免破坏数十个接口的前端行为）。标准 HTTP 语义异常（4xx/5xx）见下方独立 handler。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.error(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数错误" : fieldError.getDefaultMessage();
        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /** 请求体 JSON 解析失败属客户端错误：HTTP 400（修复前 200 + 业务码，前端错误分支同样兼容展示 message）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return httpError(HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException exception) {
        return Result.error(ResultCode.PARAM_ERROR);
    }

    /** 路径变量缺失（URL 模板未匹配上）：HTTP 400。 */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<Result<Void>> handleMissingPathVariable(MissingPathVariableException exception) {
        return httpError(HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR);
    }

    /** 路径/查询参数类型不匹配（如传入非数字）：修复前落入兜底 500，语义应为 400。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return httpError(HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR);
    }

    /** 表单对象绑定失败（非 @RequestBody 的 @ModelAttribute）：HTTP 400。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数错误" : fieldError.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.error("Data integrity violation, requestId={}", RequestIdHolder.get(), exception);
        return Result.error(ResultCode.INTERNAL_ERROR);
    }

    /**
     * 唯一键冲突兜底：并发「先查唯一再插入」的 create 路径预检非原子，后插入者命中数据库唯一键时
     * 由 DataIntegrityViolationException 细化而来，返回业务码而非 500；各 Service 可捕获后转精确业务码。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException exception) {
        log.warn("Duplicate key constraint, requestId={}", RequestIdHolder.get(), exception);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "数据已存在，请检查唯一字段");
    }

    @ExceptionHandler(FlowException.class)
    public Result<Void> handleFlowException(FlowException exception) {
        return Result.error(ResultCode.PARAM_ERROR.getCode(), exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException exception) {
        return Result.error(ResultCode.BAD_CREDENTIALS);
    }

    /** 认证失败：HTTP 401（前端 axios 错误分支据此触发 token 刷新/重登）。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException exception) {
        return httpError(HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        return httpError(HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
    }

    /** 上传/请求体超过大小限制（multipart 20MB）：修复前落入兜底 500，语义应为 413。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handlePayloadTooLarge(MaxUploadSizeExceededException exception) {
        log.warn("Upload size exceeded, requestId={}", RequestIdHolder.get());
        return httpError(HttpStatus.PAYLOAD_TOO_LARGE, ResultCode.PAYLOAD_TOO_LARGE);
    }

    /** 请求方法不支持（POST 打了 GET 等）：HTTP 405。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return httpError(HttpStatus.METHOD_NOT_ALLOWED, ResultCode.METHOD_NOT_ALLOWED);
    }

    /** 请求 Content-Type 与接口不匹配：HTTP 415。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        return httpError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ResultCode.MEDIA_TYPE_NOT_SUPPORTED);
    }

    /** 未映射到任何静态资源/接口的路径：HTTP 404（修复前落入兜底 500）。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return httpError(HttpStatus.NOT_FOUND, ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        log.error("Unhandled exception, requestId={}", RequestIdHolder.get(), exception);
        return Result.error(ResultCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> httpError(HttpStatus status, ResultCode code) {
        return ResponseEntity.status(status).body(Result.error(code));
    }
}
