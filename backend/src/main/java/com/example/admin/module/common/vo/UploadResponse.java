package com.example.admin.module.common.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResponse {

    private Long id;
    private String url;
    private String name;
    private long size;
    private String accessToken;
    private String contentType;
    private String category;
    private String sha256;
    private String scanStatus;
    private String contentUrl;
}

