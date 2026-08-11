package com.example.admin.module.importexport.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入导出模板视图对象。
 */
@Data
@Builder
public class TemplateVo {

    private Long id;
    private String name;
    private String code;
    private String type;
    private String entityKey;
    private String configJson;
    private String remark;
    private Integer status;
    /** 乐观锁版本号：编辑时需原样回传，供服务端检测并发修改。 */
    private Integer version;
    private LocalDateTime createdAt;
}
