package com.example.admin.module.notice;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 消息模板渲染器：将 ${key} 占位符替换为参数值。
 *
 * <p>规则：只替换已提供的 key；未提供的占位符原样保留（调用方可事后发现缺参）。
 * 为避免误替换（如富文本模板内的 ${ 字面量），仅当参数中存在该 key 时才替换。
 */
@Component
public class MessageTemplateRenderer {

    public String render(String template, Map<String, Object> params) {
        if (template == null || params == null || params.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            Object value = entry.getValue();
            result = result.replace("${" + key + "}", value == null ? "" : String.valueOf(value));
        }
        return result;
    }

    /** 空值或空白模板判定，便于调用方快速兜底。 */
    public boolean isBlank(String template) {
        return !StringUtils.hasText(template);
    }
}
