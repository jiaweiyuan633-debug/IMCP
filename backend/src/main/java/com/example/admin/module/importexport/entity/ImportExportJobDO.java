package com.example.admin.module.importexport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入导出任务实体。任务由 ImportExportJobProcessor 轮询处理，状态机 PENDING→PROCESSING→SUCCEEDED/FAILED。
 * 注意：import_export_job 表无 updated_by 列，实体不声明该字段。
 */
@Data
@TableName("import_export_job")
public class ImportExportJobDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
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
    private String queryJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
