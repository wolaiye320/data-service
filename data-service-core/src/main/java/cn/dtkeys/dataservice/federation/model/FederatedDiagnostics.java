package cn.dtkeys.dataservice.federation.model;

import java.util.List;

public record FederatedDiagnostics(
    List<String> logicalPlan,
    List<String> stagePlan,
    List<String> pushdownSummary,
    List<String> fallbackReasons
) {
}
