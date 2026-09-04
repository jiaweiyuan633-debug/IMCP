package cn.admin.scaffold.module.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ThingModelSaveRequest {

    private Long id;

    @NotBlank(message = "物模型类型编码不能为空")
    @Size(max = 64, message = "物模型类型编码长度不能超过 64")
    private String deviceType;

    @NotBlank(message = "物模型名称不能为空")
    @Size(max = 100, message = "物模型名称长度不能超过 100")
    private String name;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;

    private String propertiesJson;
    private String eventsJson;
    private String servicesJson;
    private Integer status;

    /** 乐观锁版本号：编辑时由列表/详情回传，冲突时服务端拒绝覆盖；新增不传。 */
    private Integer version;
}
