import { expect, test } from '@playwright/test'
import {
  createFederatedDraft,
  deleteService,
  createPostgresConnection,
  createPredefinedCrossSourceDraft,
  createSimpleQueryDraft,
  publishService,
  saveFederatedSql,
  uniqueCode,
} from './support/admin-api'

async function confirmVisiblePopconfirm(page: Parameters<typeof test>[0]['page']) {
  const confirmButton = page.locator('.ant-popover:visible .ant-popconfirm-buttons .ant-btn-primary').last()
  await expect(confirmButton).toBeVisible()
  await confirmButton.evaluate((element) => {
    ;(element as HTMLButtonElement).click()
  })
}

test.describe('管理端未执行主流程补测', () => {
  test('连接管理可新增 PostgreSQL 连接、编辑后保持脱敏，并对非法参数显示失败提示', async ({ page, request }) => {
    test.setTimeout(60_000)
    const failedResponses: Array<{ url: string; status: number }> = []
    const consoleErrors: string[] = []
    const connectionCode = uniqueCode('pw_conn')
    const updatedName = `${connectionCode}_updated`

    page.on('response', (response) => {
      if (response.url().includes('/api/') && response.status() >= 400) {
        failedResponses.push({ url: response.url(), status: response.status() })
      }
    })
    page.on('console', (message) => {
      const text = message.text()
      if (message.type() === 'error') {
        if (text.includes('antd v5 support React is 16 ~ 18')) {
          return
        }
        if (text.includes('Instance created by `useForm`')) {
          return
        }
        if (text.includes('Failed to load resource') && text.includes('409 (Conflict)')) {
          return
        }
        consoleErrors.push(text)
      }
    })

    await page.goto('/datasource')
    await expect(page.getByRole('heading', { name: '数据源连接' })).toBeVisible()

    await page.getByRole('button', { name: '新建连接' }).click()
    await page.getByLabel('连接编码').fill(connectionCode)
    await page.getByLabel('连接名称').fill(connectionCode)
    await page.getByLabel('主机').fill('127.0.0.1')
    await page.getByLabel('用户名').fill('postgres')
    await page.getByLabel('密码').fill('postgres')
    await page.getByLabel('扩展配置 JSON').fill('{\n  "database": "data_service"\n}')
    await page.getByRole('button', { name: '新增目标库' }).click()
    await page.locator('#catalogs_0_catalogValue').fill('public')
    await expect(page.locator('.catalog-edit-table .ant-select-selection-item').first()).toHaveText('SCHEMA')
    await page.getByRole('button', { name: '新增目标库' }).click()
    await page.locator('#catalogs_1_catalogValue').fill('analytics')
    await expect(page.getByText('同一连接下目标库名称不可重复')).toHaveCount(0)
    const createResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith('/api/admin/connections') &&
        response.status() < 500,
    )
    await page.getByRole('button', { name: /保\s*存/ }).click()
    const createResponse = await createResponsePromise
    expect(createResponse.status()).toBe(200)

    await page.reload()

    await page.getByRole('row', { name: new RegExp(connectionCode) }).getByLabel('编辑连接').click()
    await expect(page.locator('#catalogs_0_catalogValue')).toHaveValue('public')
    await expect(page.locator('#catalogs_1_catalogValue')).toHaveValue('analytics')
    await page.getByLabel('连接名称').fill(updatedName)
    await page.locator('#catalogs_1_catalogValue').fill('reporting')
    await page.getByLabel('删除目标库第 1 行').click()
    const updateResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().includes('/api/admin/connections/') &&
        response.url().includes('/api/admin/connections/') &&
        response.status() < 500,
    )
    await page.getByRole('button', { name: /保\s*存/ }).click()
    const updateResponse = await updateResponsePromise
    expect(updateResponse.status()).toBe(200)

    await page.reload()
    await expect(page.getByRole('cell', { name: updatedName, exact: true })).toBeVisible()
    await page.getByRole('row', { name: new RegExp(connectionCode) }).getByLabel('编辑连接').click()
    await expect(page.getByLabel('连接名称')).toHaveValue(updatedName)
    await expect(page.locator('#catalogs_0_catalogValue')).toHaveValue('reporting')
    await expect(page.getByLabel('密码（留空则沿用）')).toHaveValue('')
    await expect(page.getByLabel('扩展配置 JSON')).toHaveValue('***MASKED***')
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: '新建连接' }).click()
    await page.getByLabel('连接编码').fill(uniqueCode('pw_bad_conn'))
    await page.getByLabel('连接名称').fill('pw bad conn')
    await page.getByLabel('主机').fill('127.0.0.1')
    await page.getByLabel('端口').fill('15432')
    await page.getByLabel('用户名').fill('postgres')
    await page.getByLabel('密码').fill('wrong-password')
    await page.getByLabel('扩展配置 JSON').fill('{\n  "database": "data_service"\n}')
    await page.getByRole('button', { name: '新增目标库' }).click()
    await page.locator('#catalogs_0_catalogValue').fill('dup_schema')
    await page.getByRole('button', { name: '新增目标库' }).click()
    await page.locator('#catalogs_1_catalogValue').fill('dup_schema')
    await page.getByRole('button', { name: /保\s*存/ }).click()
    await expect(page.getByText('同一连接下目标库名称不可重复')).toHaveCount(2)
    await page.locator('#catalogs_1_catalogValue').fill('dup_schema_2')
    await page.getByRole('button', { name: /保\s*存/ }).click()

    await expect(page.getByText('执行失败').or(page.getByText('连接测试失败'))).toBeVisible()
    expect(consoleErrors).toEqual([])
    expect(failedResponses.some((item) => item.url.includes('/api/admin/connections') && item.status >= 400)).toBe(true)

    const definitionList = await request.get('/api/admin/connections', {
      headers: {
        'X-Operator': 'web-admin',
        'X-Operator-Role': 'ADMIN',
      },
    })
    expect(definitionList.ok()).toBeTruthy()
  })

  test('服务配置可创建草稿、查看、发布、停用、删除，并在配置缺失时阻止发布', async ({ page, request }) => {
    test.setTimeout(60_000)
    const baseConnection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_svc_conn'),
    })
    const serviceCode = uniqueCode('pw_svc')
    const invalidServiceCode = uniqueCode('pw_svc_invalid')

    const draft = await createSimpleQueryDraft(request, {
      serviceCode,
      serviceName: `Playwright 服务 ${serviceCode}`,
      connectionId: baseConnection.connection.id!,
      catalogId: baseConnection.catalogs[0].id!,
    })

    await page.goto('/service')
    await expect(page.locator('.module-hero').getByText('数据服务')).toBeVisible()
    await page.getByPlaceholder('搜索服务...').fill(serviceCode)
    const serviceRow = page.locator('.ant-table-tbody').first().getByRole('row', {
      name: new RegExp(`${serviceCode}\\s+Playwright 服务 ${serviceCode}`),
    })
    await expect(serviceRow.getByRole('cell', { name: serviceCode, exact: true })).toBeVisible()

    await serviceRow.getByLabel('查看服务详情').click()
    await expect(page.getByText('服务概览')).toBeVisible()
    await expect(page.getByText(serviceCode, { exact: true }).last()).toBeVisible()
    await expect(page.getByText('普通服务', { exact: true }).last()).toBeVisible()
    const detailDialog = page.getByRole('dialog', { name: '服务详情' })
    await detailDialog.getByRole('button', { name: /关闭|Close/ }).click()
    await expect(detailDialog).toBeHidden()

    const publishResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/admin/service-definitions/') &&
        response.url().endsWith('/publish') &&
        response.status() < 500,
    )
    await serviceRow.getByLabel('发布服务').click()
    const publishResponse = await publishResponsePromise
    expect(publishResponse.status()).toBe(200)
    await expect(serviceRow).toContainText('已发布')

    const disableResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().includes('/api/admin/service-definitions/') &&
        response.url().endsWith('/status') &&
        response.status() < 500,
    )
    await serviceRow.getByLabel('停用服务').click()
    await confirmVisiblePopconfirm(page)
    const disableResponse = await disableResponsePromise
    expect(disableResponse.status()).toBe(200)
    await expect(serviceRow).toContainText('已停用')

    await deleteService(request, draft.definition.id!)
    await page.reload()
    await expect(page.getByRole('cell', { name: serviceCode, exact: true })).toHaveCount(0)

    await createSimpleQueryDraft(request, {
      serviceCode: invalidServiceCode,
      serviceName: `Invalid ${invalidServiceCode}`,
      connectionId: baseConnection.connection.id!,
      catalogId: baseConnection.catalogs[0].id!,
      params: [],
      fields: [],
    })

    await page.reload()
    await page.getByPlaceholder('搜索服务...').fill(invalidServiceCode)
    const invalidRow = page.locator('.ant-table-tbody').first().getByRole('row', {
      name: new RegExp(`${invalidServiceCode}\\s+Invalid ${invalidServiceCode}`),
    })
    await expect(invalidRow.getByRole('cell', { name: invalidServiceCode, exact: true })).toBeVisible()
    await invalidRow.getByLabel('发布服务').click()
    await expect(page.locator('.ant-message-notice').getByText('发布失败: 未配置参数')).toBeVisible()
    await expect(page.getByRole('cell', { name: '草稿', exact: true }).first()).toBeVisible()
  })

  test('服务配置可通过 SQL 自动识别创建草稿', async ({ page, request }) => {
    test.setTimeout(60_000)
    const baseConnection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_sql_detect_conn'),
    })
    const serviceCode = uniqueCode('pw_sql_detect')

    await page.goto('/service')
    await expect(page.locator('.module-hero').getByText('数据服务')).toBeVisible()

    await page.getByRole('button', { name: '新建服务' }).click()
    await page.getByLabel('服务编码').fill(serviceCode)
    await page.getByLabel('服务名称').fill(`SQL 即服务 ${serviceCode}`)
    await page.getByLabel('默认连接').click()
    await page.getByRole('combobox', { name: '默认连接' }).fill(baseConnection.connection.connectionCode)
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({
      hasText: baseConnection.connection.connectionCode,
    }).first().click()
    await page.locator('.sql-highlight-editor textarea').fill(
      'select cb.customer_id as customerId, cb.customer_name as customerName from customer_base cb where cb.customer_id = /* customerId */0 and cb.active = /* active */false',
    )

    const detectResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith('/api/admin/service-definitions/sql-auto-detect') &&
        response.status() === 200,
    )
    await page.getByRole('button', { name: /reload SQL 解析/ }).click()
    const detectResponse = await detectResponsePromise
    expect(detectResponse.status()).toBe(200)

    await expect(page.locator('#sources_0_sourceAlias')).toHaveValue('cb')
    await expect(page.locator('#sources_0_sourceValue')).toHaveValue('customer_base')
    await expect(page.locator('#params_0_paramName')).toHaveValue('customerId')
    await expect(page.locator('#params_1_paramName')).toHaveValue('active')
    await expect(page.locator('#fields_0_fieldName')).toHaveValue('customerId')
    await expect(page.locator('#fields_1_fieldName')).toHaveValue('customerName')

    const createResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith('/api/admin/service-definitions') &&
        response.status() === 200,
    )
    await page.getByRole('button', { name: /保存草稿/ }).click()
    const createResponse = await createResponsePromise
    expect(createResponse.status()).toBe(200)

    await page.getByPlaceholder('搜索服务...').fill(serviceCode)
    const serviceRow = page.locator('.ant-table-tbody').first().getByRole('row', {
      name: new RegExp(`${serviceCode}\\s+SQL 即服务 ${serviceCode}`),
    })
    await expect(serviceRow).toBeVisible()
  })

  test('数据服务页对非法联邦 SQL 和来源不一致展示明确错误', async ({ page, request }) => {
    test.setTimeout(60_000)
    const primary = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_primary'),
    })
    const child = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_child'),
    })
    const draft = await createFederatedDraft(request, {
      primaryConnectionId: primary.connection.id!,
      primaryCatalogId: primary.catalogs[0].id!,
      childConnectionId: child.connection.id!,
      childCatalogId: child.catalogs[0].id!,
    })

    await page.goto('/service')
    await expect(page.locator('.module-hero').getByText('数据服务')).toBeVisible()
    await page.getByPlaceholder('搜索服务...').fill(draft.definition.serviceCode)
    const serviceRow = page.locator('.ant-table-tbody').first().getByRole('row', {
      name: new RegExp(`${draft.definition.serviceCode}\\s+Playwright 联邦服务 ${draft.definition.serviceCode}`),
    })
    const workspaceResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes(`/api/admin/service-definitions/${draft.definition.id}/federated-metadata`) &&
        response.status() === 200,
    )
    await serviceRow.getByLabel('查看服务详情').click()
    await workspaceResponsePromise
    await expect(page.getByRole('dialog', { name: '服务详情' })).toBeVisible()

    await page.locator('textarea').first().fill('SELECT FROM')
    await page.getByRole('button', { name: '保存并校验' }).click()
    await expect(page.locator('.ant-message-notice').getByText(/联邦 SQL 解析失败/).first()).toBeVisible()

    await page.locator('textarea').first().fill(
      'SELECT wrong_alias.customer_id FROM wrong_alias WHERE wrong_alias.customer_id = /* customerId */0',
    )
    await page.getByRole('button', { name: '保存并校验' }).click()
    await expect(page.locator('.ant-message-notice').getByText(/联邦 SQL 校验失败/).first()).toBeVisible()
  })

  test('统一查询调试页可展示联邦执行摘要字段', async ({ page, request }) => {
    test.setTimeout(60_000)
    const primary = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_sum_primary'),
    })
    const child = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_sum_child'),
    })
    const draft = await createPredefinedCrossSourceDraft(request, {
      primaryConnectionId: primary.connection.id!,
      primaryCatalogId: primary.catalogs[0].id!,
      childConnectionId: child.connection.id!,
      childCatalogId: child.catalogs[0].id!,
    })
    await publishService(request, draft.definition.id!)

    await page.goto('/query-debug')
    await expect(page.getByRole('heading', { name: '统一查询调试' })).toBeVisible()

    await page.getByLabel('服务编码').fill(draft.definition.serviceCode)
    await page.getByLabel('params JSON').fill('{\n  "active": true,\n  "customerIds": [3001, 3002]\n}')
    await page.getByRole('button', { name: '执行调试' }).click()

    await expect(page.getByText(/"federatedExecution"/).first()).toBeVisible()
    await expect(page.getByText(/"stageWaveCount"/).first()).toBeVisible()
    await expect(page.getByText(/"executedStageCount"/).first()).toBeVisible()
    await expect(page.getByText(/"resultBuffer"/).first()).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Alice', exact: true })).toBeVisible()
  })

  test('统一查询调试页对缺失参数展示显式错误态', async ({ page, request }) => {
    test.setTimeout(60_000)
    const connection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_query_conn'),
    })
    const draft = await createSimpleQueryDraft(request, {
      serviceCode: uniqueCode('pw_query_missing'),
      connectionId: connection.connection.id!,
      catalogId: connection.catalogs[0].id!,
    })
    await publishService(request, draft.definition.id!)

    await page.goto('/query-debug')
    await expect(page.getByRole('heading', { name: '统一查询调试' })).toBeVisible()

    await page.getByLabel('服务编码').fill(draft.definition.serviceCode)
    await page.getByLabel('params JSON').fill('{\n  "customerId": 1001\n}')
    await page.getByRole('button', { name: '执行调试' }).click()

    await expect(page.getByText('执行失败')).toBeVisible()
    await expect(page.locator('.ant-alert-description').getByText('缺少必填参数: active')).toBeVisible()
  })
})
