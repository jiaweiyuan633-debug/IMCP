package cn.admin.scaffold.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String configName;
    private String configKey;
    private String configValue;
    private Integer configType;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
}

