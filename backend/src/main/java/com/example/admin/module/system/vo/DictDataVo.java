package com.example.admin.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DictDataVo {

    private Long id;
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer dictSort;
    private String listClass;
    private Integer isDefault;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
}

