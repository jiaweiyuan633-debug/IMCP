package cn.admin.scaffold.module.notice.channel;

import cn.admin.scaffold.common.LogMaskUtils;
import cn.admin.scaffold.module.notice.ChannelType;
import cn.admin.scaffold.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 邮件渠道：使用配置的 SMTP 服务器动态构建 JavaMailSender 发送。
 * config_json: {"host","port","username","password","from"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailChannelSender implements MessageChannelSender {

    private final ObjectMapper objectMapper;

    @Override
    public ChannelType supports() {
        return ChannelType.MAIL;
    }

    @Override
    public String send(SysChannelConfigDO config, String target, String title, String content) {
        try {
            JsonNode node = objectMapper.readTree(config.getConfigJson());
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(node.path("host").asText());
            sender.setPort(node.path("port").asInt(465));
            sender.setUsername(node.path("username").asText());
            sender.setPassword(node.path("password").asText());
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(node.path("from").asText());
            helper.setTo(target.split("[,;，；]"));
            helper.setSubject(title);
            helper.setText(content, true);
            sender.send(message);
            return null;
        } catch (Exception e) {
            // 批8d：异常消息可能内嵌 SMTP 配置或完整地址（含账号密码形态），落库/日志前统一打码
            String error = LogMaskUtils.sanitize(e.getMessage());
            log.warn("邮件发送失败: target={}, err={}", target, error);
            return error;
        }
    }
}
