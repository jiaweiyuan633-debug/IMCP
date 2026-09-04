package cn.admin.scaffold.module.system.dto;

import lombok.Data;

@Data
public class RoleQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String code;
    private String name;
    private Integer status;
}

