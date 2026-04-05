package cn.dtkeys.dataservice.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

@Component
public class CacheKeyGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generate(String serviceCode, int version, Map<String, Object> params) {
        String paramHash = hash(normalize(params));
        return "data-service:" + serviceCode + ":v" + version + ":" + paramHash;
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), normalize(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(this::normalize)
                .toList();
        }
        return value;
    }

    private String hash(Object normalizedParams) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(normalizedParams);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("生成缓存键失败", exception);
        }
    }
}
