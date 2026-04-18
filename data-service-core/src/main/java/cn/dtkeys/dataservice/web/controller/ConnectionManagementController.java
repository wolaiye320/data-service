package cn.dtkeys.dataservice.web.controller;

import cn.dtkeys.dataservice.datasource.ConnectionManagementService;
import cn.dtkeys.dataservice.federation.FederatedMetadataManagementService;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.datasource.model.DSSourceCapability;
import cn.dtkeys.dataservice.security.PermissionCode;
import cn.dtkeys.dataservice.security.RequirePermission;
import cn.dtkeys.dataservice.web.dto.admin.connection.CatalogUpsertRequest;
import cn.dtkeys.dataservice.web.dto.admin.connection.ConnectionStatusUpdateRequest;
import cn.dtkeys.dataservice.web.dto.admin.connection.ConnectionUpsertRequest;
import cn.dtkeys.dataservice.web.dto.admin.connection.SourceCapabilityUpsertRequest;
import cn.dtkeys.dataservice.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/admin/connections")
@RequirePermission(PermissionCode.CONNECTION_MANAGE)
public class ConnectionManagementController {

    private final ConnectionManagementService connectionManagementService;
    private final FederatedMetadataManagementService federatedMetadataManagementService;

    public ConnectionManagementController(ConnectionManagementService connectionManagementService,
                                          FederatedMetadataManagementService federatedMetadataManagementService) {
        this.connectionManagementService = connectionManagementService;
        this.federatedMetadataManagementService = federatedMetadataManagementService;
    }

    @GetMapping
    public ApiResponse<List<ConnectionManagementService.ConnectionView>> list() {
        return ApiResponse.success(connectionManagementService.listConnections());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectionManagementService.ConnectionDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(connectionManagementService.getConnection(id));
    }

    @PostMapping
    public ApiResponse<ConnectionManagementService.ConnectionDetail> create(@Valid @RequestBody ConnectionUpsertRequest request) {
        return ApiResponse.success(connectionManagementService.createConnection(
            toConnection(request),
            toCatalogs(request.catalogs())
        ));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConnectionManagementService.ConnectionDetail> update(@PathVariable("id") Long id,
                                                                            @Valid @RequestBody ConnectionUpsertRequest request) {
        return ApiResponse.success(connectionManagementService.updateConnection(
            id,
            toConnection(request),
            toCatalogs(request.catalogs())
        ));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<ConnectionManagementService.ConnectionDetail> updateStatus(@PathVariable("id") Long id,
                                                                                  @Valid @RequestBody ConnectionStatusUpdateRequest request) {
        return ApiResponse.success(connectionManagementService.updateConnectionStatus(id, request.status()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Void> testConnection(@PathVariable("id") Long id) {
        connectionManagementService.testConnection(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/capabilities")
    public ApiResponse<List<DSSourceCapability>> listCapabilities(@PathVariable("id") Long id) {
        return ApiResponse.success(federatedMetadataManagementService.listCapabilities(id));
    }

    @PutMapping("/{id}/capabilities")
    public ApiResponse<List<DSSourceCapability>> replaceCapabilities(@PathVariable("id") Long id,
                                                                     @Valid @RequestBody List<@Valid SourceCapabilityUpsertRequest> requests) {
        return ApiResponse.success(federatedMetadataManagementService.replaceCapabilities(
            id,
            toCapabilities(requests)
        ));
    }

    private DSConnection toConnection(ConnectionUpsertRequest request) {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode(request.connectionCode());
        connection.setConnectionName(request.connectionName());
        connection.setDbType(request.dbType());
        connection.setHost(request.host());
        connection.setPort(request.port());
        connection.setUsername(request.username());
        connection.setPasswordCiphertext(request.passwordCiphertext());
        connection.setStatus(request.status());
        connection.setRemark(request.remark());
        connection.setConnectionConfigJson(request.connectionConfigJson());
        return connection;
    }

    private List<DSCatalog> toCatalogs(List<CatalogUpsertRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            DSCatalog catalog = new DSCatalog();
            catalog.setCatalogCode(request.catalogCode());
            catalog.setCatalogName(request.catalogName());
            catalog.setCatalogType(request.catalogType());
            catalog.setCatalogValue(request.catalogValue());
            catalog.setStatus(request.status());
            catalog.setRemark(request.remark());
            return catalog;
        }).toList();
    }

    private List<DSSourceCapability> toCapabilities(List<SourceCapabilityUpsertRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            DSSourceCapability capability = new DSSourceCapability();
            capability.setCapabilityCode(request.capabilityCode());
            capability.setCapabilityValue(request.capabilityValue());
            capability.setCapabilityDetailJson(request.capabilityDetailJson());
            capability.setScope(request.scope());
            capability.setScopeValue(request.scopeValue());
            capability.setEnabled(request.enabled());
            capability.setRemark(request.remark());
            return capability;
        }).toList();
    }
}
