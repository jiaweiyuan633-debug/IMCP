package com.example.admin.module.importexport.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入导出任务视图对象。
 */
@Data
@Builder
public class JobVo {

    private Long id;
    private Long templateId;
    private String templateCode;
    private String type;
    private String status;
    private Long fileId;
    private String fileName;
    private Long resultFileId;
    private Integer total;
    private Integer success;
    private Integer failed;
    private String errorMessage;
    private LocalDateTime createdAt;
}
