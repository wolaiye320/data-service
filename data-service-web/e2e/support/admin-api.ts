import type { APIRequestContext } from '@playwright/test'

type ApiResponse<T> = {
  success: boolean
  code: string
  message: string
  data: T
  meta: Record<string, unknown>
}

type ConnectionItem = {
  id?: number
  connectionCode: string
  connectionName: string
  dbType: string
  host: string
  port: number
  username: string
  status: string
}

type CatalogItem = {
  id?: number
  connectionId?: number
  catalogCode: string
  catalogName: string
  catalogType: string
  catalogValue: string
  status?: string
  remark?: string | null
}

type ConnectionDetail = {
  connection: ConnectionItem
  catalogs: CatalogItem[]
}

type ServiceDefinition = {
  id?: number
  serviceCode: string
  serviceName: string
  serviceType: string
  status: string
}

type ServiceDefinitionDetail = {
  definition: ServiceDefinition & {
    sqlType?: string
    executionMode?: string
    planStatus?: string
    version?: number | null
    currentSqlVersion?: number | null
  }
  sources: Array<Record<string, unknown>>
  params: Array<Record<string, unknown>>
  fields: Array<Record<string, unknown>>
}

const ADMIN_HEADERS = {
  'X-Operator': 'web-admin',
  'X-Operator-Role': 'ADMIN',
}

export function uniqueCode(prefix: string) {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 10_000)}`
}

async function readJson<T>(response: Awaited<ReturnType<APIRequestContext['fetch']>>, path: string) {
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok() || !payload.success) {
    throw new Error(`${path} 失败: ${payload.message}`)
  }
  return payload
}

async function get<T>(request: APIRequestContext, path: string) {
  const response = await request.get(path, {
    headers: ADMIN_HEADERS,
  })
  return readJson<T>(response, path)
}

async function post<T>(request: APIRequestContext, path: string, data?: unknown) {
  const response = await request.post(path, {
    headers: {
      ...ADMIN_HEADERS,
      'Content-Type': 'application/json',
    },
    data,
  })
  return readJson<T>(response, path)
}

async function put<T>(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.put(path, {
    headers: {
      ...ADMIN_HEADERS,
      'Content-Type': 'application/json',
    },
    data,
  })
  return readJson<T>(response, path)
}

export async function createPostgresConnection(
  request: APIRequestContext,
  options?: {
    connectionCode?: string
    connectionName?: string
    port?: number
    password?: string
    database?: string
    host?: string
  },
) {
  const connectionCode = options?.connectionCode ?? uniqueCode('pw_pg_conn')
  const connectionName = options?.connectionName ?? `Playwright PostgreSQL ${connectionCode}`
  const payload = {
    connectionCode,
    connectionName,
    dbType: 'POSTGRESQL',
    host: options?.host ?? '127.0.0.1',
    port: options?.port ?? 5432,
    username: 'postgres',
    passwordCiphertext: options?.password ?? 'postgres',
    status: 'ENABLED',
    connectionConfigJson: JSON.stringify({
      database: options?.database ?? 'data_service',
    }),
    catalogs: [
      {
        catalogCode: `${connectionCode}_schema`,
        catalogName: 'public',
        catalogType: 'SCHEMA',
        catalogValue: 'public',
        status: 'ENABLED',
      },
    ],
  }
  const response = await post<ConnectionDetail>(request, '/api/admin/connections', payload)
  return response.data
}

export async function createSimpleQueryDraft(
  request: APIRequestContext,
  options: {
    serviceCode?: string
    serviceName?: string
    connectionId: number
    catalogId: number
    sqlTemplate?: string
    params?: Array<Record<string, unknown>>
    fields?: Array<Record<string, unknown>>
    maxBatchSize?: number
    maxResultRows?: number
    queryTimeoutSeconds?: number
  },
) {
  const serviceCode = options.serviceCode ?? uniqueCode('pw_query_service')
  const response = await post<ServiceDefinitionDetail>(request, '/api/admin/service-definitions', {
    serviceCode,
    serviceName: options.serviceName ?? `Playwright 服务 ${serviceCode}`,
    serviceType: 'SIMPLE_QUERY',
    status: 'DRAFT',
    sqlTemplate:
      options.sqlTemplate ??
      'select customer_id as customerId, order_amount as orderAmount, active as active from customer_order where customer_id = :customerId and active = :active',
    sqlType: 'SIMPLE_SQL',
    executionMode: 'REMOTE_ONLY',
    planStatus: 'UNPLANNED',
    currentSqlVersion: 0,
    version: 0,
    maxBatchSize: options.maxBatchSize ?? 20,
    maxResultRows: options.maxResultRows ?? 200,
    queryTimeoutSeconds: options.queryTimeoutSeconds ?? 20,
    federatedQueryTimeoutSeconds: 40,
    remark: 'Playwright 自动化样本',
    sources: [
      {
        connectionId: options.connectionId,
        catalogId: options.catalogId,
        sourceAlias: 'customer',
        sourceType: 'TABLE',
        sourceValue: 'customer_order',
        joinKey: '',
        configJson: '',
        status: 'ENABLED',
        remark: '',
      },
    ],
    params:
      options.params ??
      [
        {
          paramName: 'customerId',
          displayName: '客户号',
          paramType: 'LONG',
          sqlPlaceholder: 'customerId',
          required: true,
          defaultValue: '',
          sortOrder: 1,
          remark: '',
        },
        {
          paramName: 'active',
          displayName: '激活状态',
          paramType: 'BOOLEAN',
          sqlPlaceholder: 'active',
          required: true,
          defaultValue: '',
          sortOrder: 2,
          remark: '',
        },
      ],
    fields:
      options.fields ??
      [
        {
          sourceAlias: 'customer',
          sourceColumn: 'customer_id',
          fieldName: 'customerId',
          displayName: '客户号',
          fieldType: 'LONG',
          sortOrder: 1,
          primaryKey: true,
          joinKey: true,
          remark: '',
        },
        {
          sourceAlias: 'customer',
          sourceColumn: 'order_amount',
          fieldName: 'orderAmount',
          displayName: '订单金额',
          fieldType: 'DECIMAL',
          sortOrder: 2,
          primaryKey: false,
          joinKey: false,
          remark: '',
        },
        {
          sourceAlias: 'customer',
          sourceColumn: 'active',
          fieldName: 'active',
          displayName: '激活状态',
          fieldType: 'BOOLEAN',
          sortOrder: 3,
          primaryKey: false,
          joinKey: false,
          remark: '',
        },
      ],
  })
  return response.data
}

export async function publishService(request: APIRequestContext, serviceId: number) {
  const response = await post<ServiceDefinitionDetail>(request, `/api/admin/service-definitions/${serviceId}/publish`)
  return response.data
}

export async function createFederatedDraft(
  request: APIRequestContext,
  options: {
    serviceCode?: string
    serviceName?: string
    primaryConnectionId: number
    primaryCatalogId: number
    childConnectionId: number
    childCatalogId: number
  },
) {
  const serviceCode = options.serviceCode ?? uniqueCode('pw_fed_service')
  const response = await post<ServiceDefinitionDetail>(request, '/api/admin/service-definitions', {
    serviceCode,
    serviceName: options.serviceName ?? `Playwright 联邦服务 ${serviceCode}`,
    serviceType: 'FEDERATED_QUERY',
    status: 'DRAFT',
    sqlTemplate: '',
    sqlType: 'FEDERATED_SQL',
    executionMode: 'REMOTE_PLUS_LOCAL',
    planStatus: 'UNPLANNED',
    currentSqlVersion: 0,
    version: 0,
    maxBatchSize: 20,
    maxResultRows: 200,
    queryTimeoutSeconds: 20,
    federatedQueryTimeoutSeconds: 40,
    remark: 'Playwright 联邦样本',
    sources: [
      {
        connectionId: options.primaryConnectionId,
        catalogId: options.primaryCatalogId,
        sourceAlias: 'pg_customer',
        sourceType: 'TABLE',
        sourceValue: 'customer_base',
        joinKey: '',
        configJson: '',
        status: 'ENABLED',
        remark: '',
      },
      {
        connectionId: options.childConnectionId,
        catalogId: options.childCatalogId,
        sourceAlias: 'pg_order',
        sourceType: 'TABLE',
        sourceValue: 'customer_order_ext',
        joinKey: '',
        configJson: '',
        status: 'ENABLED',
        remark: '',
      },
    ],
    params: [
      {
        paramName: 'customerId',
        displayName: '客户号',
        paramType: 'LONG',
        sqlPlaceholder: 'customerId',
        required: true,
        defaultValue: '',
        sortOrder: 1,
        remark: '',
      },
    ],
    fields: [
      {
        sourceAlias: 'pg_customer',
        sourceColumn: 'customer_id',
        fieldName: 'customerId',
        displayName: '客户号',
        fieldType: 'LONG',
        sortOrder: 1,
        primaryKey: true,
        joinKey: true,
        remark: '',
      },
      {
        sourceAlias: 'pg_customer',
        sourceColumn: 'customer_name',
        fieldName: 'customerName',
        displayName: '客户名称',
        fieldType: 'STRING',
        sortOrder: 2,
        primaryKey: false,
        joinKey: false,
        remark: '',
      },
      {
        sourceAlias: 'pg_order',
        sourceColumn: 'order_amount',
        fieldName: 'orderAmount',
        displayName: '订单金额',
        fieldType: 'DECIMAL',
        sortOrder: 3,
        primaryKey: false,
        joinKey: false,
        remark: '',
      },
    ],
  })
  return response.data
}

export async function createPredefinedCrossSourceDraft(
  request: APIRequestContext,
  options: {
    serviceCode?: string
    serviceName?: string
    primaryConnectionId: number
    primaryCatalogId: number
    childConnectionId: number
    childCatalogId: number
  },
) {
  const serviceCode = options.serviceCode ?? uniqueCode('pw_cross_service')
  const response = await post<ServiceDefinitionDetail>(request, '/api/admin/service-definitions', {
    serviceCode,
    serviceName: options.serviceName ?? `Playwright 跨源服务 ${serviceCode}`,
    serviceType: 'SIMPLE_QUERY',
    status: 'DRAFT',
    sqlTemplate:
      'select customer_id as customer_base_customer_id, customer_name as customer_base_customer_name, active as customer_base_active from customer_base where active = :active and customer_id in (:customerIds) order by customer_id',
    sqlType: 'SIMPLE_SQL',
    executionMode: 'REMOTE_PLUS_LOCAL',
    planStatus: 'UNPLANNED',
    currentSqlVersion: 0,
    version: 0,
    maxBatchSize: 20,
    maxResultRows: 200,
    queryTimeoutSeconds: 20,
    federatedQueryTimeoutSeconds: 40,
    remark: 'Playwright 跨源联调样本',
    sources: [
      {
        connectionId: options.primaryConnectionId,
        catalogId: options.primaryCatalogId,
        sourceAlias: 'customer_base',
        sourceType: 'TABLE',
        sourceValue: 'customer_base',
        joinKey: '',
        configJson: '',
        status: 'ENABLED',
        remark: '',
      },
      {
        connectionId: options.childConnectionId,
        catalogId: options.childCatalogId,
        sourceAlias: 'customer_order_ext',
        sourceType: 'TABLE',
        sourceValue: 'customer_order_ext',
        joinKey: '',
        configJson: JSON.stringify({
          joinType: 'LEFT',
          lookupParam: 'customerIds',
          lookupSourceColumn: 'customer_id',
          parentJoinField: 'customerId',
          childJoinField: 'orderCustomerId',
          childSqlTemplate:
            'select customer_id as customer_order_ext_customer_id, order_amount as customer_order_ext_order_amount from customer_order_ext where customer_id in (:customerIds)',
        }),
        status: 'ENABLED',
        remark: '',
      },
    ],
    params: [
      {
        paramName: 'active',
        displayName: '激活状态',
        paramType: 'BOOLEAN',
        sqlPlaceholder: 'active',
        required: true,
        defaultValue: '',
        sortOrder: 1,
        remark: '',
      },
      {
        paramName: 'customerIds',
        displayName: '客户列表',
        paramType: 'LIST',
        sqlPlaceholder: 'customerIds',
        required: true,
        defaultValue: '',
        sortOrder: 2,
        remark: '',
      },
    ],
    fields: [
      {
        sourceAlias: 'customer_base',
        sourceColumn: 'customer_id',
        fieldName: 'customerId',
        displayName: '客户号',
        fieldType: 'LONG',
        sortOrder: 1,
        primaryKey: true,
        joinKey: true,
        remark: '',
      },
      {
        sourceAlias: 'customer_base',
        sourceColumn: 'customer_name',
        fieldName: 'customerName',
        displayName: '客户名称',
        fieldType: 'STRING',
        sortOrder: 2,
        primaryKey: false,
        joinKey: false,
        remark: '',
      },
      {
        sourceAlias: 'customer_base',
        sourceColumn: 'active',
        fieldName: 'active',
        displayName: '激活状态',
        fieldType: 'BOOLEAN',
        sortOrder: 3,
        primaryKey: false,
        joinKey: false,
        remark: '',
      },
      {
        sourceAlias: 'customer_order_ext',
        sourceColumn: 'customer_id',
        fieldName: 'orderCustomerId',
        displayName: '订单客户号',
        fieldType: 'LONG',
        sortOrder: 4,
        primaryKey: false,
        joinKey: true,
        remark: '',
      },
      {
        sourceAlias: 'customer_order_ext',
        sourceColumn: 'order_amount',
        fieldName: 'orderAmount',
        displayName: '订单金额',
        fieldType: 'DECIMAL',
        sortOrder: 5,
        primaryKey: false,
        joinKey: false,
        remark: '',
      },
    ],
  })
  return response.data
}

export async function saveFederatedSql(
  request: APIRequestContext,
  serviceId: number,
  federatedSqlText: string,
  sqlComment = 'Playwright 联邦 SQL 保存',
) {
  const response = await put<Record<string, unknown>>(
    request,
    `/api/admin/service-definitions/${serviceId}/federated-sql`,
    {
      federatedSqlText,
      sqlComment,
    },
  )
  return response.data
}

export async function listDefinitions(request: APIRequestContext) {
  const response = await get<ServiceDefinition[]>(request, '/api/admin/service-definitions')
  return response.data
}

export async function getDefinition(request: APIRequestContext, serviceId: number) {
  const response = await get<ServiceDefinitionDetail>(request, `/api/admin/service-definitions/${serviceId}`)
  return response.data
}
