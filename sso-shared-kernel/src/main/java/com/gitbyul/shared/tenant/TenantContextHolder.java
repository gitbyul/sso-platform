package com.gitbyul.shared.tenant;

/**
 * 요청 단위 테넌트 컨텍스트.
 * <p>
 * ThreadLocal 기반이며, {@link #clear()} 호출로 메모리 릭을 방지해야 한다.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static String get() {
        return TENANT_ID.get();
    }

    public static void set(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

