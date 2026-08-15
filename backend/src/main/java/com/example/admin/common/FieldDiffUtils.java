package com.example.admin.common;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.example.admin.common.annotation.AuditField;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 字段级 diff 计算工具。
 *
 * <p>基于两个实体快照反射比对：排除主键、逻辑删除、乐观锁、租户/审计时间等框架字段，
 * 支持 {@link AuditField} 自定义字段中文名与强制忽略，敏感字段值脱敏为 {@code ***}。
 */
public final class FieldDiffUtils {

    /** 快照值需脱敏的字段名（与 LogMaskUtils 对齐，批8c 同步扩充） */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "oldPassword", "newPassword",
            "totpCode", "totpSecret", "secret", "secretKey",
            "apiKey", "appSecret", "clientSecret",
            "accessKeyId", "accessKeySecret",
            "accessToken", "refreshToken", "authorization", "authCode",
            "sign", "signature", "token",
            "configValue", "configJson",
            "phone", "mobile", "email", "idCard", "idCardNo");

    /** 框架维护、不参与业务 diff 的字段 */
    private static final Set<String> IGNORED_FIELD_NAMES = Set.of(
            "tenantId", "createdAt", "updatedAt", "createdBy", "updatedBy", "deleted", "version");

    private FieldDiffUtils() {
    }

    /** 单字段变更项 */
    public record Change(String field, String label, String before, String after) {
    }

    /**
     * 计算 before/after 快照的字段级变更。
     * 任一侧为 null 视为"无值"，两侧均为 null 不产生变更。
     */
    public static List<Change> diff(Object before, Object after) {
        List<Change> changes = new ArrayList<>();
        Class<?> type = after != null ? after.getClass() : (before == null ? null : before.getClass());
        if (type == null) {
            return changes;
        }
        for (Field field : collectFields(type)) {
            if (!isAuditable(field)) {
                continue;
            }
            String b = before == null ? null : read(field, before);
            String a = after == null ? null : read(field, after);
            if (!Objects.equals(b, a)) {
                changes.add(new Change(field.getName(), label(field), b, a));
            }
        }
        return changes;
    }

    public static String toJson(List<Change> changes, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception exception) {
            return "[]";
        }
    }

    /** 完整快照 JSON（脱敏，失败回退 toString） */
    public static String toJson(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return LogMaskUtils.toMaskedJson(value, objectMapper);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private static boolean isAuditable(Field field) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
            return false;
        }
        if (field.isAnnotationPresent(TableId.class)
                || field.isAnnotationPresent(TableLogic.class)
                || field.isAnnotationPresent(Version.class)) {
            return false;
        }
        AuditField auditField = field.getAnnotation(AuditField.class);
        if (auditField != null && auditField.ignore()) {
            return false;
        }
        return !IGNORED_FIELD_NAMES.contains(field.getName());
    }

    private static String label(Field field) {
        AuditField auditField = field.getAnnotation(AuditField.class);
        if (auditField != null && !auditField.label().isBlank()) {
            return auditField.label();
        }
        return field.getName();
    }

    private static String read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return stringify(field, field.get(target));
        } catch (IllegalAccessException exception) {
            return null;
        }
    }

    private static String stringify(Field field, Object value) {
        if (value == null) {
            return null;
        }
        if (SENSITIVE_FIELDS.contains(field.getName())) {
            return "***";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum || value instanceof Temporal) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }
}
