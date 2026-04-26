package cn.dtkeys.dataservice.app.api.connection;

import cn.dtkeys.dataservice.app.api.AdminRequestContext;
import cn.dtkeys.dataservice.core.meta.service.ConnectionService;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionCreateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionStatusUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionDetailResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionTestResponse;
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
@RequestMapping("/api/admin/connections")
public class AdminConnectionController {

    private final ConnectionService connectionService;

    public AdminConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping
    public ResponseEntity<ConnectionDetailResponse> create(@Valid @RequestBody ConnectionCreateRequest request,
                                                           HttpServletRequest httpServletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(connectionService.create(request, operatorContext(httpServletRequest), traceId(httpServletRequest)));
    }

    @PutMapping("/{id}")
    public ConnectionDetailResponse update(@PathVariable("id") Long id,
                                           @Valid @RequestBody ConnectionUpdateRequest request,
                                           HttpServletRequest httpServletRequest) {
        return connectionService.update(requestedId(id), request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @PutMapping("/{id}/status")
    public ConnectionDetailResponse updateStatus(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ConnectionStatusUpdateRequest request,
                                                 HttpServletRequest httpServletRequest) {
        return connectionService.updateStatus(requestedId(id), request, operatorContext(httpServletRequest), traceId(httpServletRequest));
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResponse test(@PathVariable("id") Long id,
                                       HttpServletRequest httpServletRequest) {
        return connectionService.testExisting(
                requestedId(id),
                operatorContext(httpServletRequest),
                traceId(httpServletRequest)
        );
    }

    @GetMapping("/{id}")
    public ConnectionDetailResponse detail(@PathVariable("id") Long id) {
        return connectionService.get(requestedId(id));
    }

    @GetMapping
    public List<ConnectionDetailResponse> list(@RequestParam(value = "status", required = false) String status) {
        return connectionService.list(status);
    }

    private Long requestedId(Long id) {
        return id;
    }

    private OperatorContext operatorContext(HttpServletRequest request) {
        return AdminRequestContext.operatorContextOf(request.getAttribute(AdminRequestContext.ATTRIBUTE_OPERATOR_CONTEXT));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID);
    }
}
