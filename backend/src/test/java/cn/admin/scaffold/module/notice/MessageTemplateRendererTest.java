package cn.admin.scaffold.module.notice;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplateRendererTest {

    private final MessageTemplateRenderer renderer = new MessageTemplateRenderer();

    @Test
    void replacesProvidedPlaceholders() {
        String result = renderer.render("您好 ${name}，您的工单 ${ticketNo} 已创建",
                Map.of("name", "张三", "ticketNo", "T-1001"));
        assertThat(result).isEqualTo("您好 张三，您的工单 T-1001 已创建");
    }

    @Test
    void keepsUnknownPlaceholdersAsIs() {
        String result = renderer.render("您好 ${name}，请处理", Map.of("other", "x"));
        assertThat(result).contains("${name}");
    }

    @Test
    void nullValueRendersAsEmpty() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        String result = renderer.render("名称：${name}", params);
        assertThat(result).isEqualTo("名称：");
    }

    @Test
    void nullTemplateOrEmptyParamsReturnsAsIs() {
        assertThat(renderer.render(null, Map.of("a", "1"))).isNull();
        assertThat(renderer.render("${a}", null)).isEqualTo("${a}");
        assertThat(renderer.render("${a}", Map.of())).isEqualTo("${a}");
    }

    @Test
    void supportsHtmlContentWithPlaceholders() {
        String template = "<p>您的验证码为 <b>${code}</b></p>";
        String result = renderer.render(template, Map.of("code", "123456"));
        assertThat(result).isEqualTo("<p>您的验证码为 <b>123456</b></p>");
    }
}
