package cn.admin.scaffold.module.system.dto;

import lombok.Data;

@Data
public class PostQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String postCode;
    private String postName;
    private Integer status;
}

