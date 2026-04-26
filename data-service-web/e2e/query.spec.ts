import { expect, test, type APIRequestContext } from '@playwright/test'

async function createConnection(request: APIRequestContext, suffix: string) {
  const connectionCode = `E2E_QUERY_PG_${suffix}`
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName: `E2E Query PG ${suffix}`,
      dbType: 'POSTGRESQL',
      host: '127.0.0.1',
      port: 5432,
      username: 'postgres',
      password: 'postgres',
      databaseName: 'data_service',
      remark: 'query e2e connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  return { connectionCode }
}

async function createSimpleService(
  request: APIRequestContext,
  suffix: string,
  options?: {
    serviceCodePrefix?: string
    maxBatchSize?: number
    maxResultRows?: number
    queryTimeoutSeconds?: number
  },
) {
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `${options?.serviceCodePrefix ?? 'svc_query_e2e'}_${suffix}`
  const createResponse = await request.post('http://127.0.0.1:8081/api/admin/services', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      serviceCode,
      serviceName: `E2E 统一查询服务 ${suffix}`,
      sqlType: 'SIMPLE_SQL',
      defaultConnectionCode: connectionCode,
      sqlText: 'select id, order_name from public.orders where id = /* orderId */1',
      paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
      maxBatchSize: options?.maxBatchSize ?? 100,
      maxResultRows: options?.maxResultRows ?? 1000,
      queryTimeoutSeconds: options?.queryTimeoutSeconds ?? 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'query e2e service',
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createdService = (await createResponse.json()) as { id: number }

  return {
    serviceId: createdService.id,
    serviceCode,
    connectionCode,
  }
}

async function publishService(request: APIRequestContext, serviceId: number) {
  const publishResponse = await request.post(`http://127.0.0.1:8081/api/admin/services/${serviceId}/publish`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {},
  })
  expect(publishResponse.ok()).toBeTruthy()
}

async function createPublishedSimpleService(
  request: APIRequestContext,
  suffix: string,
  options?: {
    serviceCodePrefix?: string
    maxBatchSize?: number
    maxResultRows?: number
    queryTimeoutSeconds?: number
  },
) {
  const service = await createSimpleService(request, suffix, options)
  await publishService(request, service.serviceId)
  return service
}

test('统一查询单次成功并返回响应元信息', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceCode, connectionCode } = await createPublishedSimpleService(request, suffix)
  const traceId = `query-single-${suffix}`

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        callerId: `e2e-caller-single-${suffix}`,
        contextKeys: ['callerId'],
      },
    },
  })
  expect(queryResponse.ok()).toBeTruthy()

  const body = await queryResponse.json()
  expect(body.serviceCode).toBe(serviceCode)
  expect(body.serviceVersion).toBe(1)
  expect(body.status).toBe('SUCCESS')
  expect(body.requestContext.callerId).toBe(`e2e-caller-single-${suffix}`)
  expect(body.requestContext.traceId).toBe(traceId)
  expect(body.requestContext.contextKeys).toEqual(['callerId'])
  expect(body.requestContext.contextSummaryKeys).toEqual(expect.arrayContaining(['callerId', 'traceId']))
  expect(body.meta.batch).toBe(false)
  expect(body.meta.requestedBatchSize).toBe(1)
  expect(body.meta.successCount).toBe(1)
  expect(body.meta.failureCount).toBe(0)
  expect(body.meta.cacheHit).toBe(false)
  expect(body.meta.traceId).toBe(traceId)
  expect(body.meta.elapsedMs).toBeGreaterThanOrEqual(0)
  expect(body.items).toHaveLength(1)
  expect(body.items[0].status).toBe('SUCCESS')
  expect(body.items[0].rows).toHaveLength(1)
  expect(body.items[0].rows[0]).toMatchObject({
    id: 1,
    order_name: 'order-a',
  })
  expect(body.items[0].meta.cacheHit).toBe(false)
  expect(body.items[0].meta.elapsedMs).toBeGreaterThanOrEqual(0)
  expect(body.items[0].meta.diagnosticSummary.connectionCode).toBe(connectionCode)
  expect(body.items[0].meta.diagnosticSummary.orderedFields).toEqual(['id', 'order_name'])
})

test('统一查询批量返回错误结构与批量元信息', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceCode } = await createPublishedSimpleService(request, suffix)
  const traceId = `query-batch-${suffix}`

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
    },
    data: {
      serviceCode,
      inputs: [
        { params: { orderId: 1 } },
        { params: { orderId: 'bad' } },
      ],
      requestContext: {
        callerId: `e2e-caller-batch-${suffix}`,
        traceId,
        contextKeys: ['callerId'],
      },
    },
  })
  expect(queryResponse.ok()).toBeTruthy()

  const body = await queryResponse.json()
  expect(body.serviceCode).toBe(serviceCode)
  expect(body.serviceVersion).toBe(1)
  expect(body.status).toBe('PARTIAL_FAILURE')
  expect(body.requestContext.callerId).toBe(`e2e-caller-batch-${suffix}`)
  expect(body.requestContext.traceId).toBe(traceId)
  expect(body.meta.batch).toBe(true)
  expect(body.meta.requestedBatchSize).toBe(2)
  expect(body.meta.successCount).toBe(1)
  expect(body.meta.failureCount).toBe(1)
  expect(body.meta.cacheHit).toBe(false)
  expect(body.meta.traceId).toBe(traceId)
  expect(body.meta.elapsedMs).toBeGreaterThanOrEqual(0)
  expect(body.items).toHaveLength(2)
  expect(body.items[0].status).toBe('SUCCESS')
  expect(body.items[0].rows).toHaveLength(1)
  expect(body.items[0].rows[0]).toMatchObject({
    id: 1,
    order_name: 'order-a',
  })
  expect(body.items[1].status).toBe('FAILURE')
  expect(body.items[1].rows).toEqual([])
  expect(body.items[1].error.errorCode).toBe('INVALID_ARGUMENT')
  expect(body.items[1].error.details).toHaveLength(1)
  expect(body.items[1].error.details[0]).toMatchObject({
    path: 'inputs[1].params.orderId',
    reasonCode: 'TYPE_MISMATCH',
    paramName: 'orderId',
    expectedType: 'LONG',
    actualType: 'STRING',
    collection: false,
  })
  expect(body.items[1].meta.cacheHit).toBe(false)
  expect(body.items[1].meta.diagnosticSummary.failureStage).toBe('PARAM_VALIDATION')
  expect(body.items[1].meta.diagnosticSummary.detailCount).toBe(1)
})

test('统一查询未发布服务返回稳定冲突错误', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceCode } = await createSimpleService(request, suffix, {
    serviceCodePrefix: 'svc_query_draft_e2e',
  })
  const traceId = `query-draft-${suffix}`

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        callerId: `e2e-caller-draft-${suffix}`,
      },
    },
  })
  expect(queryResponse.status()).toBe(409)

  const body = await queryResponse.json()
  expect(body.errorCode).toBe('RESOURCE_CONFLICT')
  expect(body.message).toBe(`服务未发布: ${serviceCode}`)
  expect(body.traceId).toBe(traceId)
})

test('统一查询单次参数错误返回稳定失败结构', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceCode } = await createPublishedSimpleService(request, suffix, {
    serviceCodePrefix: 'svc_query_param_fail_e2e',
  })
  const traceId = `query-param-fail-${suffix}`

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 'bad' } }],
      requestContext: {
        callerId: `e2e-caller-param-${suffix}`,
        traceId,
        contextKeys: ['callerId'],
      },
    },
  })
  expect(queryResponse.ok()).toBeTruthy()

  const body = await queryResponse.json()
  expect(body.status).toBe('FAILURE')
  expect(body.meta.batch).toBe(false)
  expect(body.meta.requestedBatchSize).toBe(1)
  expect(body.meta.successCount).toBe(0)
  expect(body.meta.failureCount).toBe(1)
  expect(body.meta.traceId).toBe(traceId)
  expect(body.items).toHaveLength(1)
  expect(body.items[0].status).toBe('FAILURE')
  expect(body.items[0].error.errorCode).toBe('INVALID_ARGUMENT')
  expect(body.items[0].error.details).toHaveLength(1)
  expect(body.items[0].error.details[0]).toMatchObject({
    path: 'inputs[0].params.orderId',
    reasonCode: 'TYPE_MISMATCH',
    paramName: 'orderId',
    expectedType: 'LONG',
    actualType: 'STRING',
    collection: false,
  })
  expect(body.items[0].meta.diagnosticSummary.failureStage).toBe('PARAM_VALIDATION')
  expect(body.items[0].meta.diagnosticSummary.detailCount).toBe(1)
})

test('统一查询批量超限返回资源保护错误', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceCode } = await createPublishedSimpleService(request, suffix, {
    serviceCodePrefix: 'svc_query_batch_limit_e2e',
    maxBatchSize: 1,
  })
  const traceId = `query-batch-limit-${suffix}`

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [
        { params: { orderId: 1 } },
        { params: { orderId: 2 } },
      ],
      requestContext: {
        callerId: `e2e-caller-batch-limit-${suffix}`,
      },
    },
  })
  expect(queryResponse.status()).toBe(400)

  const body = await queryResponse.json()
  expect(body.errorCode).toBe('INVALID_ARGUMENT')
  expect(body.message).toBe('批量请求超过服务上限: maxBatchSize=1, actualBatchSize=2')
  expect(body.traceId).toBe(traceId)
  expect(body.diagnosticSummary).toMatchObject({
    failureStage: 'BATCH_GUARD',
    protectionType: 'BATCH_SIZE',
    maxBatchSize: 1,
    actualBatchSize: 2,
  })
})
