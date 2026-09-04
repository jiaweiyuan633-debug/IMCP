package cn.admin.scaffold.module.notice.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelConfigVo {

    private Long id;
    private String channelType;
    private String channelName;
    private String configJson;
    private Integer status;
    private Integer sort;
    private String description;
    private LocalDateTime createdAt;
}
