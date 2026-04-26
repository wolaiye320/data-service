package cn.dtkeys.dataservice.app.api.service;

import cn.dtkeys.dataservice.app.api.AdminRequestContext;
import cn.dtkeys.dataservice.core.meta.service.CachePolicyService;
import cn.dtkeys.dataservice.core.meta.web.request.CachePolicyUpsertRequest;
import cn.dtkeys.dataservice.core.meta.web.response.CachePolicyResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/services/{id}/cache-policy")
public class AdminCachePolicyController {

    private final CachePolicyService cachePolicyService;

    public AdminCachePolicyController(CachePolicyService cachePolicyService) {
        this.cachePolicyService = cachePolicyService;
    }

    @GetMapping
    public CachePolicyResponse detail(@PathVariable("id") Long id) {
        return cachePolicyService.detail(id);
    }

    @PutMapping
    public CachePolicyResponse upsert(@PathVariable("id") Long id,
                                      @Valid @RequestBody CachePolicyUpsertRequest request,
                                      HttpServletRequest httpServletRequest) {
        return cachePolicyService.upsert(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @DeleteMapping("/cache")
    public CachePolicyService.CacheEvictResponse clearCache(@PathVariable("id") Long id,
                                                           HttpServletRequest httpServletRequest) {
        return cachePolicyService.clearCache(id, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    private OperatorContext operatorContext(HttpServletRequest request) {
        return AdminRequestContext.operatorContextOf(request.getAttribute(AdminRequestContext.ATTRIBUTE_OPERATOR_CONTEXT));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID);
    }
}
