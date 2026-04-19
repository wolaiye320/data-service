package cn.dtkeys.dataservice.federation.model;

public record FederatedSourceReference(
    String sourceName,
    String sqlAlias
) {
}
