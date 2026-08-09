package com.example.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String fileName;
    private String originalName;
    private String url;
    private String objectKey;
    private Long size;
    private String storageType;
    private Long createdBy;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String accessToken;
}

