package cn.admin.scaffold.module.system.dto;

import lombok.Data;

@Data
public class ApiPermQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String method;
    private String pathPattern;
    private Integer enabled;
}
