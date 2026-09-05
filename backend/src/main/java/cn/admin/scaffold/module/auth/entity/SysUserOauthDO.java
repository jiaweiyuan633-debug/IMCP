package cn.admin.scaffold.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户-第三方账号绑定。 */
@Data
@TableName("sys_user_oauth")
public class SysUserOauthDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String provider;
    private String openId;
    private String unionId;
    private String nickname;
    private String avatar;
    private LocalDateTime createdAt;
}
