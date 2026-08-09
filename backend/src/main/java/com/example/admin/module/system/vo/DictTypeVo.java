package com.example.admin.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DictTypeVo {

    private Long id;
    private String dictName;
    private String dictType;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
}

