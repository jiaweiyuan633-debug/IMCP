package com.example.admin.module.common.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResponse {

    private String url;
    private String name;
    private long size;
}

