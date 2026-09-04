package cn.admin.scaffold.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserVo {

    private Long id;
    private Long deptId;
    private String deptName;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createdAt;
    private List<Long> roleIds;
    private List<String> roleNames;
    private List<Long> postIds;
    private List<String> postNames;
}

