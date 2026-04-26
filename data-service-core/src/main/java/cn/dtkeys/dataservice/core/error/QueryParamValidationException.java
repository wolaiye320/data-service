package cn.dtkeys.dataservice.core.error;

import java.util.List;

public class QueryParamValidationException extends DataServiceException {

    private final List<ParamDiagnostic> diagnostics;

    public QueryParamValidationException(String message, List<ParamDiagnostic> diagnostics) {
        super(ErrorCode.INVALID_ARGUMENT, message);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<ParamDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public record ParamDiagnostic(
            String reasonCode,
            String paramName,
            String message,
            String expectedType,
            String actualType,
            boolean collection
    ) {
    }
}
