package cn.admin.scaffold.module.system.vo;

import lombok.Data;

@Data
public class NotificationFeedItemVO {

    private String kind;
    private Long id;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private String createdAt;
    private String tag;
}
