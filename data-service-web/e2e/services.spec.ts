import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

type ServiceFormPayload = {
  serviceCode?: string
  serviceName: string
  defaultConnectionKeyword: string
  sqlText: string
  remark: string
}

async function createConnection(request: APIRequestContext, suffix: string) {
  const connectionCode = `E2E_SVC_PG_${suffix}`
  const response = await request.post('http://127.0.0.1:8081/api/admin/connections', {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: {
      connectionCode,
      connectionName: `E2E Service PG ${suffix}`,
      dbType: 'POSTGRESQL',
      host: '127.0.0.1',
      port: 5432,
      username: 'postgres',
      password: 'postgres',
      databaseName: 'data_service',
      remark: 'service e2e connection',
    },
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  return {
    connectionCode,
    connectionId: body.id as number,
  }
}

async function createServiceByApi(
  request: APIRequestContext,
  payload: {
    serviceCode: string
    serviceName: string
    defaultConnectionCode: string
    sqlText: string
    remark: string
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
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function updateConnectionStatusByApi(request: APIRequestContext, connectionId: number, status: 'ENABLED' | 'DISABLED') {
  const response = await request.put(`http://127.0.0.1:8081/api/admin/connections/${connectionId}/status`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Operator': 'web-admin',
      'X-Operator-Role': 'ADMIN',
    },
    data: { status },
  })
  expect(response.ok()).toBeTruthy()
}

async function fillServiceForm(page: Page, payload: ServiceFormPayload) {
  if (payload.serviceCode) {
    await page.getByLabel('服务编码').fill(payload.serviceCode)
  }
  await page.getByLabel('服务名称').fill(payload.serviceName)
  await page.getByLabel('默认连接').click()
  const connectionDropdown = page.locator('.ant-select-dropdown:visible').last()
  await expect(connectionDropdown).toBeVisible()
  await connectionDropdown.locator('.ant-select-item-option-content', { hasText: payload.defaultConnectionKeyword }).first().click()
  await page.getByLabel('SQL 文本').fill(payload.sqlText)
  await page.getByLabel('备注').fill(payload.remark)
}

async function confirmVisiblePopconfirm(page: Page, titleText: string) {
  const popconfirm = page.getByRole('tooltip').filter({ hasText: titleText }).last()
  await expect(popconfirm).toBeVisible()
  const confirmButton = popconfirm.getByRole('button', { name: /确\s*定/ }).last()
  await expect(confirmButton).toBeVisible()
  await confirmButton.click({ force: true })
}

async function openSnapshotTab(page: Page) {
  await page.getByRole('tab', { name: /解析结果/ }).click()
  await expect(page.getByText('SQL 解析与校验结果')).toBeVisible()
}

async function openBasicTab(page: Page) {
  await page.getByRole('tab', { name: /基础配置/ }).click()
  await expect(page.getByLabel('服务名称')).toBeVisible()
}

async function expandSnapshotSection(page: Page, name: string) {
  const sectionButton = page.getByRole('button', { name: new RegExp(name) }).first()
  if ((await sectionButton.getAttribute('aria-expanded')) !== 'true') {
    await sectionButton.click()
  }
}

test('服务创建、保存并展示来源字段快照', async ({ page, request }) => {
  test.setTimeout(60_000)

  const apiResponses = {
    list: [] as number[],
  }

  page.on('response', (response) => {
    if (response.url().includes('/api/admin/services') && response.request().method() === 'GET') {
      apiResponses.list.push(response.status())
    }
  })

  const suffix = Date.now().toString().slice(-6)
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_e2e_${suffix}`
  const serviceName = `E2E 服务 ${suffix}`
  const updatedServiceName = `E2E 服务已保存 ${suffix}`
  const initialSql = 'select id as order_id, order_name as order_name from public.orders where id = 1'
  const updatedSql = 'select id as order_id, order_name as order_title from public.orders where id = 1'

  await page.goto('/services')
  await expect(page.getByTestId('services-page')).toBeVisible()
  await expect(page.getByRole('heading', { name: '数据服务' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新增服务' })).toBeVisible()
  await expect.poll(() => apiResponses.list.some((status) => status >= 200 && status < 300)).toBeTruthy()

  await page.getByRole('button', { name: '新增服务' }).click()
  await expect(page).toHaveURL(/\/services\/new$/)
  await expect(page.getByText('新增数据服务')).toBeVisible()
  await expect(page.getByText('未找到该数据服务')).toHaveCount(0)
  await fillServiceForm(page, {
    serviceCode,
    serviceName,
    defaultConnectionKeyword: connectionCode,
    sqlText: initialSql,
    remark: 'e2e create service',
  })
  await expect(page.locator('.sql-code-editor__token--keyword').filter({ hasText: 'select' }).first()).toBeVisible()
  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith('/api/admin/services'),
    { timeout: 30_000 },
  )
  await page.getByRole('button', { name: /保\s*存草稿/ }).click()
  const createResponse = await createResponsePromise
  expect(createResponse.ok()).toBeTruthy()
  await expect(page).toHaveURL(/\/services$/)
  await expect(page.getByText(serviceCode)).toBeVisible()
  await expect(page.getByText(serviceName)).toBeVisible()
  await expect(page.locator('tr', { hasText: serviceCode }).first()).toContainText('草稿')

  const row = page.locator('tr', { hasText: serviceCode }).first()
  await row.getByTestId(/service-edit-/).click()
  await expect(page).toHaveURL(/\/services\/\d+\/edit$/)
  await openSnapshotTab(page)
  await expandSnapshotSection(page, '返回字段')
  await expect(page.getByRole('cell', { name: connectionCode }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'orders' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'order_id' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'order_name' }).first()).toBeVisible()

  await openBasicTab(page)
  await page.getByLabel('服务名称').fill(updatedServiceName)
  await page.getByLabel('SQL 文本').fill(updatedSql)
  await page.getByLabel('备注').fill('e2e update service')
  const updateResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && /\/api\/admin\/services\/\d+$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await page.getByRole('button', { name: /保\s*存草稿/ }).click()
  const updateResponse = await updateResponsePromise
  expect(updateResponse.ok()).toBeTruthy()
  await expect(page).toHaveURL(/\/services$/)
  await expect(page.getByText(updatedServiceName)).toBeVisible()

  const reopenedRow = page.locator('tr', { hasText: serviceCode }).first()
  await reopenedRow.getByTestId(/service-edit-/).click()
  await expect(page).toHaveURL(/\/services\/\d+\/edit$/)
  await openSnapshotTab(page)
  await expandSnapshotSection(page, '返回字段')
  await expect(page.getByText('版本历史')).toBeVisible()
  await expect(page.getByText('当前版本')).toBeVisible()
  await expect(page.getByText('草稿版本')).toBeVisible()
  await expect(page.getByText('历史版本')).toBeVisible()
  await expect(page.getByRole('cell', { name: connectionCode }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'orders' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'order_id' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'order_title' })).toBeVisible()
})

test('服务编辑时展示参数快照并复用已确认类型保存', async ({ page, request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_param_e2e_${suffix}`
  const updatedServiceName = `E2E 参数服务已保存 ${suffix}`

  await createServiceByApi(request, {
    serviceCode,
    serviceName: `E2E 参数服务 ${suffix}`,
    defaultConnectionCode: connectionCode,
    sqlText: 'select id as order_id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
    remark: 'seeded param service',
  })

  await page.goto('/services')
  await expect(page.getByTestId('services-page')).toBeVisible()
  const row = page.locator('tr', { hasText: serviceCode }).first()
  await expect(row).toBeVisible()
  await row.getByTestId(/service-edit-/).click()
  await expect(page).toHaveURL(/\/services\/\d+\/edit$/)

  await expect(page.getByText('参数定义')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'orderId' }).first()).toBeVisible()
  await expect(page.locator('.ant-select-selection-item', { hasText: 'LONG' }).first()).toBeVisible()
  await openSnapshotTab(page)
  await expandSnapshotSection(page, '参数定义')
  await expandSnapshotSection(page, '返回字段')
  await expect(page.getByRole('cell', { name: connectionCode }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'orders' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'orderId', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'order_id' })).toBeVisible()

  await openBasicTab(page)
  await page.getByLabel('服务名称').fill(updatedServiceName)
  await page.getByLabel('备注').fill('e2e update param service')
  const updateResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && /\/api\/admin\/services\/\d+$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await page.getByRole('button', { name: /保\s*存草稿/ }).click()
  const updateResponse = await updateResponsePromise
  expect(updateResponse.ok()).toBeTruthy()
  await expect(page).toHaveURL(/\/services$/)
  await expect(page.getByText(updatedServiceName)).toBeVisible()

  const reopenedRow = page.locator('tr', { hasText: serviceCode }).first()
  await reopenedRow.getByTestId(/service-edit-/).click()
  await expect(page).toHaveURL(/\/services\/\d+\/edit$/)
  await expect(page.getByText('参数定义')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'orderId' }).first()).toBeVisible()
  await expect(page.locator('.ant-select-selection-item', { hasText: 'LONG' }).first()).toBeVisible()
  await openSnapshotTab(page)
  await expandSnapshotSection(page, '参数定义')
  await expandSnapshotSection(page, '返回字段')
  await expect(page.getByRole('cell', { name: 'orderId', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'order_id' })).toBeVisible()
})

test('服务发布、停用与失败明细可见', async ({ page, request }) => {
  test.setTimeout(60_000)

  const successSuffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const publishSuccessConnection = await createConnection(request, successSuffix)
  const publishSuccessCode = `svc_publish_e2e_${successSuffix}`

  await createServiceByApi(request, {
    serviceCode: publishSuccessCode,
    serviceName: `E2E 发布服务 ${successSuffix}`,
    defaultConnectionCode: publishSuccessConnection.connectionCode,
    sqlText: 'select id as order_id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
    remark: 'publish success service',
  })

  const failureSuffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const publishFailureConnection = await createConnection(request, failureSuffix)
  const publishFailureCode = `svc_publish_fail_e2e_${failureSuffix}`

  await createServiceByApi(request, {
    serviceCode: publishFailureCode,
    serviceName: `E2E 发布失败服务 ${failureSuffix}`,
    defaultConnectionCode: publishFailureConnection.connectionCode,
    sqlText: 'select id as order_id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
    remark: 'publish failure service',
  })
  await updateConnectionStatusByApi(request, publishFailureConnection.connectionId, 'DISABLED')

  await page.goto('/services')
  await expect(page.getByTestId('services-page')).toBeVisible()

  const publishSuccessRow = page.locator('tr', { hasText: publishSuccessCode }).first()
  await expect(publishSuccessRow).toBeVisible()
  const publishSuccessResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/api\/admin\/services\/\d+\/publish$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await publishSuccessRow.getByTestId(/service-publish-/).click()
  await confirmVisiblePopconfirm(page, '确定发布该服务吗？')
  expect((await publishSuccessResponse).ok()).toBeTruthy()
  await expect(page.locator('tr', { hasText: publishSuccessCode }).first()).toContainText('已发布')

  const publishedRow = page.locator('tr', { hasText: publishSuccessCode }).first()
  await expect(publishedRow).toContainText('已发布')
  const disableButton = publishedRow.getByTestId(/service-disable-/)
  await expect(disableButton).toBeVisible()
  const disableSuccessResponse = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && /\/api\/admin\/services\/\d+\/status$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await disableButton.click({ force: true })
  await confirmVisiblePopconfirm(page, '确定停用该服务吗？')
  expect((await disableSuccessResponse).ok()).toBeTruthy()
  await expect(page.locator('tr', { hasText: publishSuccessCode }).first()).toContainText('已停用')

  const publishFailureRow = page.locator('tr', { hasText: publishFailureCode }).first()
  await expect(publishFailureRow).toBeVisible()
  const publishFailureResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/api\/admin\/services\/\d+\/publish$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await publishFailureRow.getByTestId(/service-publish-/).click()
  await confirmVisiblePopconfirm(page, '确定发布该服务吗？')
  expect((await publishFailureResponse).ok()).toBeFalsy()
  await expect(page.getByText('发布或停用失败')).toBeVisible()
  await expect(page.locator('.ant-alert-description').getByText(new RegExp(`默认连接未启用: ${publishFailureConnection.connectionCode}`))).toBeVisible()
  await expect(page.locator('tr', { hasText: publishFailureCode }).first()).toContainText('草稿')
})

test('服务预览成功并展示结果与耗时', async ({ page, request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_preview_e2e_${suffix}`

  await createServiceByApi(request, {
    serviceCode,
    serviceName: `E2E 预览服务 ${suffix}`,
    defaultConnectionCode: connectionCode,
    sqlText: 'select id as order_id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
    remark: 'preview success service',
  })

  await page.goto('/services')
  await expect(page.getByTestId('services-page')).toBeVisible()
  const row = page.locator('tr', { hasText: serviceCode }).first()
  await expect(row).toBeVisible()
  await row.getByTestId(/service-edit-/).click()
  await expect(page).toHaveURL(/\/services\/\d+\/edit$/)
  await page.getByPlaceholder('输入默认值').first().fill('1')

  const previewResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/api\/admin\/services\/\d+\/preview$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await page.getByRole('button', { name: '执行预览' }).click()
  const previewResponse = await previewResponsePromise
  expect(previewResponse.ok()).toBeTruthy()
  const previewBody = await previewResponse.json()
  expect(Array.isArray(previewBody.rows)).toBeTruthy()

  await expect(page.getByText('预览结果')).toBeVisible()
  await expect(page.getByText('耗时')).toBeVisible()
  await expect(page.getByText('返回行数')).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'order_id' })).toBeVisible()
  if (previewBody.rows.length > 0) {
    await expect(page.getByRole('cell', { name: '1' }).first()).toBeVisible()
  }
})

test('服务预览失败与权限限制提示可见', async ({ page, request }) => {
  test.setTimeout(60_000)

  const suffix = `${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 100).toString().padStart(2, '0')}`
  const { connectionCode } = await createConnection(request, suffix)
  const serviceCode = `svc_preview_fail_e2e_${suffix}`

  await createServiceByApi(request, {
    serviceCode,
    serviceName: `E2E 预览失败服务 ${suffix}`,
    defaultConnectionCode: connectionCode,
    sqlText: 'select id as order_id, order_name from public.orders where id = /* orderId */1',
    paramDefinitions: [{ paramName: 'orderId', paramType: 'LONG' }],
    remark: 'preview failure service',
  })

  await page.goto('/services')
  await expect(page.getByTestId('services-page')).toBeVisible()
  const row = page.locator('tr', { hasText: serviceCode }).first()
  await expect(row).toBeVisible()
  await row.getByTestId(/service-edit-/).click()
  await expect(page).toHaveURL(/\/services\/\d+\/edit$/)

  await page.getByPlaceholder('输入默认值').first().fill('bad')
  const invalidPreviewResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/api\/admin\/services\/\d+\/preview$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await page.getByRole('button', { name: '执行预览' }).click()
  expect((await invalidPreviewResponsePromise).ok()).toBeFalsy()
  await expect(page.getByText('预览执行失败')).toBeVisible()
  await expect(page.locator('.ant-alert-description').getByText(/预览参数类型不匹配: orderId/)).toBeVisible()

  await page.route('**/api/admin/services/*/preview', async (route) => {
    const headers = { ...route.request().headers() }
    delete headers['x-operator']
    delete headers['x-operator-role']
    await route.continue({ headers })
  })

  await page.getByPlaceholder('输入默认值').first().fill('1')
  const deniedPreviewResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/api\/admin\/services\/\d+\/preview$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 },
  )
  await page.getByRole('button', { name: '执行预览' }).click()
  const deniedPreviewResponse = await deniedPreviewResponsePromise
  expect(deniedPreviewResponse.status()).toBe(403)
  const deniedPreviewText = await deniedPreviewResponse.text()
  expect(deniedPreviewText).toContain('"errorCode":"ACCESS_DENIED"')
  expect(deniedPreviewText).toContain('"traceId":"')
  await expect(page.getByText('预览执行失败')).toBeVisible()
  await expect(page.locator('.ant-alert-description').last().getByText(/traceId:/)).toBeVisible()
  await page.unroute('**/api/admin/services/*/preview')
})
