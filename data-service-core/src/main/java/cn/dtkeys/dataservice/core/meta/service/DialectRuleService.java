package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsDialectRuleRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsDialectRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一封装方言规则的优先级、生效集合和表达式重写入口。
 */
@Service
public class DialectRuleService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Comparator<EffectiveDialectRule> RULE_ORDER =
            Comparator.comparing(EffectiveDialectRule::priority)
                    .thenComparing(EffectiveDialectRule::ruleCode)
                    .thenComparing(EffectiveDialectRule::id);

    private final DsDialectRuleRepository dialectRuleRepository;
    private final ObjectMapper objectMapper;

    public DialectRuleService(DsDialectRuleRepository dialectRuleRepository, ObjectMapper objectMapper) {
        this.dialectRuleRepository = dialectRuleRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载连接当前生效的方言规则。连接级规则优先于 dbType 默认规则。
     */
    public List<EffectiveDialectRule> loadEffectiveRules(DsConnectionRecord connection) {
        if (connection == null) {
            return List.of();
        }
        Map<String, EffectiveDialectRule> effectiveRules = new LinkedHashMap<>();
        dialectRuleRepository.findEnabledByDbType(connection.getDbType()).stream()
                .map(record -> toEffectiveRule(record, RulePriority.DB_TYPE_DEFAULT))
                .forEach(rule -> effectiveRules.put(rule.ruleCode(), rule));
        dialectRuleRepository.findEnabledByConnectionId(connection.getId()).stream()
                .map(record -> toEffectiveRule(record, RulePriority.CONNECTION_OVERRIDE))
                .forEach(rule -> effectiveRules.put(rule.ruleCode(), rule));
        return effectiveRules.values().stream()
                .sorted(RULE_ORDER)
                .toList();
    }

    /**
     * 统一的 SQL 改写入口。当前仅处理函数映射型规则。
     */
    public RewriteResult rewriteSql(String sql, DsConnectionRecord connection) {
        List<EffectiveDialectRule> effectiveRules = loadEffectiveRules(connection);
        String rewrittenSql = sql;
        int appliedRuleCount = 0;
        for (EffectiveDialectRule rule : effectiveRules) {
            if (!"FUNCTION_MAPPING".equalsIgnoreCase(rule.ruleType())) {
                continue;
            }
            String rewritten = applyFunctionMapping(rewrittenSql, rule);
            if (!rewritten.equals(rewrittenSql)) {
                rewrittenSql = rewritten;
                appliedRuleCount++;
            }
        }
        return new RewriteResult(rewrittenSql, appliedRuleCount, effectiveRules);
    }

    private EffectiveDialectRule toEffectiveRule(DsDialectRuleRecord record, RulePriority priority) {
        return new EffectiveDialectRule(
                record.getId(),
                record.getConnectionId(),
                record.getDbType(),
                record.getRuleCode(),
                record.getRuleType(),
                record.getRuleConfig(),
                priority
        );
    }

    private String applyFunctionMapping(String sql, EffectiveDialectRule rule) {
        Map<String, Object> config = parseRuleConfig(rule.ruleConfig());
        String source = stringValue(config.get("source"));
        String target = stringValue(config.get("target"));
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return sql;
        }
        if (source.equalsIgnoreCase(target)) {
            return sql;
        }
        return sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(source) + "\\s*\\(", target + "(");
    }

    private Map<String, Object> parseRuleConfig(String ruleConfig) {
        if (ruleConfig == null || ruleConfig.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(ruleConfig, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("方言规则配置不是合法 JSON", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public enum RulePriority {
        CONNECTION_OVERRIDE,
        DB_TYPE_DEFAULT
    }

    public record EffectiveDialectRule(
            Long id,
            Long connectionId,
            String dbType,
            String ruleCode,
            String ruleType,
            String ruleConfig,
            RulePriority priority
    ) {
    }

    public record RewriteResult(
            String sql,
            int appliedRuleCount,
            List<EffectiveDialectRule> effectiveRules
    ) {
    }
}
