package com.example.admin.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(1001, "参数错误"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    BAD_CREDENTIALS(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),
    PASSWORD_ERROR(1005, "原密码错误"),
    USERNAME_EXISTS(1006, "用户名已存在"),
    ROLE_CODE_EXISTS(1007, "角色编码已存在"),
    DEPT_HAS_CHILDREN(1008, "存在下级部门，不能删除"),
    POST_CODE_EXISTS(1009, "岗位编码已存在"),
    AI_SERVICE_UNAVAILABLE(1010, "AI 服务不可用"),
    AI_CONFIG_UNAVAILABLE(1011, "AI 服务未启用或不存在"),
    DICT_TYPE_EXISTS(1012, "字典类型已存在"),
    CONFIG_KEY_EXISTS(1013, "参数键名已存在"),
    LOGIN_TOO_MANY(1014, "登录过于频繁或账号已锁定"),
    TOTP_REQUIRED(1015, "需要两步验证"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    TENANT_LIMIT_EXCEEDED(1016, "租户用户数量已达上限"),
    AI_DAILY_LIMIT_EXCEEDED(1017, "AI 任务已达每日上限"),
    STORAGE_LIMIT_EXCEEDED(1018, "租户存储空间不足"),
    CAPTCHA_ERROR(1019, "验证码错误"),
    TOTP_CODE_ERROR(1020, "动态验证码错误"),
    AI_CALLBACK_INVALID(1021, "AI 回调签名无效"),
    AI_CALLBACK_STATUS_INVALID(1022, "非法回调状态"),
    WORKFLOW_FINISHED(1023, "当前流程已结束"),
    WORKFLOW_DEF_INVALID(1024, "流程定义不可用"),
    WORKFLOW_NO_START_NODE(1025, "流程定义没有可进入的起始节点"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;
}

