package cn.dtkeys.dataservice.federation.capability;

import java.util.Locale;
import java.util.Set;

public class FunctionCompatibilityRegistry {

    private static final Set<String> SUPPORTED_FUNCTIONS = Set.of(
        "COUNT",
        "SUM",
        "AVG",
        "MIN",
        "MAX",
        "UPPER",
        "LOWER"
    );

    public boolean isSupported(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            return false;
        }
        return SUPPORTED_FUNCTIONS.contains(functionName.toUpperCase(Locale.ROOT));
    }
}
