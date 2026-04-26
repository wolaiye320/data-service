import { expect, test, type APIRequestContext } from '@playwright/test'

async function createConnection(request: APIRequestContext, suffix: string) {
  const connectionCode = `E2E_CACHE_CTX_PG_${suffix}`
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName: `E2E Cache Ctx PG ${suffix}`,
      dbType: 'POSTGRESQL',
      host: '127.0.0.1',
      port: 5432,
      username: 'postgres',
      password: 'postgres',
      databaseName: 'data_service',
      remark: 'cache context e2e connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  return { connectionCode }
}

async function createPublishedService(request: APIRequestContext, suffix: string) {
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_cache_ctx_e2e_${suffix}`
  const createResponse = await request.post('http://127.0.0.1:8081/api/admin/services', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      serviceCode,
      serviceName: `E2E 缓存上下文服务 ${suffix}`,
      sqlType: 'SIMPLE_SQL',
      defaultConnectionCode: connectionCode,
      sqlText: 'select id, order_name from public.orders where id = /* orderId */1',
      paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
      maxBatchSize: 100,
      maxResultRows: 1000,
      queryTimeoutSeconds: 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'cache context e2e service',
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
  return { serviceId: createdService.id, serviceCode }
}

async function upsertCachePolicy(
  request: APIRequestContext,
  serviceId: number,
  options: {
    cacheKeyTemplate: string
    contextKeys: string[]
    remark: string
  },
) {
  const response = await request.put(`http://127.0.0.1:8081/api/admin/services/${serviceId}/cache-policy`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      enabled: true,
      ttlSeconds: 300,
      cacheKeyTemplate: options.cacheKeyTemplate,
      maxEntries: 1000,
      contextKeys: options.contextKeys,
      remark: options.remark,
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function clearCache(request: APIRequestContext, serviceId: number) {
  const response = await request.delete(`http://127.0.0.1:8081/api/admin/services/${serviceId}/cache-policy/cache`, {
    headers: {
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function queryService(request: APIRequestContext, serviceCode: string, callerId: string, traceId: string) {
  const response = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        callerId,
        traceId,
        contextKeys: ['callerId'],
      },
    },
  })
  expect(response.ok()).toBeTruthy()
  return response.json()
}

test('缓存策略变更、上下文隔离和显式清理都可验证', async ({ request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceId, serviceCode } = await createPublishedService(request, suffix)
  await upsertCachePolicy(request, serviceId, {
    cacheKeyTemplate: 'data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}',
    contextKeys: ['callerId'],
    remark: 'cache context caller isolation',
  })

  const callerAFirst = await queryService(request, serviceCode, `caller-a-${suffix}`, `ctx-a1-${suffix}`)
  expect(callerAFirst.meta.cacheHit).toBe(false)
  expect(callerAFirst.items[0].meta.cacheHit).toBe(false)
  const callerACacheKey = callerAFirst.items[0].meta.diagnosticSummary.cacheKey

  const callerASecond = await queryService(request, serviceCode, `caller-a-${suffix}`, `ctx-a2-${suffix}`)
  expect(callerASecond.meta.cacheHit).toBe(true)
  expect(callerASecond.items[0].meta.cacheHit).toBe(true)
  expect(callerASecond.items[0].meta.diagnosticSummary.cacheKey).toBe(callerACacheKey)

  const callerBFirst = await queryService(request, serviceCode, `caller-b-${suffix}`, `ctx-b1-${suffix}`)
  expect(callerBFirst.meta.cacheHit).toBe(false)
  expect(callerBFirst.items[0].meta.cacheHit).toBe(false)
  expect(callerBFirst.items[0].meta.diagnosticSummary.cacheKey).not.toBe(callerACacheKey)

  await upsertCachePolicy(request, serviceId, {
    cacheKeyTemplate: 'data-service:{serviceCode}:v{version}:{paramHash}',
    contextKeys: [],
    remark: 'cache context removed',
  })

  const afterPolicyChange = await queryService(request, serviceCode, `caller-a-${suffix}`, `ctx-a3-${suffix}`)
  expect(afterPolicyChange.meta.cacheHit).toBe(false)
  expect(afterPolicyChange.items[0].meta.cacheHit).toBe(false)
  expect(afterPolicyChange.items[0].meta.diagnosticSummary.cacheKey).not.toBe(callerACacheKey)

  const afterPolicyChangeSecond = await queryService(request, serviceCode, `caller-a-${suffix}`, `ctx-a4-${suffix}`)
  expect(afterPolicyChangeSecond.meta.cacheHit).toBe(true)
  expect(afterPolicyChangeSecond.items[0].meta.cacheHit).toBe(true)

  await clearCache(request, serviceId)

  const afterExplicitClear = await queryService(request, serviceCode, `caller-a-${suffix}`, `ctx-a5-${suffix}`)
  expect(afterExplicitClear.meta.cacheHit).toBe(false)
  expect(afterExplicitClear.items[0].meta.cacheHit).toBe(false)
  expect(afterExplicitClear.items[0].meta.diagnosticSummary.cacheMissReason).toBe('EXPLICIT_EVICT')
})
