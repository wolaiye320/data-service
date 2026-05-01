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

/**
 * 管理端维护数据服务定义、发布和预览入口。
 */
@Validated
@RestController
@RequestMapping("/api/admin/services")
public class AdminServiceDefinitionController {

    private final ServiceDefinitionService serviceDefinitionService;

    public AdminServiceDefinitionController(ServiceDefinitionService serviceDefinitionService) {
        this.serviceDefinitionService = serviceDefinitionService;
    }

    /**
     * 新增服务草稿。
     */
    @PostMapping
    public ResponseEntity<ServiceDetailResponse> create(@Valid @RequestBody ServiceCreateRequest request,
                                                        HttpServletRequest httpServletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceDefinitionService.create(request, operatorContext(httpServletRequest), traceId(httpServletRequest)));
    }

    /**
     * 编辑服务草稿。
     */
    @PutMapping("/{id}")
    public ServiceDetailResponse update(@PathVariable("id") Long id,
                                        @Valid @RequestBody ServiceUpdateRequest request,
                                        HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.update(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    /**
     * 停用已发布服务。
     */
    @PutMapping("/{id}/status")
    public ServiceDetailResponse updateStatus(@PathVariable("id") Long id,
                                              @Valid @RequestBody ServiceStatusUpdateRequest request,
                                              HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.disable(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    /**
     * 发布当前草稿版本。
     */
    @PostMapping("/{id}/publish")
    public ServiceDetailResponse publish(@PathVariable("id") Long id,
                                         @Valid @RequestBody ServicePublishRequest request,
                                         HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.publish(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    /**
     * 预览当前草稿版本的执行结果。
     */
    @PostMapping("/{id}/preview")
    public ServicePreviewResponse preview(@PathVariable("id") Long id,
                                          @Valid @RequestBody ServicePreviewRequest request,
                                          HttpServletRequest httpServletRequest) {
        return serviceDefinitionService.preview(id, request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    /**
     * 查询服务详情。
     */
    @GetMapping("/{id}")
    public ServiceDetailResponse detail(@PathVariable("id") Long id) {
        return serviceDefinitionService.detail(id);
    }

    /**
     * 按状态过滤服务列表；不传状态时返回全部。
     */
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
