package cn.dtkeys.dataservice.infrastructure.security;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 平台角色与基础权限映射。
 */
public enum PlatformRole {
    ADMIN(EnumSet.allOf(PermissionCode.class)),
    DEVELOPER(EnumSet.of(
        PermissionCode.PLATFORM_BASELINE_VIEW,
        PermissionCode.DATA_SERVICE_QUERY,
        PermissionCode.SERVICE_DEFINITION_MANAGE
    )),
    CALLER(EnumSet.of(PermissionCode.PLATFORM_BASELINE_VIEW, PermissionCode.DATA_SERVICE_QUERY)),
    SYSTEM(EnumSet.allOf(PermissionCode.class));

    private final Set<PermissionCode> permissions;

    PlatformRole(Set<PermissionCode> permissions) {
        this.permissions = permissions;
    }

    public boolean hasPermission(PermissionCode permissionCode) {
        return permissions.contains(permissionCode);
    }

    public static PlatformRole fromHeader(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return CALLER;
        }
        try {
            return PlatformRole.valueOf(rawRole.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CALLER;
        }
    }
}
