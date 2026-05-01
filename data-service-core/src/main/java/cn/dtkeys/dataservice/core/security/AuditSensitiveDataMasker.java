package cn.dtkeys.dataservice.core.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 对审计详情和上下文中的敏感字段做结构化脱敏。
 */
@Component
public class AuditSensitiveDataMasker {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "passwordCiphertext",
            "credential",
            "credentialSecret",
            "secret",
            "token",
            "accessToken"
    );
    private static final Pattern SENSITIVE_TEXT_PATTERN =
            Pattern.compile("(?i)(password|credential|secret|token|ciphertext)");

    private final ObjectMapper objectMapper;

    public AuditSensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 尝试按 JSON 结构脱敏；若不是 JSON，则按关键字兜底模糊处理。
     */
    public String maskStructuredText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            maskNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return SENSITIVE_TEXT_PATTERN.matcher(text).find() ? "***" : text;
        }
    }

    private void maskNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            List<String> fieldNames = StreamSupport.stream(
                            java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0),
                            false
                    )
                    .collect(Collectors.toList());
            for (String fieldName : fieldNames) {
                JsonNode child = node.get(fieldName);
                if (isSensitiveKey(fieldName) && node instanceof ObjectNode objectNode) {
                    objectNode.put(fieldName, "***");
                    continue;
                }
                maskNode(child);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEYS.stream().anyMatch(sensitive -> sensitive.equalsIgnoreCase(key));
    }
}
