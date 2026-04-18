package cn.dtkeys.dataservice.datasource;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.datasource.model.DSSourceCapability;
import cn.dtkeys.dataservice.audit.AuditLogService;
import cn.dtkeys.dataservice.datasource.DatasourceConnectionManager;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.repository.DSSourceCapabilityRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionManagementServiceTest {

    @Mock
    private DSConnectionRepository dsConnectionRepository;

    @Mock
    private DSCatalogRepository dsCatalogRepository;

    @Mock
    private DSSourceCapabilityRepository dsSourceCapabilityRepository;

    @Mock
    private DSSourceRepository dsSourceRepository;

    @Mock
    private DatasourceConnectionManager datasourceConnectionManager;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConnectionManagementService connectionManagementService;

    @AfterEach
    void tearDown() {
        OperatorContext.clear();
    }

    @Test
    void shouldRejectDisablingConnectionReferencedByPublishedService() {
        DSConnection existing = buildConnection(9L, "bank_conn", "ENABLED");
        when(dsConnectionRepository.findById(9L)).thenReturn(existing);
        when(dsSourceRepository.countPublishedReferencesByConnectionId(9L)).thenReturn(2L);

        assertThatThrownBy(() -> connectionManagementService.updateConnectionStatus(9L, "DISABLED"))
            .isInstanceOf(ServiceConfigInvalidException.class)
            .hasMessage("连接已被已发布服务引用，禁止停用");

        verify(dsConnectionRepository, never()).update(existing);
        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUpdateConnectionStatusAndWriteAudit() {
        OperatorContext.setOperator("admin-a");
        OperatorContext.setRole("ADMIN");
        DSConnection existing = buildConnection(8L, "bank_conn", "DISABLED");
        when(dsConnectionRepository.findById(8L)).thenReturn(existing);
        connectionManagementService.updateConnectionStatus(8L, "enabled");

        ArgumentCaptor<DSConnection> connectionCaptor = ArgumentCaptor.forClass(DSConnection.class);
        verify(dsConnectionRepository).update(connectionCaptor.capture());
        assertThat(connectionCaptor.getValue().getStatus()).isEqualTo("ENABLED");
        assertThat(connectionCaptor.getValue().getUpdatedBy()).isEqualTo("admin-a");
        verify(auditLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
            "ENABLE_CONNECTION".equals(event.eventType())
                && "bank_conn".equals(event.targetId())
                && "SUCCESS".equals(event.operationResult())
        ));
    }

    @Test
    void shouldMaskSecretsWhenListingConnections() {
        DSConnection connection = buildConnection(1L, "pg_meta", "ENABLED");
        connection.setConnectionConfigJson("{\"database\":\"data_service\"}");
        connection.setPasswordCiphertext("postgres");
        when(dsConnectionRepository.findAll()).thenReturn(java.util.List.of(connection));

        ConnectionManagementService.ConnectionView view = connectionManagementService.listConnections().get(0);

        assertThat(view.passwordCiphertext()).isEqualTo("po****es");
        assertThat(view.connectionConfigJson()).isEqualTo("***MASKED***");
        assertThat(view.connectionCode()).isEqualTo("pg_meta");
    }

    @Test
    void shouldExposeCapabilitiesInConnectionDetail() {
        DSConnection connection = buildConnection(1L, "pg_meta", "ENABLED");
        DSSourceCapability capability = new DSSourceCapability();
        capability.setConnectionId(1L);
        capability.setCapabilityCode("JOIN_INNER");
        capability.setCapabilityValue("true");
        when(dsConnectionRepository.findById(1L)).thenReturn(connection);
        when(dsCatalogRepository.findByConnectionId(1L)).thenReturn(java.util.List.of());
        when(dsSourceCapabilityRepository.findByConnectionId(1L)).thenReturn(java.util.List.of(capability));

        ConnectionManagementService.ConnectionDetail detail = connectionManagementService.getConnection(1L);

        assertThat(detail.capabilities()).hasSize(1);
        assertThat(detail.capabilities().get(0).getCapabilityCode()).isEqualTo("JOIN_INNER");
    }

    private DSConnection buildConnection(Long id, String code, String status) {
        DSConnection connection = new DSConnection();
        connection.setId(id);
        connection.setConnectionCode(code);
        connection.setConnectionName("Bank Connection");
        connection.setDbType("POSTGRESQL");
        connection.setHost("127.0.0.1");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("postgres");
        connection.setStatus(status);
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        return connection;
    }
}
