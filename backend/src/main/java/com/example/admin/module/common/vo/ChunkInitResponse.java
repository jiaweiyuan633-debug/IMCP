package com.example.admin.module.common.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 分片上传初始化响应（批次2c）。
 * exists=true 表示 sha256 命中已存文件（秒传），前端跳过整场上传直接使用 fileId/url。
 */
@Data
@Builder
public class ChunkInitResponse {

    /** 上传任务号（exists=false 时由前端逐片携带）。 */
    private String uploadId;
    /** 是否秒传命中。 */
    private boolean exists;
    /** 秒传命中的文件 ID。 */
    private Long fileId;
    /** 秒传命中的文件访问地址。 */
    private String url;
    /** 服务端确认的分片大小（供前端切分对齐）。 */
    private Integer chunkSize;
}
