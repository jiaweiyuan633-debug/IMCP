package com.example.admin.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PostVo {

    private Long id;
    private String postCode;
    private String postName;
    private Integer sort;
    private Integer status;
    private String description;
    private LocalDateTime createdAt;
}

