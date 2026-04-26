import { expect, test, type APIRequestContext } from '@playwright/test'

async function createConnection(request: APIRequestContext, suffix: string) {
  const connectionCode = `E2E_CACHE_PG_${suffix}`
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName: `E2E Cache PG ${suffix}`,
      dbType: 'POSTGRESQL',
      host: '127.0.0.1',
      port: 5432,
      username: 'postgres',
      password: 'postgres',
      databaseName: 'data_service',
      remark: 'cache e2e connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  return { connectionCode }
}

async function createSimpleService(request: APIRequestContext, suffix: string) {
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_cache_e2e_${suffix}`
  const createResponse = await request.post('http://127.0.0.1:8081/api/admin/services', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      serviceCode,
      serviceName: `E2E 缓存服务 ${suffix}`,
      sqlType: 'SIMPLE_SQL',
      defaultConnectionCode: connectionCode,
      sqlText: 'select id, order_name from public.orders where id = /* orderId */1',
      paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
      maxBatchSize: 100,
      maxResultRows: 1000,
      queryTimeoutSeconds: 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'cache e2e service',
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createdService = (await createResponse.json()) as { id: number }
  return { serviceId: createdService.id, serviceCode, connectionCode }
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

async function updateService(request: APIRequestContext, serviceId: number, connectionCode: string) {
  const updateResponse = await request.put(`http://127.0.0.1:8081/api/admin/services/${serviceId}`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      serviceName: `E2E 缓存服务 V2 ${serviceId}`,
      sqlType: 'SIMPLE_SQL',
      defaultConnectionCode: connectionCode,
      sqlText: "select id, 'version-two' as order_name from public.orders where id = /* orderId */1",
      paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
      maxBatchSize: 100,
      maxResultRows: 1000,
      queryTimeoutSeconds: 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'cache e2e service v2',
    },
  })
  expect(updateResponse.ok()).toBeTruthy()
}

async function upsertCachePolicy(request: APIRequestContext, serviceId: number) {
  const response = await request.put(`http://127.0.0.1:8081/api/admin/services/${serviceId}/cache-policy`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      enabled: true,
      ttlSeconds: 300,
      cacheKeyTemplate: 'data-service:{serviceCode}:v{version}:{paramHash}',
      maxEntries: 1000,
      contextKeys: [],
      remark: 'cache e2e policy',
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function queryService(request: APIRequestContext, serviceCode: string, traceId: string) {
  const response = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        callerId: `cache-caller-${traceId}`,
        traceId,
      },
    },
  })
  expect(response.ok()).toBeTruthy()
  return response.json()
}

test('缓存命中与版本切换后隔离都可验证', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceId, serviceCode, connectionCode } = await createSimpleService(request, suffix)
  await publishService(request, serviceId)
  await upsertCachePolicy(request, serviceId)

  const firstBody = await queryService(request, serviceCode, `cache-first-${suffix}`)
  expect(firstBody.serviceVersion).toBe(1)
  expect(firstBody.meta.cacheHit).toBe(false)
  expect(firstBody.items[0].meta.cacheHit).toBe(false)
  expect(firstBody.items[0].meta.diagnosticSummary.cacheMissReason).toBe('MISS')
  expect(firstBody.items[0].rows[0]).toMatchObject({
    id: 1,
    order_name: 'order-a',
  })

  const secondBody = await queryService(request, serviceCode, `cache-second-${suffix}`)
  expect(secondBody.serviceVersion).toBe(1)
  expect(secondBody.meta.cacheHit).toBe(true)
  expect(secondBody.items[0].meta.cacheHit).toBe(true)
  expect(secondBody.items[0].meta.diagnosticSummary.cacheHit).toBe(true)
  expect(secondBody.items[0].rows[0]).toMatchObject({
    id: 1,
    order_name: 'order-a',
  })

  await updateService(request, serviceId, connectionCode)
  await publishService(request, serviceId)

  const thirdBody = await queryService(request, serviceCode, `cache-third-${suffix}`)
  expect(thirdBody.serviceVersion).toBe(2)
  expect(thirdBody.meta.cacheHit).toBe(false)
  expect(thirdBody.items[0].meta.cacheHit).toBe(false)
  expect(thirdBody.items[0].meta.diagnosticSummary.cacheMissReason).toBe('MISS')
  expect(thirdBody.items[0].meta.diagnosticSummary.cacheKey).not.toBe(
    secondBody.items[0].meta.diagnosticSummary.cacheKey,
  )
  expect(thirdBody.items[0].rows[0]).toMatchObject({
    id: 1,
    order_name: 'version-two',
  })

  const fourthBody = await queryService(request, serviceCode, `cache-fourth-${suffix}`)
  expect(fourthBody.serviceVersion).toBe(2)
  expect(fourthBody.meta.cacheHit).toBe(true)
  expect(fourthBody.items[0].meta.cacheHit).toBe(true)
  expect(fourthBody.items[0].rows[0]).toMatchObject({
    id: 1,
    order_name: 'version-two',
  })
})
