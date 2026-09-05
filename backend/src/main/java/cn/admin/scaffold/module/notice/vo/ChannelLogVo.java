package cn.admin.scaffold.module.notice.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelLogVo {

    private Long id;
    private String channelType;
    private Long channelId;
    private String target;
    private String title;
    private String content;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createdAt;
}
