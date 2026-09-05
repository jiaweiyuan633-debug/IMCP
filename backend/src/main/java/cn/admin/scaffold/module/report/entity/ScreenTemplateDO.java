package cn.admin.scaffold.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据大屏模板。tenant_id 为 NULL 表示内置全局模板（所有租户可见），
 * 非空表示用户自定义模板（租户隔离）。layout 为画布布局 JSON。
 * 表未纳入租户拦截白名单：内置模板需全租户可见，租户维度由服务层手动过滤。
 */
@Data
@TableName("screen_template")
public class ScreenTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String category;
    private String theme;
    private String layout;
    private String remark;
    private Integer builtin;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
