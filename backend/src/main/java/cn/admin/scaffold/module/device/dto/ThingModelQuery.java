package cn.admin.scaffold.module.device.dto;

import lombok.Data;

@Data
public class ThingModelQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String deviceType;
    private String name;
    private Integer status;
}
