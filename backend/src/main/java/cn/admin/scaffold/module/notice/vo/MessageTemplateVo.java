package cn.admin.scaffold.module.notice.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageTemplateVo {

    private Long id;
    private String templateCode;
    private String templateName;
    private String messageType;
    private String titleTemplate;
    private String contentTemplate;
    private String contentType;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
}
