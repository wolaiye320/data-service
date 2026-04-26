package cn.dtkeys.dataservice.core.error;

import java.util.List;

public class FederatedQueryExecutionException extends DataServiceException {

    private final List<FederatedDiagnostic> diagnostics;

    public FederatedQueryExecutionException(String message, List<FederatedDiagnostic> diagnostics) {
        super(ErrorCode.INVALID_ARGUMENT, message);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<FederatedDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public record FederatedDiagnostic(
            String path,
            String reasonCode,
            String connectionCode,
            String alias,
            String message
    ) {
    }
}
