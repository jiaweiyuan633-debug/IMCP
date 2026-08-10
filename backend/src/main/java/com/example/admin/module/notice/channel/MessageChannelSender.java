package com.example.admin.module.notice.channel;

import com.example.admin.module.notice.ChannelType;
import com.example.admin.module.notice.entity.SysChannelConfigDO;

/**
 * 消息渠道发送器抽象。实现类按渠道类型注册，由 {@link ChannelFactory} 按 type 分派。
 */
public interface MessageChannelSender {

    ChannelType supports();

    /**
     * 发送一条消息。
     *
     * @return null 表示发送成功，否则返回可读的错误信息
     */
    String send(SysChannelConfigDO config, String target, String title, String content);
}
