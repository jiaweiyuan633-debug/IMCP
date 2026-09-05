package cn.admin.scaffold.module.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginConfigVo {

    private boolean captchaEnabled;
}

