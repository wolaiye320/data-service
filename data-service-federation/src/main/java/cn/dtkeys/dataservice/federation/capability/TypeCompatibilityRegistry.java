package cn.dtkeys.dataservice.federation.capability;

import java.util.Locale;
import java.util.Set;

public class TypeCompatibilityRegistry {

    private static final Set<String> NUMERIC_TYPES = Set.of("INT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "DOUBLE");
    private static final Set<String> TEXT_TYPES = Set.of("CHAR", "VARCHAR", "TEXT");

    public boolean areComparable(String leftType, String rightType) {
        if (leftType == null || rightType == null) {
            return false;
        }
        String normalizedLeft = leftType.toUpperCase(Locale.ROOT);
        String normalizedRight = rightType.toUpperCase(Locale.ROOT);
        return normalizedLeft.equals(normalizedRight)
            || NUMERIC_TYPES.contains(normalizedLeft) && NUMERIC_TYPES.contains(normalizedRight)
            || TEXT_TYPES.contains(normalizedLeft) && TEXT_TYPES.contains(normalizedRight);
    }
}
