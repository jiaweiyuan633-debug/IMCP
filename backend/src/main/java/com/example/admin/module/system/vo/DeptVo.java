package com.example.admin.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeptVo {

    private Long id;
    private Long parentId;
    private String deptName;
    private Integer orderNum;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
    private List<DeptVo> children;
}

