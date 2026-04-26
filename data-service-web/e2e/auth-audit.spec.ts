import { expect, test, type APIRequestContext } from '@playwright/test'

async function createConnection(request: APIRequestContext, suffix: string) {
  const connectionCode = `E2E_AUTH_PG_${suffix}`
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName: `E2E Auth PG ${suffix}`,
      dbType: 'POSTGRESQL',
      host: '127.0.0.1',
      port: 5432,
      username: 'postgres',
      password: 'postgres',
      databaseName: 'data_service',
      remark: 'auth audit e2e connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  return { connectionCode }
}

async function createServiceByApi(
  request: APIRequestContext,
  payload: {
    serviceCode: string
    serviceName: string
    defaultConnectionCode: string
    sqlText: string
    tenantId?: string
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
      sqlType: 'SIMPLE_SQL',
      maxBatchSize: 100,
      maxResultRows: 1000,
      queryTimeoutSeconds: 30,
      federatedQueryTimeoutSeconds: 60,
      remark: 'auth audit e2e service',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { id: number }
}

async function publishService(request: APIRequestContext, serviceId: number) {
  const response = await request.post(`http://127.0.0.1:8081/api/admin/services/${serviceId}/publish`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {},
  })
  expect(response.ok()).toBeTruthy()
}

async function createPublishedSimpleService(request: APIRequestContext, suffix: string) {
  const { connectionCode } = await createConnection(request, suffix)
  const service = await createServiceByApi(request, {
    serviceCode: `svc_auth_e2e_${suffix}`,
    serviceName: `E2E 权限服务 ${suffix}`,
    defaultConnectionCode: connectionCode,
    sqlText: 'select id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
  })
  await publishService(request, service.id)
  return { serviceCode: `svc_auth_e2e_${suffix}` }
}

async function createPublishedTenantService(request: APIRequestContext, suffix: string) {
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_tenant_e2e_${suffix}`
  const service = await createServiceByApi(request, {
    serviceCode,
    serviceName: `E2E 租户服务 ${suffix}`,
    defaultConnectionCode: connectionCode,
    tenantId: 'tenant-a',
    sqlText: 'select id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
  })
  await publishService(request, service.id)
  return { serviceId: service.id, serviceCode }
}

test('管理页未授权与查询调用方缺失都会被拒绝', async ({ page, request }) => {
  test.setTimeout(60_000)

  const deniedResponses: Array<{ status: number; body: string }> = []
  await page.route('**/api/admin/**', async (route) => {
    const headers = { ...route.request().headers() }
    delete headers['x-operator']
    delete headers['x-operator-role']
    await route.continue({ headers })
  })
  page.on('response', async (response) => {
    if (response.request().method() === 'GET' && response.url().includes('/api/admin/connections')) {
      deniedResponses.push({
        status: response.status(),
        body: await response.text(),
      })
    }
  })

  await page.goto('/connections')
  await expect(page.getByTestId('connections-page')).toBeVisible()
  await expect(page.getByText('数据源连接加载失败')).toBeVisible()
  await expect(page.getByText(/traceId:/)).toBeVisible()
  await expect.poll(() => deniedResponses.some((item) => item.status === 403)).toBeTruthy()
  expect(deniedResponses.some((item) => item.body.includes('"errorCode":"ACCESS_DENIED"'))).toBeTruthy()
  await page.unroute('**/api/admin/**')

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceCode } = await createPublishedSimpleService(request, suffix)
  const traceId = `auth-query-denied-${suffix}`

  const queryResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        traceId,
      },
    },
  })
  expect(queryResponse.status()).toBe(403)

  const body = await queryResponse.json()
  expect(body.errorCode).toBe('ACCESS_DENIED')
  expect(body.message).toBe('查询接口要求提供调用方标识 callerId')
  expect(body.traceId).toBe(traceId)
})

test('审计页可检索预览权限拒绝与租户越权日志', async ({ page, request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { serviceId, serviceCode } = await createPublishedTenantService(request, suffix)
  const previewTraceId = `audit-preview-denied-${suffix}`
  const tenantTraceId = `audit-tenant-denied-${suffix}`

  const previewDeniedResponse = await request.post(`http://127.0.0.1:8081/api/admin/services/${serviceId}/preview`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': previewTraceId,
    },
    data: {
      previewParams: { orderId: 1 },
      requestContext: {
        callerId: `preview-caller-${suffix}`,
      },
    },
  })
  expect(previewDeniedResponse.status()).toBe(403)
  const previewDeniedBody = await previewDeniedResponse.json()
  expect(previewDeniedBody.errorCode).toBe('ACCESS_DENIED')
  expect(previewDeniedBody.traceId).toBe(previewTraceId)

  const tenantDeniedResponse = await request.post('http://127.0.0.1:8081/api/data-services/query', {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': tenantTraceId,
    },
    data: {
      serviceCode,
      inputs: [{ params: { orderId: 1 } }],
      requestContext: {
        tenantId: 'tenant-b',
        callerId: `tenant-caller-${suffix}`,
        traceId: tenantTraceId,
      },
    },
  })
  expect(tenantDeniedResponse.status()).toBe(403)
  const tenantDeniedBody = await tenantDeniedResponse.json()
  expect(tenantDeniedBody.errorCode).toBe('ACCESS_DENIED')
  expect(tenantDeniedBody.message).toContain('查询接口租户越权')
  expect(tenantDeniedBody.traceId).toBe(tenantTraceId)

  const listStatuses: number[] = []
  page.on('response', (response) => {
    if (response.url().includes('/api/admin/audits') && response.request().method() === 'GET') {
      listStatuses.push(response.status())
    }
  })

  await page.goto('/audits')
  await expect(page.getByTestId('audits-page')).toBeVisible()
  await expect.poll(() => listStatuses.some((status) => status >= 200 && status < 300)).toBeTruthy()

  await page.getByLabel('TraceId').fill(previewTraceId)
  await page.getByLabel('事件类型').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', { hasText: 'ADMIN_ACCESS_DENIED' }).click()
  await page.getByRole('button', { name: /查\s*询/ }).click()
  await expect(page.locator('tr', { hasText: previewTraceId }).first()).toContainText('ADMIN_ACCESS_DENIED')
  await page.locator('tr', { hasText: previewTraceId }).first().getByTestId(/audit-detail-/).click()
  const detailDrawer = page.getByRole('dialog')
  await expect(detailDrawer.getByText('管理接口权限拒绝')).toBeVisible()
  await expect(detailDrawer.getByText(`/api/admin/services/${serviceId}/preview`)).toBeVisible()
  await expect(detailDrawer.getByText('MISSING_OR_INVALID_ADMIN_HEADERS')).toBeVisible()
  await page.keyboard.press('Escape')

  await page.getByRole('button', { name: /重\s*置/ }).click()
  await page.getByLabel('服务编码').fill(serviceCode)
  await page.getByLabel('TraceId').fill(tenantTraceId)
  await page.getByLabel('事件类型').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', { hasText: 'QUERY_TENANT_ACCESS_DENIED' }).click()
  await page.getByRole('button', { name: /查\s*询/ }).click()
  const tenantRow = page.locator('tr', { hasText: tenantTraceId }).first()
  await expect(tenantRow).toContainText('QUERY_TENANT_ACCESS_DENIED')
  await expect(tenantRow).toContainText(serviceCode)
  await tenantRow.getByTestId(/audit-detail-/).click()
  const tenantDetailDrawer = page.getByRole('dialog')
  await expect(tenantDetailDrawer.getByText('查询接口租户校验拒绝')).toBeVisible()
  const detailJsonBlock = tenantDetailDrawer.locator('.audit-json-block').filter({ hasText: 'detailJson' })
  const contextSummaryBlock = tenantDetailDrawer.locator('.audit-json-block').filter({ hasText: 'contextSummaryJson' })
  await expect(detailJsonBlock.getByText('"requiredTenantId":"tenant-a"')).toBeVisible()
  await expect(detailJsonBlock.getByText('"actualTenantId":"tenant-b"')).toBeVisible()
  await expect(contextSummaryBlock.getByText('"requiredTenantId":"tenant-a"')).toBeVisible()
  await expect(contextSummaryBlock.getByText('"actualTenantId":"tenant-b"')).toBeVisible()
  await expect(contextSummaryBlock.getByText(`"callerId":"tenant-caller-${suffix}"`)).toBeVisible()
})
