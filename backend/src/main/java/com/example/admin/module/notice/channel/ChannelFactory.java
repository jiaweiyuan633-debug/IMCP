package com.example.admin.module.notice.channel;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.notice.ChannelType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 按渠道类型分派发送器。 */
@Component
public class ChannelFactory {

    private final Map<ChannelType, MessageChannelSender> senders;

    public ChannelFactory(List<MessageChannelSender> senderList) {
        this.senders = senderList.stream()
                .collect(Collectors.toMap(sender -> sender.supports(), sender -> sender));
    }

    public MessageChannelSender get(ChannelType type) {
        MessageChannelSender sender = senders.get(type);
        if (sender == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的渠道类型: " + type);
        }
        return sender;
    }
}
