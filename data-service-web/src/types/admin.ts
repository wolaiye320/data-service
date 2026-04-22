export interface CatalogItem {
  id?: number
  connectionId?: number
  catalogType: string
  catalogValue: string
}

export interface ConnectionItem {
  id?: number
  connectionCode: string
  connectionName: string
  dbType: string
  host: string
  port: number
  username: string
  passwordCiphertext?: string
  status: string
  remark?: string | null
  connectionConfigJson?: string | null
  deleted?: boolean
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
}

export interface ConnectionDetail {
  connection: ConnectionItem
  catalogs: CatalogItem[]
}

export interface SourceItem {
  id?: number
  serviceId?: number
  connectionId?: number | null
  catalogId?: number | null
  sourceAlias: string
  sourceType: string
  sourceValue: string
  joinKey?: string | null
  configJson?: string | null
}

export interface ParamItem {
  id?: number
  serviceId?: number
  paramName: string
  displayName: string
  paramType: string
  sqlPlaceholder: string
  required?: boolean
  defaultValue?: string | null
  sortOrder: number
}

export interface FieldItem {
  id?: number
  serviceId?: number
  sourceAlias?: string | null
  sourceColumn: string
  fieldName: string
  displayName: string
  fieldType: string
  sortOrder: number
  primaryKey?: boolean
  joinKey?: boolean
}

export interface ServiceDefinition {
  id?: number
  serviceCode: string
  serviceName: string
  serviceType: string
  status: string
  sqlTemplate?: string | null
  sqlType: string
  executionMode: string
  planStatus: string
  currentSqlVersion?: number | null
  version?: number | null
  maxBatchSize?: number | null
  maxResultRows?: number | null
  queryTimeoutSeconds?: number | null
  federatedQueryTimeoutSeconds?: number | null
  remark?: string | null
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
  publishedAt?: string | null
  publishedBy?: string | null
}

export interface ServiceDefinitionDetail {
  definition: ServiceDefinition
  sources: SourceItem[]
  params: ParamItem[]
  fields: FieldItem[]
}

export interface ServiceVersionItem {
  id: number
  serviceId: number
  version: number
  status: string
  serviceDefinitionJson: string
  sourceDefinitionJson: string
  paramDefinitionJson: string
  fieldDefinitionJson: string
  sqlDefinitionJson: string
  createdBy: string
  createdAt: string
}

export interface SourceCapabilityItem {
  id?: number
  connectionId: number
  dbType: string
  capabilityCode: string
  capabilityValue: string
  capabilityDetailJson?: string | null
  scope?: string | null
  scopeValue?: string | null
  enabled?: boolean | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface FederatedSqlDraftItem {
  id?: number
  serviceId?: number
  version?: number | null
  sqlText: string
  sqlComment?: string | null
  status?: string | null
  current?: boolean | null
  createdBy?: string | null
  createdAt?: string | null
  updatedBy?: string | null
  updatedAt?: string | null
}

export interface SqlValidateLogItem {
  id?: number
  serviceId?: number
  version?: number | null
  validateStage: string
  result: string
  message: string
  detailJson?: string | null
  traceId?: string | null
  createdAt?: string | null
  createdBy?: string | null
}

export interface SqlPlanItem {
  id?: number
  serviceId?: number
  version?: number | null
  planStage: string
  planFormat?: string | null
  planContent?: string | null
  stageGraphJson?: string | null
  pushdownSummary?: string | null
  fallbackReason?: string | null
  costSummary?: string | null
  localExecutionSummary?: string | null
  datasourceScope?: string | null
  createdAt?: string | null
  createdBy?: string | null
}

export interface FederatedMetadata {
  definition: ServiceDefinition
  draftSql?: FederatedSqlDraftItem | null
  validateLogs: SqlValidateLogItem[]
  plans: SqlPlanItem[]
}

export interface SqlAutoDetectSource {
  connectionId?: number | null
  catalogId?: number | null
  sourceAlias: string
  sourceType: string
  sourceValue: string
  sourceName: string
  sqlAlias: string
  connectionResolved: boolean
  catalogResolved: boolean
}

export interface SqlAutoDetectParam {
  paramName: string
  displayName: string
  paramType: string
  sqlPlaceholder: string
  required: boolean
  sortOrder: number
}

export interface SqlAutoDetectField {
  sourceAlias?: string | null
  sourceColumn: string
  fieldName: string
  displayName: string
  fieldType: string
  sortOrder: number
  primaryKey: boolean
  joinKey: boolean
  selectedExpression: string
}

export interface SqlAutoDetectResponse {
  sqlType: string
  serviceType: string
  executionMode: string
  planStatus: string
  sources: SqlAutoDetectSource[]
  params: SqlAutoDetectParam[]
  fields: SqlAutoDetectField[]
}

export interface AuditLogItem {
  id: number
  serviceId?: number | null
  connectionId?: number | null
  eventType: string
  targetType?: string | null
  targetId?: string | null
  operator: string
  operatorRole: string
  operationResult: string
  traceId?: string | null
  requestIp?: string | null
  changeSummary?: string | null
  detailJson?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface AuditLogPage {
  total: number
  records: AuditLogItem[]
}

export interface FederatedPreviewRequest {
  federatedSqlText: string
  params?: Record<string, unknown>
}

export interface ConnectionUpsertPayload {
  connectionCode: string
  connectionName: string
  dbType: string
  host: string
  port: number
  username: string
  passwordCiphertext?: string
  status?: string
  remark?: string
  connectionConfigJson?: string
  catalogs: CatalogItem[]
}

export interface ServiceDefinitionUpsertPayload {
  serviceCode: string
  serviceName: string
  serviceType: string
  status?: string
  sqlTemplate?: string
  sqlType: string
  executionMode?: string
  planStatus?: string
  currentSqlVersion?: number
  version?: number
  maxBatchSize?: number
  maxResultRows?: number
  queryTimeoutSeconds?: number
  federatedQueryTimeoutSeconds?: number
  remark?: string
  sources: SourceItem[]
  params: ParamItem[]
  fields: FieldItem[]
}

export interface QueryDebugContext {
  traceId?: string
  operator?: string
}

export interface QueryDebugRequest {
  serviceCode: string
  params?: Record<string, unknown>
  batchParams?: Array<Record<string, unknown>>
  context?: QueryDebugContext
}
