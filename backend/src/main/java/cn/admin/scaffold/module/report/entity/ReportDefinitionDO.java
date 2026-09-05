package cn.admin.scaffold.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表定义实体。租户隔离（tenant_id 由拦截器自动注入），逻辑删除 + 乐观锁。
 * dataSource 为只读查询 SQL（支持 :param 命名占位），paramsJson 为参数定义 JSON。
 */
@Data
@TableName("report_definition")
public class ReportDefinitionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String category;
    private String dataSource;
    private String chartType;
    private String paramsJson;
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
