package cn.admin.scaffold.module.importexport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入导出模板实体。租户隔离（tenant_id 由拦截器注入，集成阶段登记进 TENANT_TABLES），逻辑删除 + 乐观锁。
 */
@Data
@TableName("import_export_template")
public class ImportExportTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String type;
    private String entityKey;
    private String configJson;
    private String remark;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
