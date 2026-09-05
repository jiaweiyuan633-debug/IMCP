package cn.admin.scaffold.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 资源权限映射：URL（method+path 模式）→ 所需权限编码。
 * URL 层在"已认证"基础上叠加资源级权限校验（见 ApiPermAuthorizationFilter）。
 */
@Data
@TableName("sys_api_perm")
public class SysApiPermDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String method;
    private String pathPattern;
    private String permCode;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
