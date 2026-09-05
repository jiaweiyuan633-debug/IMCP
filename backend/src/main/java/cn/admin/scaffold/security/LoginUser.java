package cn.admin.scaffold.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@Builder
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long deptId;
    private String username;
    private String nickname;
    private List<String> roles;
    private List<String> perms;

    /** 口令生命周期拦截标记：must_change_password=1 或口令已过期（JwtAuthenticationFilter 按库判定后写入，
     *  PasswordPolicyEnforcementFilter 据此对受限账号返回 403；避免每个业务请求重复查库）。 */
    private boolean passwordChangeRequired;

    /** 防御快照：调用方拿到的是不可变副本，无法经 getter 篡改主体角色/权限。 */
    public List<String> getRoles() {
        return roles == null ? null : List.copyOf(roles);
    }

    public List<String> getPerms() {
        return perms == null ? null : List.copyOf(perms);
    }

    /** 入参拷贝：Jackson 反序列化（Redis 往返）不持有外部可变集合引用。 */
    public void setRoles(List<String> roles) {
        this.roles = roles == null ? null : new ArrayList<>(roles);
    }

    public void setPerms(List<String> perms) {
        this.perms = perms == null ? null : new ArrayList<>(perms);
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        if (perms != null) {
            perms.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
        }
        return authorities;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

