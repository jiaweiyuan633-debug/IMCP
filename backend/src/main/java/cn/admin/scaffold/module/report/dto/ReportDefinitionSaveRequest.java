package cn.admin.scaffold.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 报表定义新增/编辑入参。code 唯一（租户内），dataSource 为只读查询 SQL。
 */
@Data
public class ReportDefinitionSaveRequest {

    private Long id;

    @NotBlank(message = "报表名称不能为空")
    @Size(max = 100, message = "报表名称长度不能超过 100")
    private String name;

    @NotBlank(message = "报表编码不能为空")
    @Size(max = 64, message = "报表编码长度不能超过 64")
    private String code;

    @Size(max = 64, message = "报表分类长度不能超过 64")
    private String category;

    @NotBlank(message = "数据源 SQL 不能为空")
    private String dataSource;

    @Size(max = 32, message = "图表类型长度不能超过 32")
    private String chartType;

    private String paramsJson;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    private Integer status;

    /** 乐观锁版本号：编辑时由列表/详情回传，冲突时服务端拒绝覆盖；新增不传。 */
    private Integer version;
}
