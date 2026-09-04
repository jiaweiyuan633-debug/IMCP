package cn.admin.scaffold.module.common.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 预签名直传签发响应（批次2c）。
 * supported=false 表示存储后端不支持预签名，前端应回退普通/分片上传。
 */
@Data
@Builder
public class PresignUploadResponse {

    private String objectKey;
    private String uploadUrl;
    private String storageType;
    private boolean supported;
}
