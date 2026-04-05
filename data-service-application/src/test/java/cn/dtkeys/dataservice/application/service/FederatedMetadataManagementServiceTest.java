package cn.dtkeys.dataservice.application.service;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.domain.model.DSConnection;
import cn.dtkeys.dataservice.domain.model.DSDefinition;
import cn.dtkeys.dataservice.domain.model.DSSource;
import cn.dtkeys.dataservice.domain.model.DSSourceCapability;
import cn.dtkeys.dataservice.domain.model.DSSqlText;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogService;
import cn.dtkeys.dataservice.infrastructure.repository.DSCachePolicyRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSFieldRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSParamRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSourceRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSourceCapabilityRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSqlPlanRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSqlTextRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSqlValidateLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederatedMetadataManagementServiceTest {

    @Mock
    private DSDefinitionRepository dsDefinitionRepository;

    @Mock
    private DSConnectionRepository dsConnectionRepository;

    @Mock
    private DSSourceRepository dsSourceRepository;

    @Mock
    private DSSourceCapabilityRepository dsSourceCapabilityRepository;

    @Mock
    private DSSqlTextRepository dsSqlTextRepository;

    @Mock
    private DSSqlPlanRepository dsSqlPlanRepository;

    @Mock
    private DSSqlValidateLogRepository dsSqlValidateLogRepository;

    @Mock
    private DSCachePolicyRepository dsCachePolicyRepository;

    @Mock
    private DSCatalogRepository dsCatalogRepository;

    @Mock
    private DSParamRepository dsParamRepository;

    @Mock
    private DSFieldRepository dsFieldRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private cn.dtkeys.dataservice.federation.executor.FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor;

    private FederatedMetadataManagementService federatedMetadataManagementService;

    @BeforeEach
    void setUp() {
        federatedMetadataManagementService = new FederatedMetadataManagementService(
            dsDefinitionRepository,
            dsConnectionRepository,
            dsSourceRepository,
            dsSourceCapabilityRepository,
            dsSqlTextRepository,
            dsSqlPlanRepository,
            dsSqlValidateLogRepository,
            dsCachePolicyRepository,
            dsCatalogRepository,
            dsParamRepository,
            dsFieldRepository,
            auditLogService,
            new ObjectMapper(),
            federatedRuntimeQueryExecutor
        );
    }

    @AfterEach
    void tearDown() {
        OperatorContext.clear();
    }

    @Test
    void shouldRejectNonFederatedServiceWhenSavingFederatedSqlDraft() {
        DSDefinition definition = new DSDefinition();
        definition.setId(1L);
        definition.setServiceType("SIMPLE_QUERY");
        when(dsDefinitionRepository.findById(1L)).thenReturn(definition);

        assertThatThrownBy(() -> federatedMetadataManagementService.saveFederatedSqlDraft(1L, "select 1 from pg_demo", "test"))
            .isInstanceOf(ParamInvalidException.class)
            .hasMessage("仅联邦查询服务支持维护联邦 SQL");
    }

    @Test
    void shouldSaveFederatedSqlDraftAndPersistMetadata() {
        OperatorContext.setOperator("dev-a");
        OperatorContext.setRole("DEVELOPER");
        DSDefinition definition = new DSDefinition();
        definition.setId(2L);
        definition.setServiceCode("federated_customer");
        definition.setServiceType("FEDERATED_QUERY");
        definition.setCurrentSqlVersion(1);
        definition.setPlanStatus("UNPLANNED");
        when(dsDefinitionRepository.findById(2L)).thenReturn(definition);
        when(dsSourceRepository.findByServiceId(2L)).thenReturn(List.of(source("pg_customer"), source("mysql_order")));
        DSSqlText sqlText = new DSSqlText();
        sqlText.setServiceId(2L);
        sqlText.setVersion(2);
        sqlText.setSqlText("select id from pg_customer");
        when(dsSqlTextRepository.findLatestDraftByServiceId(2L)).thenReturn(sqlText);
        when(dsSqlValidateLogRepository.findByServiceIdAndVersion(2L, 2)).thenReturn(List.of());
        when(dsSqlPlanRepository.findByServiceIdAndVersion(2L, 2)).thenReturn(List.of());

        FederatedMetadataManagementService.FederatedServiceMetadata metadata =
            federatedMetadataManagementService.saveFederatedSqlDraft(
                2L,
                "select id from pg_customer join mysql_order on pg_customer.id = mysql_order.customer_id where id = :id",
                "first draft"
            );

        ArgumentCaptor<DSDefinition> definitionCaptor = ArgumentCaptor.forClass(DSDefinition.class);
        verify(dsDefinitionRepository).update(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getSqlType()).isEqualTo("FEDERATED_SQL");
        assertThat(definitionCaptor.getValue().getExecutionMode()).isEqualTo("REMOTE_PLUS_LOCAL");
        assertThat(definitionCaptor.getValue().getPlanStatus()).isEqualTo("PLANNED");
        assertThat(metadata.definition().getServiceCode()).isEqualTo("federated_customer");
    }

    @Test
    void shouldRejectUnsupportedDialectExpressionWhenSavingFederatedSqlDraft() {
        DSDefinition definition = new DSDefinition();
        definition.setId(4L);
        definition.setServiceType("FEDERATED_QUERY");
        when(dsDefinitionRepository.findById(4L)).thenReturn(definition);
        when(dsSourceRepository.findByServiceId(4L)).thenReturn(List.of(source("oracle_customer")));

        assertThatThrownBy(() -> federatedMetadataManagementService.saveFederatedSqlDraft(
            4L,
            "select custom_func(name) from oracle_customer",
            "unsupported"))
            .isInstanceOf(ServiceConfigInvalidException.class)
            .hasMessageContaining("联邦 SQL 规划失败");
    }

    @Test
    void shouldRejectFederatedSqlWhenRecognizedSourcesNotConfigured() {
        DSDefinition definition = new DSDefinition();
        definition.setId(5L);
        definition.setServiceType("FEDERATED_QUERY");
        when(dsDefinitionRepository.findById(5L)).thenReturn(definition);
        when(dsSourceRepository.findByServiceId(5L)).thenReturn(List.of(source("pg_customer")));

        assertThatThrownBy(() -> federatedMetadataManagementService.saveFederatedSqlDraft(
            5L,
            "select pg_customer.id, mysql_order.customer_id from pg_customer join mysql_order on pg_customer.id = mysql_order.customer_id",
            "mismatch"))
            .isInstanceOf(ServiceConfigInvalidException.class)
            .hasMessageContaining("联邦 SQL 校验失败")
            .hasMessageContaining("未显式配置的来源: mysql_order");
    }

    @Test
    void shouldReplaceCapabilitiesForConnection() {
        OperatorContext.setOperator("admin-a");
        OperatorContext.setRole("ADMIN");
        DSConnection connection = new DSConnection();
        connection.setId(3L);
        connection.setConnectionCode("oracle_conn");
        connection.setDbType("ORACLE");
        when(dsConnectionRepository.findById(3L)).thenReturn(connection);
        when(dsSourceCapabilityRepository.findByConnectionId(3L)).thenReturn(List.of());

        DSSourceCapability capability = new DSSourceCapability();
        capability.setCapabilityCode("JOIN_INNER");
        capability.setCapabilityValue("false");

        federatedMetadataManagementService.replaceCapabilities(3L, List.of(capability));

        ArgumentCaptor<DSSourceCapability> capabilityCaptor = ArgumentCaptor.forClass(DSSourceCapability.class);
        verify(dsSourceCapabilityRepository).insert(capabilityCaptor.capture());
        assertThat(capabilityCaptor.getValue().getConnectionId()).isEqualTo(3L);
        assertThat(capabilityCaptor.getValue().getDbType()).isEqualTo("ORACLE");
        assertThat(capabilityCaptor.getValue().getScope()).isEqualTo("GLOBAL");
    }

    private DSSource source(String alias) {
        DSSource source = new DSSource();
        source.setSourceAlias(alias);
        source.setSourceValue(alias);
        return source;
    }
}
