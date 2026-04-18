package cn.dtkeys.dataservice.federation.validator;

import cn.dtkeys.dataservice.federation.capability.FunctionCompatibilityRegistry;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FederatedSqlValidator {

    private static final Pattern FUNCTION_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final List<String> FORBIDDEN_KEYWORDS = List.of("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ", "TRUNCATE ");

    private final FunctionCompatibilityRegistry functionCompatibilityRegistry = new FunctionCompatibilityRegistry();

    public ValidationResult validate(FederatedParsedQuery query) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String upperSql = query.originalSql().toUpperCase(Locale.ROOT);
        for (String forbiddenKeyword : FORBIDDEN_KEYWORDS) {
            if (upperSql.contains(forbiddenKeyword)) {
                errors.add("sql contains forbidden keyword: " + forbiddenKeyword.trim());
            }
        }

        if (query.sourceTables().isEmpty()) {
            errors.add("no source table found");
        }

        Matcher matcher = FUNCTION_PATTERN.matcher(query.originalSql());
        while (matcher.find()) {
            String functionName = matcher.group(1);
            if (!"SELECT".equalsIgnoreCase(functionName) && !functionCompatibilityRegistry.isSupported(functionName)) {
                warnings.add("unsupported function will require fallback: " + functionName.toUpperCase(Locale.ROOT));
            }
        }

        if (query.joinQuery()) {
            warnings.add("join query detected, optimizer will evaluate pushdown and fallback");
        }

        Map<String, Object> details = Map.of(
            "parse", Map.of("result", "PASS", "recognizedSources", query.sourceTables()),
            "semantic", Map.of("result", errors.isEmpty() ? "PASS" : "FAIL", "warnings", warnings),
            "capability", Map.of("result", warnings.isEmpty() ? "PASS" : "WARN", "warningCount", warnings.size())
        );

        return ValidationResult.of(errors.isEmpty(), errors, warnings, query.sourceTables(), details);
    }
}
