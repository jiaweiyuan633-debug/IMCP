package com.example.admin.module.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分片上传初始化请求（批次2c）。
 */
@Data
public class ChunkInitRequest {

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    private String contentType;
    private String category;

    @Min(value = 1, message = "分片总数必须大于 0")
    private int totalChunks;

    @Min(value = 1, message = "分片大小必须大于 0")
    private int chunkSize;

    @Min(value = 1, message = "总大小必须大于 0")
    private long totalSize;

    /** 整文件 sha256（可选）：init 时命中已存文件实现秒传，complete 时校验合并结果。 */
    private String sha256;
}
