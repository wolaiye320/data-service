package cn.dtkeys.dataservice.federation.executor;

public record FederatedExecutionOptions(
    int maxParallelism,
    int maxResultRows,
    int previewRows,
    int inMemoryBudgetRows
) {

    public static FederatedExecutionOptions defaults() {
        return new FederatedExecutionOptions(2, 1000, 200, 500);
    }
}
