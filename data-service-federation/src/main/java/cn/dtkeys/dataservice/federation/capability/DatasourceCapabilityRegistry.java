package cn.dtkeys.dataservice.federation.capability;

import cn.dtkeys.dataservice.federation.model.SourceCapability;

import java.util.Locale;
import java.util.Map;

public class DatasourceCapabilityRegistry {

    private static final Map<String, SourceCapability> CAPABILITIES = Map.of(
        "MYSQL", new SourceCapability("MYSQL", true, true, true,
            Map.of("recommendedJoinStrategy", "PUSHDOWN", "estimatedRows", 1000, "costWeight", 1)),
        "POSTGRESQL", new SourceCapability("POSTGRESQL", true, true, true,
            Map.of("recommendedJoinStrategy", "PUSHDOWN", "estimatedRows", 1000, "costWeight", 1)),
        "ORACLE", new SourceCapability("ORACLE", true, true, false,
            Map.of("recommendedJoinStrategy", "LOOKUP", "estimatedRows", 1500, "costWeight", 2)),
        "GENERIC", new SourceCapability("GENERIC", false, false, false,
            Map.of("recommendedJoinStrategy", "LOCAL", "estimatedRows", 3000, "costWeight", 5))
    );

    public SourceCapability resolve(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return CAPABILITIES.get("GENERIC");
        }
        return CAPABILITIES.getOrDefault(sourceType.toUpperCase(Locale.ROOT), CAPABILITIES.get("GENERIC"));
    }
}
