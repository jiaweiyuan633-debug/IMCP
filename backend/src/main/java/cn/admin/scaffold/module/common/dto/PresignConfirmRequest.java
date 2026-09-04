package cn.admin.scaffold.module.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 预签名直传确认请求（批次2c）：前端 PUT 直传完成后回传 objectKey 入库。
 */
@Data
public class PresignConfirmRequest {

    @NotBlank(message = "对象键不能为空")
    private String objectKey;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    private String contentType;
    private String category;

    @Min(value = 1, message = "文件大小必须大于 0")
    private long size;
}
