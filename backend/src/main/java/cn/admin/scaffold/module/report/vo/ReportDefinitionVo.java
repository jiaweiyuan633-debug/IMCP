package cn.admin.scaffold.module.report.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表定义展示对象。
 */
@Data
@Builder
public class ReportDefinitionVo {

    private Long id;
    private String name;
    private String code;
    private String category;
    private String dataSource;
    private String chartType;
    private String paramsJson;
    private String remark;
    private Integer status;
    /** 乐观锁版本号：编辑时需原样回传，供服务端检测并发修改。 */
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
