package com.cloudpool.context;
 
import lombok.extern.slf4j.Slf4j;
 
@Slf4j
public class TenantContextHolder {
     
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
 
    /**
     * Set current tenant ID
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
        log.debug("Tenant context set: {}", tenantId);
    }
 
    /**
     * Get current tenant ID
     */
    public static String getTenantId() {
        return TENANT_ID.get();
    }
 
    /**
     * Set current user ID
     */
    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }
 
    /**
     * Get current user ID
     */
    public static String getUserId() {
        return USER_ID.get();
    }
 
    /**
     * Clear context
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
