package cn.dtkeys.dataservice.common.context;

import java.util.Optional;

/**
 * 统一维护当前线程 traceId。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static Optional<String> getTraceId() {
        return Optional.ofNullable(TRACE_ID_HOLDER.get());
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}
