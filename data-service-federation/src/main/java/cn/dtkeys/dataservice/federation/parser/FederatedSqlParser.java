package cn.dtkeys.dataservice.federation.parser;

import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FederatedSqlParser {

    public FederatedParsedQuery parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }

        String normalizedSql = sql.trim();
        String upperSql = normalizedSql.toUpperCase(Locale.ROOT);
        if (!upperSql.startsWith("SELECT ")) {
            throw new IllegalArgumentException("only SELECT statements are supported");
        }

        int fromIndex = upperSql.indexOf(" FROM ");
        if (fromIndex < 0) {
            throw new IllegalArgumentException("sql must contain FROM clause");
        }

        String fieldPart = normalizedSql.substring("SELECT ".length(), fromIndex).trim();
        String afterFrom = normalizedSql.substring(fromIndex + " FROM ".length()).trim();
        int whereIndex = afterFrom.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
        String sourcePart = whereIndex >= 0 ? afterFrom.substring(0, whereIndex).trim() : afterFrom;
        String whereClause = whereIndex >= 0 ? afterFrom.substring(whereIndex + " WHERE ".length()).trim() : "";

        List<String> selectedFields = Arrays.stream(fieldPart.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();

        List<String> sourceTables = extractSources(sourcePart);

        return new FederatedParsedQuery(
            normalizedSql,
            selectedFields,
            sourceTables,
            whereClause,
            sourceTables.size() > 1 || sourcePart.toUpperCase(Locale.ROOT).contains(" JOIN ")
        );
    }

    private List<String> extractSources(String sourcePart) {
        String normalized = sourcePart
            .replaceAll("(?i)\\s+inner\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+left\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+right\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+full\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+join\\s+", ",");

        List<String> sources = new ArrayList<>();
        for (String item : normalized.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String beforeOn = trimmed.split("(?i)\\s+on\\s+")[0].trim();
            String sourceName = beforeOn.split("\\s+")[0].trim();
            if (!sourceName.isBlank()) {
                sources.add(sourceName);
            }
        }
        return sources;
    }
}
