package cn.admin.scaffold.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据权限表-列映射配置（批次2b）。
 * 将 DataScopeInnerInterceptor 中「受控表 -> 用户关联列」的硬编码映射下沉为运行时配置。
 */
@Data
@TableName("sys_data_permission")
public class SysDataPermissionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 受控表名（全小写），如 sys_user / ai_task。 */
    private String tableName;
    /** 用户ID关联列名：按当前登录用户可见的用户ID集合过滤（列值为用户ID）。 */
    private String userColumn;
    /** 用户名关联列名：按用户名集合过滤；设置时优先于 userColumn。 */
    private String usernameColumn;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
