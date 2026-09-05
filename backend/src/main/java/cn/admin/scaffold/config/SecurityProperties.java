package cn.admin.scaffold.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 认证安全策略配置（默认口令/密码过期强制、API 权限严格模式）。
 *
 * <ul>
 *   <li>{@code forcePasswordChange}：默认口令（种子 admin/admin123 等）强制首登改密开关。
 *       生产（prod）默认开启——V63 迁移已把仍使用默认种子哈希的账号标记 must_change_password=1，
 *       登录成功但响应携带 mustChangePassword=true，前端强制跳转改密页；本地 dev/test 默认关闭，
 *       保持 admin/admin123 可直接登录的开发与 CI 体验（e2e/smoke/load-test 均依赖该默认口令）。
 *       本开关同时是「服务端口令生命周期强制」的开关：开启后，除改密/登出/刷新/自身信息外的
 *       业务接口对被标记或口令过期的账号一律 403 拦截（见 PasswordPolicyEnforcementFilter），
 *       非浏览器/CLI 客户端同样被拦。</li>
 *   <li>{@code passwordExpireDays}：密码过期天数（0=禁用）。登录时检查 password_changed_at 距今
 *       是否超过该天数，超过则同样进入强制改密流程。</li>
 *   <li>{@code apiPermStrict}：API 资源权限规则严格模式（默认 false）。开启后，已认证请求若
 *       命不中 sys_api_perm 任何规则（且非账号自助白名单）将被 403 拒绝，用于暴露规则漏配；
 *       开启前需确保全部受管接口都已登记权限规则。</li>
 *   <li>{@code refreshCookie*}：refresh token 的 httpOnly Cookie 参数（P1-F3：refresh 迁入
 *       httpOnly Cookie 防 XSS 窃取）。Secure 仅生产开启（本地 http 下 Secure cookie 不会被保存），
 *       SameSite=Lax 兼容 localhost 跨端口（dev 直连 8080）与生产同源反代两种拓扑。</li>
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

    /** API 资源权限规则严格模式（默认 false，见类注释）。 */
    private boolean apiPermStrict = false;

    /** refresh token cookie 名称。 */
    private String refreshCookieName = "admin_refresh_token";

    /** refresh token cookie Secure 属性（仅 https 部署开启）。 */
    private boolean refreshCookieSecure = false;

    /** refresh token cookie 路径（与后端 API 前缀一致）。 */
    private String refreshCookiePath = "/api";

    /** refresh token cookie SameSite 属性（Lax 兼容同站跨端口与同源反代）。 */
    private String refreshCookieSameSite = "Lax";

    /**
     * 口令生命周期统一判定（服务端拦截与登录/刷新响应共用同一口径，避免两处漂移）：
     * 开关关闭时一律不强制；开启后，must_change_password=1（默认口令/管理员重置未改）或
     * password_changed_at 距今超过 password_expire_days（0 禁用）即判定需要改密。
     */
    public boolean isPasswordChangeRequired(Integer mustChangePassword, LocalDateTime passwordChangedAt) {
        if (!forcePasswordChange) {
            return false;
        }
        boolean flagged = mustChangePassword != null && mustChangePassword == 1;
        if (flagged) {
            return true;
        }
        if (passwordExpireDays <= 0 || passwordChangedAt == null) {
            return false;
        }
        return passwordChangedAt.plusDays(passwordExpireDays).isBefore(LocalDateTime.now());
    }
}
