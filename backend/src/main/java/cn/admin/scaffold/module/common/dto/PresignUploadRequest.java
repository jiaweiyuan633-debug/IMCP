package cn.admin.scaffold.module.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 预签名直传签发请求。
 */
@Data
public class PresignUploadRequest {

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    private String contentType;
    private String category;

    @Min(value = 1, message = "文件大小必须大于 0")
    private long size;
}
