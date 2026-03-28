package cn.dtkeys.dataservice.federation.capability;

import cn.dtkeys.dataservice.federation.model.SourceCapability;

import java.util.Locale;
import java.util.Map;

public class DatasourceCapabilityRegistry {

    private static final Map<String, SourceCapability> CAPABILITIES = Map.of(
        "MYSQL", new SourceCapability("MYSQL", true, true, true),
        "POSTGRESQL", new SourceCapability("POSTGRESQL", true, true, true),
        "ORACLE", new SourceCapability("ORACLE", true, true, false),
        "GENERIC", new SourceCapability("GENERIC", false, false, false)
    );

    public SourceCapability resolve(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return CAPABILITIES.get("GENERIC");
        }
        return CAPABILITIES.getOrDefault(sourceType.toUpperCase(Locale.ROOT), CAPABILITIES.get("GENERIC"));
    }
}
