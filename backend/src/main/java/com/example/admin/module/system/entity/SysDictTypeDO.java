package com.example.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_dict_type")
public class SysDictTypeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String dictName;
    private String dictType;
    private Integer status;
    /** 是否共享字典：1=tenant_id=0 全局一份，所有租户可读且可覆盖；0=租户私有。 */
    private Integer isShared;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
}

