package cn.admin.scaffold.module.system.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostOptionVo {

    private Long id;
    private String postCode;
    private String postName;
}

