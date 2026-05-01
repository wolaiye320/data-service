package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 校验正式查询最基本的调用方身份要求。
 */
@Service
public class QueryAccessGuardService {

    /**
     * 要求调用方显式提供 callerId。
     */
    public void validate(QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        if (requestContext == null || requestContext.callerId() == null || requestContext.callerId().isBlank()) {
            throw new AccessDeniedException("查询接口要求提供调用方标识 callerId");
        }
    }
}
