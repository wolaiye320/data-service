import { execFileSync } from 'node:child_process'

import { expect, test, type APIRequestContext } from '@playwright/test'

function requireMysqlCredentials() {
  const username = process.env.MYSQL_USER
  const password = process.env.MYSQL_PASSWORD
  if (!username || !password) {
    throw new Error('缺少本地 MySQL 测试凭据 MYSQL_USER / MYSQL_PASSWORD')
  }
  return { username, password }
}

function ensurePostgresOrder(orderId: number, orderName: string) {
  execFileSync(
    'psql',
    [
      'postgresql://postgres:postgres@localhost:5432/data_service',
      '-c',
      `insert into public.orders (id, order_name) values (${orderId}, '${orderName}') on conflict (id) do update set order_name = excluded.order_name;`,
    ],
    { stdio: 'ignore' },
  )
}

async function createPostgresConnection(request: APIRequestContext, connectionCode: string, connectionName: string) {
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName,
      dbType: 'POSTGRESQL',
      host: '127.0.0.1',
      port: 5432,
      username: 'postgres',
      password: 'postgres',
      databaseName: 'data_service',
      remark: 'federated e2e postgres connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  const body = (await response.json()) as { id: number }
  return body.id
}

async function createMysqlConnection(request: APIRequestContext, connectionCode: string, connectionName: string) {
  const { username, password } = requireMysqlCredentials()
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName,
      dbType: 'MYSQL',
      host: '127.0.0.1',
      port: 3306,
      username,
      password,
      databaseName: 'mysql_federation1',
      remark: 'federated e2e mysql connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  const body = (await response.json()) as { id: number }
  return body.id
}

async function upsertConnectionCapabilities(request: APIRequestContext, connectionId: number) {
  const response = await request.put(`http://127.0.0.1:8081/api/admin/connections/${connectionId}/capabilities`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      capabilities: [
        { capabilityCode: 'FILTER_PUSHDOWN', capabilityValue: 'SUPPORTED' },
        { capabilityCode: 'PROJECT_PUSHDOWN', capabilityValue: 'SUPPORTED' },
        { capabilityCode: 'JOIN_REORDER', capabilityValue: 'SUPPORTED' },
      ],
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function createPublishedFederatedService(
  request: APIRequestContext,
  payload: {
    serviceCode: string
    serviceName: string
    defaultConnectionCode: string
    sqlText: string
    paramDefinitions: Array<{ paramName: string; paramType: string }>
  },
) {
  const createResponse = await request.post('http://127.0.0.1:8081/api/admin/services', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      ...payload,
      sqlType: 'FEDERATED_SQL',
      maxBatchSize: 20,
      maxResultRows: 100,
      queryTimeoutSeconds: 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'federated e2e service',
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createdService = (await createResponse.json()) as { id: number }

  const publishResponse = await request.post(`http://127.0.0.1:8081/api/admin/services/${createdService.id}/publish`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {},
  })
  expect(publishResponse.ok()).toBeTruthy()
}

test('同构联邦查询成功并返回联邦诊断摘要', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const leftConnectionCode = `E2E_FED_PG_LEFT_${suffix}`
  const rightConnectionCode = `E2E_FED_PG_RIGHT_${suffix}`
  const serviceCode = `svc_fed_pg_e2e_${suffix}`
  const traceId = `fed-pg-${suffix}`

  const leftConnectionId = await createPostgresConnection(request, leftConnectionCode, `E2E Fed PG Left ${suffix}`)
  const rightConnectionId = await createPostgresConnection(request, rightConnectionCode, `E2E Fed PG Right ${suffix}`)
  await upsertConnectionCapabilities(request, leftConnectionId)
  await upsertConnectionCapabilities(request, rightConnectionId)
  await createPublishedFederatedService(request, {
    serviceCode,
    serviceName: `E2E 同构联邦服务 ${suffix}`,
    defaultConnectionCode: leftConnectionCode,
    sqlText: `select o.id as order_id, oi.sku as item_sku from ${leftConnectionCode}@public.orders o join ${rightConnectionCode}@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1`,
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
  })

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        callerId: `e2e-fed-pg-${suffix}`,
        traceId,
      },
    },
  })
  expect(queryResponse.ok()).toBeTruthy()

  const body = await queryResponse.json()
  expect(body.status).toBe('SUCCESS')
  expect(body.serviceCode).toBe(serviceCode)
  expect(body.items).toHaveLength(1)
  expect(body.items[0].status).toBe('SUCCESS')
  expect(body.items[0].rows).toHaveLength(1)
  expect(body.items[0].rows[0]).toMatchObject({
    order_id: 1,
    item_sku: 'sku-a',
  })
  expect(body.items[0].meta.diagnosticSummary.stage).toBe('FEDERATED_EXECUTION')
  expect(body.items[0].meta.diagnosticSummary.taskCount).toBe(2)
  expect(body.items[0].meta.diagnosticSummary.taskSummaries).toHaveLength(2)
  expect(body.meta.traceId).toBe(traceId)
})

test('异构联邦查询成功并返回跨库结果', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const postgresConnectionCode = `E2E_FED_PG_${suffix}`
  const mysqlConnectionCode = `E2E_FED_MY_${suffix}`
  const serviceCode = `svc_fed_pg_mysql_e2e_${suffix}`
  const traceId = `fed-pg-mysql-${suffix}`

  ensurePostgresOrder(20001, 'order-mysql-join')
  const postgresConnectionId = await createPostgresConnection(request, postgresConnectionCode, `E2E Fed PG ${suffix}`)
  const mysqlConnectionId = await createMysqlConnection(request, mysqlConnectionCode, `E2E Fed MY ${suffix}`)
  await upsertConnectionCapabilities(request, postgresConnectionId)
  await upsertConnectionCapabilities(request, mysqlConnectionId)
  await createPublishedFederatedService(request, {
    serviceCode,
    serviceName: `E2E 异构联邦服务 ${suffix}`,
    defaultConnectionCode: postgresConnectionCode,
    sqlText: `select o.id as order_id, m.product_name as product_name from ${postgresConnectionCode}@public.orders o join ${mysqlConnectionCode}@mysql_federation1.fed_test_order m on o.id = m.order_id where m.order_id = /* orderId */20001`,
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
  })

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 20001 } }],
      requestContext: {
        callerId: `e2e-fed-pg-mysql-${suffix}`,
        traceId,
      },
    },
  })
  expect(queryResponse.ok()).toBeTruthy()

  const body = await queryResponse.json()
  expect(body.status).toBe('SUCCESS')
  expect(body.serviceCode).toBe(serviceCode)
  expect(body.items).toHaveLength(1)
  expect(body.items[0].status).toBe('SUCCESS')
  expect(body.items[0].rows).toHaveLength(1)
  expect(body.items[0].rows[0]).toMatchObject({
    order_id: 20001,
    product_name: 'Risk Scanner',
  })
  expect(body.items[0].meta.diagnosticSummary.stage).toBe('FEDERATED_EXECUTION')
  expect(body.items[0].meta.diagnosticSummary.taskCount).toBe(2)
  expect(body.items[0].meta.diagnosticSummary.taskSummaries).toHaveLength(2)
  expect(body.meta.traceId).toBe(traceId)
})
