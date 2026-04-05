package cn.dtkeys.dataservice.application.service;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.domain.model.DSDefinition;
import cn.dtkeys.dataservice.domain.model.DSField;
import cn.dtkeys.dataservice.domain.model.DSParam;
import cn.dtkeys.dataservice.domain.model.DSServiceVersion;
import cn.dtkeys.dataservice.domain.model.DSSource;
import cn.dtkeys.dataservice.domain.model.DSSqlText;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogService;
import cn.dtkeys.dataservice.infrastructure.executor.SqlReadOnlyValidator;
import cn.dtkeys.dataservice.infrastructure.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSFieldRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSParamRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSServiceVersionRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSourceRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSqlPlanRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSqlTextRepository;
import cn.dtkeys.dataservice.infrastructure.repository.DSSqlValidateLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceDefinitionManagementServiceTest {

    @Mock
    private DSDefinitionRepository dsDefinitionRepository;

    @Mock
    private DSSourceRepository dsSourceRepository;

    @Mock
    private DSParamRepository dsParamRepository;

    @Mock
    private DSFieldRepository dsFieldRepository;

    @Mock
    private DSServiceVersionRepository dsServiceVersionRepository;

    @Mock
    private DSSqlTextRepository dsSqlTextRepository;

    @Mock
    private DSSqlPlanRepository dsSqlPlanRepository;

    @Mock
    private DSSqlValidateLogRepository dsSqlValidateLogRepository;

    @Mock
    private SqlReadOnlyValidator sqlReadOnlyValidator;

    @Mock
    private AuditLogService auditLogService;

    private ServiceDefinitionManagementService serviceDefinitionManagementService;

    @BeforeEach
    void setUp() {
        serviceDefinitionManagementService = new ServiceDefinitionManagementService(
            dsDefinitionRepository,
            dsSourceRepository,
            dsParamRepository,
            dsFieldRepository,
            dsServiceVersionRepository,
            dsSqlTextRepository,
            dsSqlPlanRepository,
            dsSqlValidateLogRepository,
            sqlReadOnlyValidator,
            auditLogService,
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        OperatorContext.clear();
    }

    @Test
    void shouldRejectPublishWhenFieldsMissing() {
        DSDefinition definition = buildDefinition(6L, "customer_profile", "DRAFT", 0);
        when(dsDefinitionRepository.findById(6L)).thenReturn(definition);
        when(dsSourceRepository.findByServiceId(6L)).thenReturn(List.of(buildSource()));
        when(dsParamRepository.findByServiceId(6L)).thenReturn(List.of(buildParam()));
        when(dsFieldRepository.findByServiceId(6L)).thenReturn(List.of());

        assertThatThrownBy(() -> serviceDefinitionManagementService.publish(6L))
            .isInstanceOf(ServiceConfigInvalidException.class)
            .hasMessage("发布失败: 未配置返回字段");

        verify(dsServiceVersionRepository, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(dsDefinitionRepository, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPublishDraftAndPersistVersionSnapshot() {
        OperatorContext.setOperator("dev-a");
        OperatorContext.setRole("DEVELOPER");
        DSDefinition definition = buildDefinition(7L, "customer_profile", "DRAFT", 1);
        when(dsDefinitionRepository.findById(7L)).thenReturn(definition);
        when(dsSourceRepository.findByServiceId(7L)).thenReturn(List.of(buildSource()));
        when(dsParamRepository.findByServiceId(7L)).thenReturn(List.of(buildParam()));
        when(dsFieldRepository.findByServiceId(7L)).thenReturn(List.of(buildField()));

        serviceDefinitionManagementService.publish(7L);

        ArgumentCaptor<cn.dtkeys.dataservice.domain.model.DSServiceVersion> versionCaptor =
            ArgumentCaptor.forClass(cn.dtkeys.dataservice.domain.model.DSServiceVersion.class);
        verify(dsServiceVersionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersion()).isEqualTo(2);
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(versionCaptor.getValue().getCreatedBy()).isEqualTo("dev-a");

        ArgumentCaptor<DSDefinition> definitionCaptor = ArgumentCaptor.forClass(DSDefinition.class);
        verify(dsDefinitionRepository).update(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(definitionCaptor.getValue().getPlanStatus()).isEqualTo("PUBLISHED");
        assertThat(definitionCaptor.getValue().getVersion()).isEqualTo(2);
        assertThat(definitionCaptor.getValue().getCurrentSqlVersion()).isEqualTo(2);
        verify(auditLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
            "PUBLISH_SERVICE".equals(event.eventType())
                && "customer_profile".equals(event.targetId())
                && "SUCCESS".equals(event.operationResult())
        ));
    }

    @Test
    void shouldPublishFederatedSqlDraftAsCurrentVersionSnapshot() {
        OperatorContext.setOperator("dev-a");
        OperatorContext.setRole("DEVELOPER");
        DSDefinition definition = buildDefinition(8L, "federated_customer", "DRAFT", 0);
        definition.setServiceType("FEDERATED_QUERY");
        definition.setSqlType("FEDERATED_SQL");
        definition.setExecutionMode("REMOTE_PLUS_LOCAL");
        definition.setPlanStatus("PLANNED");
        DSSqlText sqlDraft = new DSSqlText();
        sqlDraft.setServiceId(8L);
        sqlDraft.setVersion(1);
        sqlDraft.setSqlText("select pg_customer.id, mysql_order.customer_id from pg_customer join mysql_order on pg_customer.id = mysql_order.customer_id");
        sqlDraft.setSqlComment("federated publish");
        when(dsDefinitionRepository.findById(8L)).thenReturn(definition);
        when(dsSourceRepository.findByServiceId(8L)).thenReturn(List.of(buildSource()));
        when(dsParamRepository.findByServiceId(8L)).thenReturn(List.of(buildParam()));
        when(dsFieldRepository.findByServiceId(8L)).thenReturn(List.of(buildField()));
        when(dsSqlTextRepository.findLatestDraftByServiceId(8L)).thenReturn(sqlDraft);
        when(dsSqlValidateLogRepository.findByServiceIdAndVersion(8L, 1)).thenReturn(List.of());
        when(dsSqlPlanRepository.findByServiceIdAndVersion(8L, 1)).thenReturn(List.of());
        when(dsSqlTextRepository.markPublished(8L, 1, "dev-a")).thenReturn(1);

        serviceDefinitionManagementService.publish(8L);

        verify(dsSqlTextRepository).clearCurrentVersion(8L, "dev-a");
        verify(dsSqlTextRepository).markPublished(8L, 1, "dev-a");

        ArgumentCaptor<DSServiceVersion> versionCaptor = ArgumentCaptor.forClass(DSServiceVersion.class);
        verify(dsServiceVersionRepository).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersion()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getSqlDefinitionJson()).contains("\"federatedSqlVersion\":1");
        assertThat(versionCaptor.getValue().getSqlDefinitionJson()).contains("\"federatedSqlStatus\":\"PUBLISHED\"");

        ArgumentCaptor<DSDefinition> definitionCaptor = ArgumentCaptor.forClass(DSDefinition.class);
        verify(dsDefinitionRepository).update(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getVersion()).isEqualTo(1);
        assertThat(definitionCaptor.getValue().getCurrentSqlVersion()).isEqualTo(1);
    }

    @Test
    void shouldRejectDisablingNonPublishedService() {
        DSDefinition definition = buildDefinition(5L, "customer_profile", "DRAFT", 0);
        when(dsDefinitionRepository.findById(5L)).thenReturn(definition);

        assertThatThrownBy(() -> serviceDefinitionManagementService.updateStatus(5L, "DISABLED"))
            .isInstanceOf(ParamInvalidException.class)
            .hasMessage("仅允许停用已发布服务");
    }

    private DSDefinition buildDefinition(Long id, String serviceCode, String status, int version) {
        DSDefinition definition = new DSDefinition();
        definition.setId(id);
        definition.setServiceCode(serviceCode);
        definition.setServiceName("客户画像");
        definition.setServiceType("SIMPLE_QUERY");
        definition.setStatus(status);
        definition.setSqlTemplate("select customer_id as customer_customer_id from customer_order where customer_id = :customerId");
        definition.setSqlType("SIMPLE_SQL");
        definition.setExecutionMode("REMOTE_ONLY");
        definition.setPlanStatus("UNPLANNED");
        definition.setVersion(version);
        definition.setCurrentSqlVersion(version);
        definition.setCreatedBy("tester");
        definition.setUpdatedBy("tester");
        return definition;
    }

    private DSSource buildSource() {
        DSSource source = new DSSource();
        source.setConnectionId(1L);
        source.setCatalogId(1L);
        source.setSourceAlias("customer");
        source.setSourceType("TABLE");
        source.setSourceValue("customer_order");
        source.setStatus("ENABLED");
        return source;
    }

    private DSParam buildParam() {
        DSParam param = new DSParam();
        param.setParamName("customerId");
        param.setDisplayName("客户号");
        param.setParamType("LONG");
        param.setSqlPlaceholder("customerId");
        param.setRequired(true);
        param.setSortOrder(1);
        return param;
    }

    private DSField buildField() {
        DSField field = new DSField();
        field.setSourceAlias("customer");
        field.setSourceColumn("customer_id");
        field.setFieldName("customerId");
        field.setDisplayName("客户号");
        field.setFieldType("LONG");
        field.setSortOrder(1);
        field.setPrimaryKey(true);
        field.setJoinKey(true);
        return field;
    }
}
