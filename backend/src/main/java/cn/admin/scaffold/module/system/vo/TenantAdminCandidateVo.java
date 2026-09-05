package cn.admin.scaffold.module.system.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 租户管理员候选用户出参 VO，附带租户信息用于前端"租户 + 用户名"展示。
 */
@Data
@Builder
public class TenantAdminCandidateVo {

    private Long id;
    private String username;
    private String nickname;
    private Long tenantId;
    private String tenantName;
}
