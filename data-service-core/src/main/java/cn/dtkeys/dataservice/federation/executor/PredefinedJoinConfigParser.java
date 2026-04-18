package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class PredefinedJoinConfigParser {

    private final ObjectMapper objectMapper;

    public PredefinedJoinConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PredefinedJoinConfig parse(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new ServiceConfigInvalidException("跨库从源缺少 configJson 配置");
        }
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {
            });
            String joinType = requireText(config, "joinType").toUpperCase(Locale.ROOT);
            if (!"LEFT".equals(joinType) && !"INNER".equals(joinType)) {
                throw new ServiceConfigInvalidException("仅支持 LEFT 或 INNER 关联: " + joinType);
            }
            return new PredefinedJoinConfig(
                joinType,
                requireText(config, "lookupParam"),
                requireText(config, "lookupSourceColumn"),
                requireText(config, "parentJoinField"),
                requireText(config, "childJoinField"),
                requireText(config, "childSqlTemplate")
            );
        } catch (ServiceConfigInvalidException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceConfigInvalidException("跨库从源 configJson 解析失败");
        }
    }

    private String requireText(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ServiceConfigInvalidException("跨库从源缺少配置项: " + key);
        }
        return String.valueOf(value).trim();
    }
}
