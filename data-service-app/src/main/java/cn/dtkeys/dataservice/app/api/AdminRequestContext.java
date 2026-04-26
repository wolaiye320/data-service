package cn.dtkeys.dataservice.app.api;

import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;

public final class AdminRequestContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String ATTRIBUTE_OPERATOR_CONTEXT = "adminOperatorContext";
    public static final String ATTRIBUTE_TRACE_ID = "traceId";

    private AdminRequestContext() {
    }

    public static OperatorContext operatorContextOf(Object value) {
        return (OperatorContext) value;
    }
}
