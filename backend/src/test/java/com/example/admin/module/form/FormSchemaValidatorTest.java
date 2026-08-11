package com.example.admin.module.form;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.form.vo.FormField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 表单 Schema 校验器单测：合法/非法 schema、必填/类型/长度校验、未知 key 忽略。
 */
class FormSchemaValidatorTest {

    private final FormSchemaValidator validator = new FormSchemaValidator(new ObjectMapper());

    @Test
    void validSchemaParsesFields() {
        String schema = "[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"input\",\"required\":true,\"maxLength\":20},"
                + "{\"key\":\"sex\",\"label\":\"性别\",\"type\":\"select\",\"required\":false,\"options\":[\"男\",\"女\"]}]";

        List<FormField> fields = validator.validateSchema(schema);

        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).getKey()).isEqualTo("name");
        assertThat(fields.get(0).getRequired()).isTrue();
        assertThat(fields.get(1).getType()).isEqualTo("select");
        assertThat(fields.get(1).getOptions()).containsExactly("男", "女");
    }

    @Test
    void invalidJsonRejected() {
        assertSchemaInvalid("not-json");
    }

    @Test
    void nonArrayRejected() {
        assertSchemaInvalid("{\"key\":\"name\",\"type\":\"input\"}");
    }

    @Test
    void duplicateKeyRejected() {
        assertSchemaInvalid("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"input\"},"
                + "{\"key\":\"name\",\"label\":\"姓名2\",\"type\":\"input\"}]");
    }

    @Test
    void missingKeyRejected() {
        assertSchemaInvalid("[{\"label\":\"姓名\",\"type\":\"input\"}]");
    }

    @Test
    void unknownTypeRejected() {
        assertSchemaInvalid("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"upload\"}]");
    }

    @Test
    void requiredMustBeBoolean() {
        assertSchemaInvalid("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"input\",\"required\":\"true\"}]");
    }

    @Test
    void selectWithoutOptionsRejected() {
        assertSchemaInvalid("[{\"key\":\"sex\",\"label\":\"性别\",\"type\":\"select\"}]");
    }

    @Test
    void multiSelectEmptyOptionsRejected() {
        assertSchemaInvalid("[{\"key\":\"tags\",\"label\":\"标签\",\"type\":\"multi-select\",\"options\":[]}]");
    }

    @Test
    void missingRequiredRejectedWithLabel() {
        FormField field = field("name", "姓名", "input", true, null);

        assertThatThrownBy(() -> validator.validateData(List.of(field), Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("姓名")
                .hasMessageContaining("必填");
    }

    @Test
    void numberFieldRequiresNumericValue() {
        FormField field = field("age", "年龄", "number", false, null);

        assertDataInvalid(field, Map.of("age", "abc"));
        // 数字字符串与数值均可通过
        validator.validateData(List.of(field), Map.of("age", "18"));
        validator.validateData(List.of(field), Map.of("age", 18));
    }

    @Test
    void dateFieldRequiresYyyyMmDd() {
        FormField field = field("birth", "生日", "date", false, null);

        assertDataInvalid(field, Map.of("birth", "2024-13-01"));
        assertDataInvalid(field, Map.of("birth", "2024/01/01"));
        validator.validateData(List.of(field), Map.of("birth", "2024-01-01"));
    }

    @Test
    void textOverMaxLengthRejected() {
        FormField field = new FormField();
        field.setKey("name");
        field.setLabel("姓名");
        field.setType("input");
        field.setRequired(false);
        field.setMaxLength(5);

        assertDataInvalid(field, Map.of("name", "abcdef"));
        validator.validateData(List.of(field), Map.of("name", "abcde"));
    }

    @Test
    void unknownKeyIgnored() {
        FormField field = field("name", "姓名", "input", false, null);

        validator.validateData(List.of(field), Map.of("unknown", "value"));
    }

    @Test
    void optionalFieldMissingAccepted() {
        FormField field = field("name", "姓名", "input", false, null);

        validator.validateData(List.of(field), Map.of());
    }

    @Test
    void selectValueMustBeInOptions() {
        FormField field = new FormField();
        field.setKey("sex");
        field.setLabel("性别");
        field.setType("select");
        field.setRequired(false);
        field.setOptions(List.of("男", "女"));

        assertDataInvalid(field, Map.of("sex", "其他"));
        validator.validateData(List.of(field), Map.of("sex", "男"));
    }

    @Test
    void multiSelectEachValueMustBeInOptions() {
        FormField field = new FormField();
        field.setKey("tags");
        field.setLabel("标签");
        field.setType("multi-select");
        field.setRequired(false);
        field.setOptions(List.of("a", "b"));

        assertDataInvalid(field, Map.of("tags", List.of("a", "c")));
        validator.validateData(List.of(field), Map.of("tags", List.of("a", "b")));
    }

    @Test
    void multiSelectEmptyListAcceptedAsUnselected() {
        FormField field = new FormField();
        field.setKey("tags");
        field.setLabel("标签");
        field.setType("multi-select");
        field.setRequired(false);
        field.setOptions(List.of("a", "b"));

        validator.validateData(List.of(field), Map.of("tags", List.of()));
    }

    private FormField field(String key, String label, String type, boolean required, Integer maxLength) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(type);
        field.setRequired(required);
        field.setMaxLength(maxLength);
        return field;
    }

    private void assertSchemaInvalid(String schema) {
        assertThatThrownBy(() -> validator.validateSchema(schema))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORM_SCHEMA_INVALID.getMessage());
    }

    private void assertDataInvalid(FormField field, Map<String, Object> data) {
        assertThatThrownBy(() -> validator.validateData(List.of(field), data))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字段【")
                .hasMessageContaining(field.getLabel());
    }
}
