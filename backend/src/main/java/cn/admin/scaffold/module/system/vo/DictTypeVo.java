package cn.admin.scaffold.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DictTypeVo {

    private Long id;
    private String dictName;
    private String dictType;
    private Integer status;
    /** 是否共享字典：1=全局共享（tenant_id=0） 0=租户私有。 */
    private Integer isShared;
    private String remark;
    private LocalDateTime createdAt;
}

