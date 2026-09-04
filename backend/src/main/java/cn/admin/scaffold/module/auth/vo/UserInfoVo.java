package cn.admin.scaffold.module.auth.vo;

import cn.admin.scaffold.module.system.vo.MenuVo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoVo {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private List<String> roles;
    private List<String> perms;
    private List<MenuVo> menus;
    /** 当前用户是否处于"必须修改密码"状态（默认口令首登 / 密码过期）。 */
    private boolean mustChangePassword;
}
