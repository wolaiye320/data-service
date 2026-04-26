package cn.dtkeys.dataservice.app.api.service;

import cn.dtkeys.dataservice.app.api.AdminRequestContext;
import cn.dtkeys.dataservice.core.meta.service.ServiceDefinitionService;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceCreateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServicePublishRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServicePreviewRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceStatusUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.response.ServiceDetailResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ServicePreviewResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/services")
public class AdminServiceDefinitionController {

    private final ServiceDefinitionService serviceDefinitionService;

    public AdminServiceDefinitionController(ServiceDefinitionService serviceDefinitionService) {
        this.serviceDefinitionService = serviceDefinitionService;
    }

    @PostMapping
    public ResponseEntity<ServiceDetailResponse> create(@Valid @RequestBody ServiceCreateRequest request,
                                                        HttpServletRequest httpServletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceDefinitionService.create(request, operatorContext(httpServletRequest), traceId(httpServletRequest)));
    }

    @PutMapping("/{id}")
    public ServiceDetailResponse update(@PathVariable("id") Long id,
                                        @Valid @RequestBody ServiceUpdateRequest request,
                                        HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.update(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @PutMapping("/{id}/status")
    public ServiceDetailResponse updateStatus(@PathVariable("id") Long id,
                                              @Valid @RequestBody ServiceStatusUpdateRequest request,
                                              HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.disable(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @PostMapping("/{id}/publish")
    public ServiceDetailResponse publish(@PathVariable("id") Long id,
                                         @Valid @RequestBody ServicePublishRequest request,
                                         HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.publish(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @PostMapping("/{id}/preview")
    public ServicePreviewResponse preview(@PathVariable("id") Long id,
                                          @Valid @RequestBody ServicePreviewRequest request,
                                          HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.preview(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @GetMapping("/{id}")
    public ServiceDetailResponse detail(@PathVariable("id") Long id) {
        return serviceDefinitionService.detail(id);
    }

    @GetMapping
    public List<ServiceDetailResponse> list(@RequestParam(value = "status", required = false) String status) {
        return serviceDefinitionService.list(status);
    }

    private OperatorContext operatorContext(HttpServletRequest request) {
        return AdminRequestContext.operatorContextOf(request.getAttribute(AdminRequestContext.ATTRIBUTE_OPERATOR_CONTEXT));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID);
    }
}
