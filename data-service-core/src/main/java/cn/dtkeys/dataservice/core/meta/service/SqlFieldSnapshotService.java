package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SqlFieldSnapshotService {

    private final ObjectMapper objectMapper;

    public SqlFieldSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析简单 SQL 的 select 输出字段并生成快照。
     */
    public String buildFieldSnapshotJson(String sqlType, String sqlText) {
        if (!"SIMPLE_SQL".equals(sqlType) && !"FEDERATED_SQL".equals(sqlType)) {
            return "[]";
        }
        List<FieldSnapshot> snapshots = parseSelectItems(sqlText);
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "字段快照序列化失败", ex);
        }
    }

    private List<FieldSnapshot> parseSelectItems(String sqlText) {
        int selectIndex = indexOfKeyword(sqlText, "select", 0);
        int fromIndex = indexOfFrom(sqlText, selectIndex + 6);
        if (selectIndex < 0 || fromIndex < 0 || fromIndex <= selectIndex) {
            return List.of();
        }

        String selectClause = sqlText.substring(selectIndex + 6, fromIndex).trim();
        List<String> items = splitSelectItems(selectClause);
        List<FieldSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i).trim();
            if (item.isEmpty()) {
                continue;
            }
            snapshots.add(toSnapshot(item, i + 1));
        }
        return snapshots;
    }

    private FieldSnapshot toSnapshot(String item, int sortOrder) {
        if ("*".equals(item) || item.endsWith(".*")) {
            return new FieldSnapshot(item, item, sortOrder);
        }

        int asIndex = findAsKeyword(item);
        if (asIndex >= 0) {
            String expression = item.substring(0, asIndex).trim();
            String alias = item.substring(asIndex + 4).trim();
            if (alias.isEmpty()) {
                throw invalid("复杂表达式输出列必须显式使用 as 别名");
            }
            return new FieldSnapshot(alias, expression, sortOrder);
        }

        if (isSimpleColumn(item)) {
            return new FieldSnapshot(extractSimpleColumnName(item), item, sortOrder);
        }

        throw invalid("复杂表达式输出列必须显式使用 as 别名");
    }

    private boolean isSimpleColumn(String item) {
        return item.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    }

    private String extractSimpleColumnName(String item) {
        int dot = item.lastIndexOf('.');
        return dot >= 0 ? item.substring(dot + 1) : item;
    }

    private List<String> splitSelectItems(String selectClause) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < selectClause.length(); i++) {
            char ch = selectClause.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                } else if (ch == ',' && depth == 0) {
                    items.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            items.add(current.toString());
        }
        return items;
    }

    private int indexOfFrom(String sqlText, int start) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = start; i < sqlText.length(); i++) {
            char ch = sqlText.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                } else if (depth == 0 && matchesKeyword(sqlText, i, "from")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int indexOfKeyword(String sqlText, String keyword, int start) {
        String lower = sqlText.toLowerCase(Locale.ROOT);
        return lower.indexOf(keyword, start);
    }

    private int findAsKeyword(String item) {
        String lower = item.toLowerCase(Locale.ROOT);
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i <= lower.length() - 4; i++) {
            char ch = lower.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                } else if (depth == 0 && lower.startsWith(" as ", i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean matchesKeyword(String text, int index, String keyword) {
        if (index < 0 || index + keyword.length() > text.length()) {
            return false;
        }
        String fragment = text.substring(index, index + keyword.length()).toLowerCase(Locale.ROOT);
        if (!keyword.equals(fragment)) {
            return false;
        }
        boolean prefixValid = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
        int end = index + keyword.length();
        boolean suffixValid = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return prefixValid && suffixValid;
    }

    private DataServiceException invalid(String message) {
        return new DataServiceException(ErrorCode.INVALID_ARGUMENT, message);
    }

    public record FieldSnapshot(String fieldName, String expression, int sortOrder) {
    }
}
