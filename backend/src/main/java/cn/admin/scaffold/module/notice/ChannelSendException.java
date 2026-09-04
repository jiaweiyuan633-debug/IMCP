package cn.admin.scaffold.module.notice;

/**
 * 渠道发送失败异常：{@code MessageChannelSender.send} 以返回非 null 字符串表示失败（不抛异常），
 * {@link ChannelConfigService#sendWithRetry} 将失败包装为该异常以触发 spring-retry 重试。
 */
public class ChannelSendException extends RuntimeException {

    public ChannelSendException(String message) {
        super(message);
    }
}
