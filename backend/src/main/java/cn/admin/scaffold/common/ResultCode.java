package cn.admin.scaffold.common;

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
    FILE_SCAN_BLOCKED(1026, "文件未通过安全检查"),
    FILE_SCAN_ERROR(1027, "病毒扫描服务不可用"),
    DEVICE_CODE_EXISTS(1028, "设备编码已存在"),
    PROMPT_CODE_EXISTS(1029, "Prompt 模板编码已存在"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    REPEAT_SUBMIT(1030, "请勿重复提交"),
    ACQUIRE_LOCK_TIMEOUT(1031, "系统繁忙，请稍后重试"),
    // 批次4：报表定义化
    REPORT_CODE_EXISTS(1032, "报表编码已存在"),
    REPORT_SQL_INVALID(1033, "报表数据源仅支持只读查询"),
    // 批次4：设备物模型/遥测
    THING_MODEL_TYPE_EXISTS(1034, "物模型类型已存在"),
    // 批次4：导入导出中心
    IMPORT_TEMPLATE_CODE_EXISTS(1035, "导入导出模板编码已存在"),
    // 批次4：低代码表单引擎
    FORM_CODE_EXISTS(1036, "表单编码已存在"),
    FORM_SCHEMA_INVALID(1037, "表单定义无效"),
    FORM_DATA_INVALID(1038, "表单数据校验不通过"),
    // 批次 C：数据大屏
    SCREEN_TEMPLATE_CODE_EXISTS(1039, "大屏模板编码已存在"),
    // R1-1.7：跨租户同名账号且未指定租户时无法唯一定位
    USERNAME_AMBIGUOUS(1040, "存在同名账号，请填写租户ID后重试"),
    // R4-1.31：HTTP 语义标准化——标准异常映射精确状态码（业务码仍携带于 Result.code，前端本地化不受影响）
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    PAYLOAD_TOO_LARGE(413, "上传内容超过大小限制"),
    MEDIA_TYPE_NOT_SUPPORTED(415, "不支持的媒体类型"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;
}

