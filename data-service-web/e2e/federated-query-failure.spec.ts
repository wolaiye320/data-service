import { expect, test, type APIRequestContext } from '@playwright/test'

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
      remark: 'federated failure e2e postgres connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  const body = (await response.json()) as { id: number }
  return body.id
}

async function upsertConnectionCapabilities(
  request: APIRequestContext,
  connectionId: number,
  capabilityCodes: string[],
) {
  const response = await request.put(`http://127.0.0.1:8081/api/admin/connections/${connectionId}/capabilities`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      capabilities: capabilityCodes.map((capabilityCode) => ({
        capabilityCode,
        capabilityValue: 'SUPPORTED',
      })),
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function createFederatedDraftService(
  request: APIRequestContext,
  payload: {
    serviceCode: string
    serviceName: string
    defaultConnectionCode: string
    sqlText: string
    paramDefinitions?: Array<{ paramName: string; paramType: string }>
  },
) {
  const response = await request.post('http://127.0.0.1:8081/api/admin/services', {
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
      remark: 'federated failure e2e service',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { id: number }
}

test('联邦发布缺少能力配置时返回明确失败原因', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const leftConnectionCode = `E2E_FED_FAIL_LEFT_${suffix}`
  const rightConnectionCode = `E2E_FED_FAIL_RIGHT_${suffix}`
  const serviceCode = `svc_fed_fail_cap_${suffix}`

  const leftConnectionId = await createPostgresConnection(request, leftConnectionCode, `E2E Fed Fail Left ${suffix}`)
  const rightConnectionId = await createPostgresConnection(request, rightConnectionCode, `E2E Fed Fail Right ${suffix}`)
  await upsertConnectionCapabilities(request, leftConnectionId, ['FILTER_PUSHDOWN'])
  await upsertConnectionCapabilities(request, rightConnectionId, ['FILTER_PUSHDOWN'])

  const createdService = await createFederatedDraftService(request, {
    serviceCode,
    serviceName: `E2E 联邦能力不足服务 ${suffix}`,
    defaultConnectionCode: leftConnectionCode,
    sqlText: `select o.id as order_id, oi.sku as item_sku from ${leftConnectionCode}@public.orders o join ${rightConnectionCode}@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1`,
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
  })

  const publishResponse = await request.post(`http://127.0.0.1:8081/api/admin/services/${createdService.id}/publish`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {},
  })
  expect(publishResponse.status()).toBe(400)

  const body = await publishResponse.json()
  expect(body.errorCode).toBe('INVALID_ARGUMENT')
  expect(body.message).toContain('联邦能力校验失败')
  expect(body.message).toContain('PROJECT_PUSHDOWN')
  expect(body.message).toContain(leftConnectionCode)
  expect(body.traceId).toBeTruthy()
})

test('联邦保存遇到不兼容表达式时返回明确失败原因', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const connectionCode = `E2E_FED_EXPR_${suffix}`
  await createPostgresConnection(request, connectionCode, `E2E Fed Expr ${suffix}`)

  const createResponse = await request.post('http://127.0.0.1:8081/api/admin/services', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      serviceCode: `svc_fed_expr_fail_${suffix}`,
      serviceName: `E2E 联邦表达式失败服务 ${suffix}`,
      sqlType: 'FEDERATED_SQL',
      defaultConnectionCode: connectionCode,
      sqlText: `select cast(o.id as json) as payload_json from ${connectionCode}@public.orders o`,
      maxBatchSize: 20,
      maxResultRows: 100,
      queryTimeoutSeconds: 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'federated expression failure e2e service',
    },
  })
  expect(createResponse.status()).toBe(400)

  const body = await createResponse.json()
  expect(body.errorCode).toBe('INVALID_ARGUMENT')
  expect(body.message).toContain('复杂表达式不受支持')
  expect(body.message).toContain('CAST')
  expect(body.traceId).toBeTruthy()
})
