package cn.admin.scaffold.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RoleVo {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer dataScope;
    private Integer sort;
    private LocalDateTime createdAt;
    private List<Long> menuIds;
    private List<Long> deptIds;
}

