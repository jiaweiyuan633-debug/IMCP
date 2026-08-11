package com.example.admin.module.notice;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 消息发送渠道类型。 */
@Getter
@RequiredArgsConstructor
public enum ChannelType {

    MAIL("邮件"),
    SMS("短信"),
    DINGTALK("钉钉"),
    WECOM("企业微信"),
    WEBHOOK("通用 Webhook");

    private final String displayName;
}
