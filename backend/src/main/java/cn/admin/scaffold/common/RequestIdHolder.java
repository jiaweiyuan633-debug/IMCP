package cn.admin.scaffold.common;

import org.slf4j.MDC;

public final class RequestIdHolder {

    public static final String REQUEST_ID = "requestId";

    private RequestIdHolder() {
    }

    public static void set(String requestId) {
        MDC.put(REQUEST_ID, requestId);
    }

    public static String get() {
        return MDC.get(REQUEST_ID);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID);
    }
}

