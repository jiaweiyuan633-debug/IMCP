package cn.admin.scaffold.module.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiChatVo {

    private String content;
    private String model;
    private String provider;
    private long durationMs;
    private int status;
}
