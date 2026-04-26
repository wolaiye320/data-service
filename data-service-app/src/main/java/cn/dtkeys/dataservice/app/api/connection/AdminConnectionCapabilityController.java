package cn.dtkeys.dataservice.app.api.connection;

import cn.dtkeys.dataservice.app.api.AdminRequestContext;
import cn.dtkeys.dataservice.core.meta.service.ConnectionCapabilityService;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionCapabilityUpsertRequest;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionCapabilityResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/connections/{id}/capabilities")
public class AdminConnectionCapabilityController {

    private final ConnectionCapabilityService connectionCapabilityService;

    public AdminConnectionCapabilityController(ConnectionCapabilityService connectionCapabilityService) {
        this.connectionCapabilityService = connectionCapabilityService;
    }

    @GetMapping
    public ConnectionCapabilityResponse detail(@PathVariable("id") Long id) {
        return connectionCapabilityService.detail(id);
    }

    @PutMapping
    public ConnectionCapabilityResponse upsert(@PathVariable("id") Long id,
                                               @Valid @RequestBody ConnectionCapabilityUpsertRequest request,
                                               HttpServletRequest httpServletRequest) {
        return connectionCapabilityService.upsert(
                id,
                request,
                operatorContext(httpServletRequest),
                traceId(httpServletRequest)
        );
    }

    private OperatorContext operatorContext(HttpServletRequest request) {
        return AdminRequestContext.operatorContextOf(request.getAttribute(AdminRequestContext.ATTRIBUTE_OPERATOR_CONTEXT));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID);
    }
}
