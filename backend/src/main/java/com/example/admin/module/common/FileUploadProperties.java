package com.example.admin.module.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class FileUploadProperties {

    private long maxSizeMb = 20;
    private String allowedExtensions = "jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,txt,zip";
}
