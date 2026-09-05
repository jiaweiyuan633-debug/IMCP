package cn.admin.scaffold.common;

/**
 * 密码复杂度策略。
 *
 * <p>统一升级为「8-32 位，需同时包含大写字母、小写字母、数字与特殊字符」——此前
 * {@code ^(?=.*[A-Za-z])(?=.*\\d).{8,32}$} 只要求字母+数字，全小写弱口令（password、
 * a1234567）均可通过。正则与提示文案集中在此，DTO {@code @Pattern} 与 Service 层显式校验
 * 共用同一常量，避免三处正则漂移；前端 {@code validation.ts} 的 PASSWORD_PATTERN 需人工同步
 * （跨语言无法共享），改动正则时需同步更新。
 */
public final class PasswordPolicy {

    /** 8-32 位，含大写/小写/数字/特殊字符（\W 之外的 _ 需显式纳入）。 */
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,32}$";

    public static final String MESSAGE = "密码需 8-32 位，且包含大写字母、小写字母、数字和特殊字符";

    private PasswordPolicy() {
    }

    public static boolean matches(String value) {
        return value != null && value.matches(PATTERN);
    }
}
