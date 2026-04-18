package cn.dtkeys.dataservice.federation.model;

import java.util.Map;

public record SourceCapability(
    String sourceType,
    boolean filterPushdown,
    boolean projectPushdown,
    boolean joinPushdown,
    Map<String, Object> attributes
) {
}
