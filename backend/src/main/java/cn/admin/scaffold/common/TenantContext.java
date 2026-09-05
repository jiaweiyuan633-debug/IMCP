package cn.admin.scaffold.common;

public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId == null ? Long.valueOf(1L) : tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = TENANT_ID.get();
        return tenantId == null ? Long.valueOf(1L) : tenantId;
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

