package com.example.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictTypeSaveRequest {

    private Long id;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称长度不能超过 100")
    private String dictName;

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型长度不能超过 100")
    private String dictType;

    private Integer status;
    /** 是否共享字典：1=tenant_id=0 全局一份（需 system:dict:shared:* 权限），0=租户私有。 */
    private Integer isShared;
    private String remark;
}

