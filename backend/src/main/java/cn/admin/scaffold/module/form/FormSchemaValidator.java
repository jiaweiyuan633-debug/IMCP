package cn.admin.scaffold.module.form;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.form.vo.FormField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 低代码表单引擎自研 Schema 校验器（零第三方 JSON-Schema 依赖，字段数组式）。
 *
 * <p>{@link #validateSchema} 校验 schemaJson 为合法 JSON、字段 key 非空且唯一、type 在枚举内、
 * required 为布尔、下拉/多选必须带 options（≥1），不合法抛 {@code FORM_SCHEMA_INVALID}，返回解析后的字段列表。
 *
 * <p>{@link #validateData} 校验提交数据：缺失 required 字段、数字/日期类型强转、文本超 maxLength 均
 * 抛 {@code FORM_DATA_INVALID}（消息含字段 label）；未知 key 忽略。
 */
@Component
@RequiredArgsConstructor
public class FormSchemaValidator {

    private static final Set<String> FIELD_TYPES = Set.of(
            "input", "textarea", "number", "date", "select", "multi-select", "switch");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    /**
     * 校验并解析表单定义 schema。字段数组形式：
     * <pre>{@code [{key,label,type,required,options,placeholder,maxLength}]}</pre>
     */
    public List<FormField> validateSchema(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(schemaJson);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
        }
        if (root == null || !root.isArray()) {
            throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
        }
        Set<String> keys = new HashSet<>();
        for (JsonNode node : root) {
            JsonNode keyNode = node.get("key");
            if (keyNode == null || !keyNode.isTextual() || !StringUtils.hasText(keyNode.asText())) {
                throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
            }
            if (!keys.add(keyNode.asText())) {
                throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
            }
            JsonNode typeNode = node.get("type");
            if (typeNode == null || !typeNode.isTextual() || !FIELD_TYPES.contains(typeNode.asText())) {
                throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
            }
            String type = typeNode.asText();
            if (node.has("required") && !node.get("required").isBoolean()) {
                throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
            }
            if ("select".equals(type) || "multi-select".equals(type)) {
                JsonNode optionsNode = node.get("options");
                if (optionsNode == null || !optionsNode.isArray() || optionsNode.isEmpty()) {
                    throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
                }
            }
        }
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<List<FormField>>() {
            });
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.FORM_SCHEMA_INVALID);
        }
    }

    /**
     * 校验提交数据：缺失 required → 拒绝（消息含字段 label）；数字/日期强转校验；文本超 maxLength 拒绝；未知 key 忽略。
     */
    public void validateData(List<FormField> fields, Map<String, Object> data) {
        if (fields == null) {
            return;
        }
        Map<String, Object> values = data == null ? Map.of() : data;
        for (FormField field : fields) {
            Object value = values.get(field.getKey());
            boolean blank = value == null || (value instanceof String text && !StringUtils.hasText(text));
            String label = labelOf(field);
            if (blank) {
                if (Boolean.TRUE.equals(field.getRequired())) {
                    throw new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(), "字段【" + label + "】为必填项");
                }
                continue;
            }
            String type = field.getType();
            if ("number".equals(type) && !isNumber(value)) {
                throw new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(), "字段【" + label + "】必须为数字");
            }
            if ("date".equals(type) && !isDate(value)) {
                throw new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(), "字段【" + label + "】必须为 yyyy-MM-dd 格式日期");
            }
            if ("select".equals(type) && !inOptions(value, field)) {
                throw new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(),
                        "字段【" + label + "】的值不在可选范围内");
            }
            if ("multi-select".equals(type) && !inMultiOptions(value, field)) {
                throw new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(),
                        "字段【" + label + "】包含不在可选范围内的值");
            }
            if (("input".equals(type) || "textarea".equals(type))
                    && field.getMaxLength() != null
                    && value.toString().length() > field.getMaxLength()) {
                throw new BusinessException(ResultCode.FORM_DATA_INVALID.getCode(),
                        "字段【" + label + "】长度不能超过 " + field.getMaxLength());
            }
        }
    }

    private boolean isNumber(Object value) {
        if (value instanceof Number) {
            return true;
        }
        try {
            new BigDecimal(value.toString());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isDate(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            LocalDate.parse(text, DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /** 单选值必须在 schema 定义的 options 枚举内，超出取值域拒绝。调用方已排除 blank（null）。 */
    private boolean inOptions(Object value, FormField field) {
        List<String> options = field.getOptions();
        return value != null && options != null && options.contains(value.toString());
    }

    /** 多选值（列表或标量）的每个元素必须在 options 枚举内；空列表视为未选择，放行。 */
    private boolean inMultiOptions(Object value, FormField field) {
        List<String> options = field.getOptions();
        if (options == null || options.isEmpty()) {
            return false;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return true;
            }
            for (Object item : list) {
                if (item == null || !options.contains(item.toString())) {
                    return false;
                }
            }
            return true;
        }
        return value != null && options.contains(value.toString());
    }

    private String labelOf(FormField field) {
        return StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getKey();
    }
}
