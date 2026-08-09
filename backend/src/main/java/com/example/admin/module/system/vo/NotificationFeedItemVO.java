package com.example.admin.module.system.vo;

import lombok.Data;

@Data
public class NotificationFeedItemVO {

    private String kind;
    private Long id;
    private String title;
    private String content;
    private String createdAt;
    private String tag;
}
