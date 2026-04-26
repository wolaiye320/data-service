package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class QueryAccessGuardService {

    public void validate(QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        if (requestContext == null || requestContext.callerId() == null || requestContext.callerId().isBlank()) {
            throw new AccessDeniedException("查询接口要求提供调用方标识 callerId");
        }
    }
}
