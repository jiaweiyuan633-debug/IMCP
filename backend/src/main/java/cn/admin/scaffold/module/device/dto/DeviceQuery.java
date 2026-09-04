package cn.admin.scaffold.module.device.dto;

import lombok.Data;

@Data
public class DeviceQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String deviceCode;
    private String deviceName;
    private Integer status;
}
