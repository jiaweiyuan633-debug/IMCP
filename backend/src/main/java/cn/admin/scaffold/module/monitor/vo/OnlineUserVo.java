package cn.admin.scaffold.module.monitor.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OnlineUserVo {

    private String tokenId;
    private Long userId;
    private String username;
    private String ip;
    private String userAgent;
    private LocalDateTime loginTime;
}

