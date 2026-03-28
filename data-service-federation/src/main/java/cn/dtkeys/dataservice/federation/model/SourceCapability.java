package cn.dtkeys.dataservice.federation.model;

public record SourceCapability(
    String sourceType,
    boolean filterPushdown,
    boolean projectPushdown,
    boolean joinPushdown
) {
}
