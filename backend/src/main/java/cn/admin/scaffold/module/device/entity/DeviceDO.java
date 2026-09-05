package cn.admin.scaffold.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备实体。租户隔离（tenant_id 由拦截器自动注入），逻辑删除 + 乐观锁。
 */
@Data
@TableName("sys_device")
public class DeviceDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String location;
    private Integer sort;
    private Integer status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
