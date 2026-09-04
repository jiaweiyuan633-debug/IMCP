package cn.admin.scaffold.module.system.dto;

import lombok.Data;

@Data
public class DictDataQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String dictType;
    private String dictLabel;
    private Integer status;
}

