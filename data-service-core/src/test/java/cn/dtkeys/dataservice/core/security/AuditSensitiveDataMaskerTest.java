package cn.dtkeys.dataservice.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSensitiveDataMaskerTest {

    private final AuditSensitiveDataMasker masker = new AuditSensitiveDataMasker(new ObjectMapper());

    @Test
    void shouldMaskSensitiveJsonKeysRecursively() {
        String masked = masker.maskStructuredText("""
                {"password":"plain","nested":{"token":"secret-token","callerId":"caller-a"}}
                """);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"token\":\"***\"");
        assertThat(masked).contains("\"callerId\":\"caller-a\"");
        assertThat(masked).doesNotContain("plain");
        assertThat(masked).doesNotContain("secret-token");
    }

    @Test
    void shouldMaskSensitivePlainTextFallback() {
        assertThat(masker.maskStructuredText("password=plain-secret")).isEqualTo("***");
        assertThat(masker.maskStructuredText("normal text")).isEqualTo("normal text");
    }
}
