package cn.admin.scaffold.module.system.dto;

import lombok.Data;

@Data
public class UserQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String username;
    private String nickname;
    private Integer status;
}

