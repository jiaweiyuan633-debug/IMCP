package cn.admin.scaffold.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiPermVo {

    private Long id;
    private String method;
    private String pathPattern;
    private String permCode;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
}
