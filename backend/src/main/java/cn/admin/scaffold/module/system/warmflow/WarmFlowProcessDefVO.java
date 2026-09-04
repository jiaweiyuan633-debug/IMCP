package cn.admin.scaffold.module.system.warmflow;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WarmFlowProcessDefVO {

    private Long id;
    private String defName;
    private String defKey;
    private String description;
    private Integer status;
    private String version;
    private String modelValue;
    private LocalDateTime createdAt;
}
