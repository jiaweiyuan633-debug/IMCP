package cn.admin.scaffold.module.device.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ThingModelVo {

    private Long id;
    private String deviceType;
    private String name;
    private String description;
    private String propertiesJson;
    private String eventsJson;
    private String servicesJson;
    private Integer status;
    /** 乐观锁版本号：编辑时需原样回传，供服务端检测并发修改。 */
    private Integer version;
    private LocalDateTime createdAt;
}
