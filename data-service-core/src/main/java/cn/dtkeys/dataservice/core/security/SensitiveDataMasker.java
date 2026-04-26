package cn.dtkeys.dataservice.core.security;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    /**
     * 脱敏用户名。
     */
    public String maskUsername(String username) {
        if (username == null || username.isBlank()) {
            return username;
        }
        if (username.length() <= 2) {
            return username.charAt(0) + "*";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    /**
     * 脱敏密码密文展示。
     */
    public String maskPassword() {
        return "******";
    }

    /**
     * 脱敏主机。
     */
    public String maskHost(String host) {
        if (host == null || host.isBlank()) {
            return host;
        }
        int firstDot = host.indexOf('.');
        if (firstDot <= 1) {
            return "***";
        }
        return host.substring(0, 1) + "***" + host.substring(firstDot);
    }
}
