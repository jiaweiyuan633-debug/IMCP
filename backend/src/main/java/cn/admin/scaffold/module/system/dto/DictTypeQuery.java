package cn.admin.scaffold.module.system.dto;

import lombok.Data;

@Data
public class DictTypeQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String dictName;
    private String dictType;
    private Integer status;
}

