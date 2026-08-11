package com.example.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据权限表-列映射新增/编辑请求（批次2b）。
 */
@Data
public class DataPermissionSaveRequest {

    private Long id;

    @NotBlank(message = "受控表名不能为空")
    @Size(max = 128, message = "受控表名长度不能超过 128")
    private String tableName;

    @Size(max = 128, message = "用户ID列名长度不能超过 128")
    private String userColumn;

    @Size(max = 128, message = "用户名列名长度不能超过 128")
    private String usernameColumn;

    private Integer enabled = 1;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
