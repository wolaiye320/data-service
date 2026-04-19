package cn.dtkeys.dataservice.federation.parser;

import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedSourceReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FederatedSqlParser {

    private static final Pattern SELECT_PATTERN = Pattern.compile("(?is)^SELECT\\s+(.*?)\\s+FROM\\s+(.*)$");
    private static final Pattern WHERE_PATTERN = Pattern.compile("(?is)^(.*?)(?:\\s+WHERE\\s+(.*))?$");
    private static final Pattern TRAILING_CLAUSE_PATTERN =
        Pattern.compile("(?is)\\s+(GROUP\\s+BY|HAVING|ORDER\\s+BY|LIMIT|OFFSET)\\s+.*$");

    public FederatedParsedQuery parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }

        String normalizedSql = sql.trim();
        String upperSql = normalizedSql.toUpperCase(Locale.ROOT);
        if (!upperSql.startsWith("SELECT ")) {
            throw new IllegalArgumentException("only SELECT statements are supported");
        }

        Matcher selectMatcher = SELECT_PATTERN.matcher(normalizedSql);
        if (!selectMatcher.matches()) {
            throw new IllegalArgumentException("sql must contain FROM clause");
        }

        String fieldPart = selectMatcher.group(1).trim();
        String afterFrom = selectMatcher.group(2).trim();
        Matcher whereMatcher = WHERE_PATTERN.matcher(afterFrom);
        String sourcePart = afterFrom;
        String whereClause = "";
        if (whereMatcher.matches()) {
            sourcePart = whereMatcher.group(1).trim();
            String parsedWhereClause = whereMatcher.group(2);
            whereClause = parsedWhereClause == null ? "" : parsedWhereClause.trim();
        }

        List<String> selectedFields = splitTopLevelByComma(fieldPart);

        List<FederatedSourceReference> sourceReferences = extractSourceReferences(sourcePart);
        List<String> sourceTables = sourceReferences.stream()
            .map(FederatedSourceReference::sourceName)
            .toList();

        return new FederatedParsedQuery(
            normalizedSql,
            selectedFields,
            sourceTables,
            sourceReferences,
            whereClause,
            sourceTables.size() > 1 || sourcePart.toUpperCase(Locale.ROOT).contains(" JOIN ")
        );
    }

    private List<FederatedSourceReference> extractSourceReferences(String sourcePart) {
        String normalized = sourcePart
            .replaceAll("(?i)\\s+inner\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+left\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+right\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+full\\s+join\\s+", ",")
            .replaceAll("(?i)\\s+join\\s+", ",");

        List<FederatedSourceReference> sources = new ArrayList<>();
        for (String item : normalized.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String beforeOn = trimmed.split("(?i)\\s+on\\s+")[0].trim();
            Matcher trailingMatcher = TRAILING_CLAUSE_PATTERN.matcher(beforeOn);
            if (trailingMatcher.find()) {
                beforeOn = beforeOn.substring(0, trailingMatcher.start()).trim();
            }
            String[] tokens = beforeOn.split("\\s+");
            String sourceName = tokens[0].trim();
            if (!sourceName.isBlank()) {
                sources.add(new FederatedSourceReference(sourceName, resolveSqlAlias(sourceName, tokens)));
            }
        }
        return sources;
    }

    private List<String> splitTopLevelByComma(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenthesesDepth = 0;
        boolean inSingleQuote = false;
        for (int index = 0; index < text.length(); index++) {
            char currentChar = text.charAt(index);
            if (currentChar == '\'' && (index == 0 || text.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote) {
                if (currentChar == '(') {
                    parenthesesDepth++;
                } else if (currentChar == ')') {
                    parenthesesDepth = Math.max(0, parenthesesDepth - 1);
                } else if (currentChar == ',' && parenthesesDepth == 0) {
                    appendPart(parts, current);
                    continue;
                }
            }
            current.append(currentChar);
        }
        appendPart(parts, current);
        return List.copyOf(parts);
    }

    private void appendPart(List<String> parts, StringBuilder current) {
        String item = current.toString().trim();
        if (!item.isBlank()) {
            parts.add(item);
        }
        current.setLength(0);
    }

    private String resolveSqlAlias(String sourceName, String[] tokens) {
        if (tokens.length >= 3 && "AS".equalsIgnoreCase(tokens[1])) {
            return tokens[2].trim();
        }
        if (tokens.length >= 2) {
            return tokens[1].trim();
        }
        int dotIndex = sourceName.lastIndexOf('.');
        return dotIndex >= 0 ? sourceName.substring(dotIndex + 1) : sourceName;
    }
}
