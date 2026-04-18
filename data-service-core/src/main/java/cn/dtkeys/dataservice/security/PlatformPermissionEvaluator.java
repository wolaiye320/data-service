package cn.dtkeys.dataservice.security;

import cn.dtkeys.dataservice.common.exception.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 平台权限判断器。
 */
@Component
public class PlatformPermissionEvaluator {

    public void check(PlatformRole role, PermissionCode permissionCode, String path) {
        if (!role.hasPermission(permissionCode)) {
            throw new AccessDeniedException("当前角色无权访问接口: " + path);
        }
    }
}
