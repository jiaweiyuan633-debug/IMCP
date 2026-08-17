package com.example.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证安全策略配置（批次1·安全阻断）。
 *
 * <ul>
 *   <li>{@code forcePasswordChange}：默认口令（种子 admin/admin123 等）强制首登改密开关。
 *       生产（prod）默认开启——V63 迁移已把仍使用默认种子哈希的账号标记 must_change_password=1，
 *       登录成功但响应携带 mustChangePassword=true，前端强制跳转改密页；本地 dev/test 默认关闭，
 *       保持 admin/admin123 可直接登录的开发与 CI 体验（e2e/smoke/load-test 均依赖该默认口令）。</li>
 *   <li>{@code passwordExpireDays}：密码过期天数（0=禁用）。登录时检查 password_changed_at 距今
 *       是否超过该天数，超过则同样进入强制改密流程。</li>
 *   <li>{@code refreshCookie*}：refresh token 的 httpOnly Cookie 参数（批次1·P1-F3）。
 *       Secure 仅生产开启（本地 http 下 Secure cookie 不会被保存），SameSite=Lax 兼容
 *       localhost 跨端口（dev 直连 8080）与生产同源反代两种拓扑。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** 默认口令强制首登改密：prod 默认 true，dev/test 显式覆盖为 false。 */
    private boolean forcePasswordChange = true;

    /** 密码过期天数（0=禁用）。 */
    private int passwordExpireDays = 90;

    /** refresh token cookie 名称。 */
    private String refreshCookieName = "admin_refresh_token";

    /** refresh token cookie Secure 属性（仅 https 部署开启）。 */
    private boolean refreshCookieSecure = false;

    /** refresh token cookie 路径（与后端 API 前缀一致）。 */
    private String refreshCookiePath = "/api";

    /** refresh token cookie SameSite 属性（Lax 兼容同站跨端口与同源反代）。 */
    private String refreshCookieSameSite = "Lax";
}
