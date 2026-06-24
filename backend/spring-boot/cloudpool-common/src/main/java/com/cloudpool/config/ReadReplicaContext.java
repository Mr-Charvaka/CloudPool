package com.cloudpool.config;

public class ReadReplicaContext {

    private static final ThreadLocal<Boolean> READ_ONLY = ThreadLocal.withInitial(() -> false);

    public static void setReadOnly(boolean readOnly) {
        READ_ONLY.set(readOnly);
    }

    public static boolean isReadOnly() {
        return READ_ONLY.get();
    }

    public static void clear() {
        READ_ONLY.remove();
    }
}