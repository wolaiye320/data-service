import { expect, test } from '@playwright/test'
import {
  createFederatedDraft,
  createPostgresConnection,
  createPredefinedCrossSourceDraft,
  createSimpleQueryDraft,
  publishService,
  saveFederatedSql,
  uniqueCode,
} from './support/admin-api'

async function chooseSelectOption(page: Parameters<typeof test>[0]['page'], inputSelector: string, optionTitle: string) {
  const input = page.locator(inputSelector)
  await input.focus()
  await input.press('ArrowDown')
  await page.locator(`.ant-select-dropdown:visible [title="${optionTitle}"]`).first().click()
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
    await expect(page.getByRole('heading', { name: '数据源连接管理' })).toBeVisible()

    await page.getByRole('button', { name: '新建连接' }).click()
    await page.getByLabel('连接编码').fill(connectionCode)
    await page.getByLabel('连接名称').fill(connectionCode)
    await page.getByLabel('主机').fill('127.0.0.1')
    await page.getByLabel('用户名').fill('postgres')
    await page.getByLabel('密码').fill('postgres')
    await page.getByLabel('扩展配置 JSON').fill('{\n  "database": "data_service"\n}')
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

    await page.getByLabel(`编辑连接 ${connectionCode}`).click()
    await page.getByLabel('连接名称').fill(updatedName)
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
    await page.getByLabel(`编辑连接 ${connectionCode}`).click()
    await expect(page.getByLabel('连接名称')).toHaveValue(updatedName)
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

  test('服务配置可创建草稿、发布、停用，并在配置缺失时阻止发布', async ({ page, request }) => {
    test.setTimeout(60_000)
    const baseConnection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_svc_conn'),
    })
    const serviceCode = uniqueCode('pw_svc')
    const invalidServiceCode = uniqueCode('pw_svc_invalid')

    await page.goto('/service')
    await expect(page.getByRole('heading', { name: '数据服务配置与发布' })).toBeVisible()

    await page.getByRole('button', { name: '新建服务草稿' }).click()
    await page.getByLabel('服务编码').fill(serviceCode)
    await page.getByLabel('服务名称').fill(`Playwright 服务 ${serviceCode}`)
    await page.getByLabel('SQL 模板').fill(
      'select customer_id as customerId, order_amount as orderAmount, active as active from customer_order where customer_id = :customerId and active = :active',
    )
    await page.getByRole('button', { name: '新增来源' }).click()
    await page.locator('#sources_0_catalogId').fill(String(baseConnection.catalogs[0].id))
    await page.locator('#sources_0_sourceAlias').fill('customer')
    await page.locator('#sources_0_sourceValue').fill('customer_order')
    await page.getByRole('button', { name: '新增参数' }).click()
    await page.locator('#params_0_paramName').fill('customerId')
    await page.locator('#params_0_displayName').fill('客户号')
    await page.locator('#params_0_sqlPlaceholder').fill('customerId')
    await page.locator('#params_0_sortOrder').fill('1')
    await page.getByRole('button', { name: '新增参数' }).click()
    await page.locator('#params_1_paramName').fill('active')
    await page.locator('#params_1_displayName').fill('激活状态')
    await page.locator('#params_1_sqlPlaceholder').fill('active')
    await chooseSelectOption(page, '#params_1_paramType', '布尔')
    await page.locator('#params_1_sortOrder').fill('2')
    await page.getByRole('button', { name: '新增字段' }).click()
    await page.locator('#fields_0_sourceAlias').fill('customer')
    await page.locator('#fields_0_sourceColumn').fill('customer_id')
    await page.locator('#fields_0_fieldName').fill('customerId')
    await page.locator('#fields_0_displayName').fill('客户号')
    await chooseSelectOption(page, '#fields_0_fieldType', '数字')
    await page.locator('#fields_0_sortOrder').fill('1')
    await page.getByRole('button', { name: /保存草稿/ }).click()

    await expect(page.locator('.ant-message-notice').getByText('服务草稿已创建')).toBeVisible()
    const createdCell = page.locator('.ant-table-tbody').first().getByRole('cell', { name: serviceCode, exact: true })
    await expect(createdCell).toBeVisible()
    await createdCell.click()
    await expect(page.getByText('草稿').first()).toBeVisible()

    const publishResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/admin/service-definitions/') &&
        response.url().endsWith('/publish') &&
        response.status() < 500,
    )
    await page.getByLabel(`发布服务 ${serviceCode}`).click()
    const publishResponse = await publishResponsePromise
    expect(publishResponse.status()).toBe(200)
    await expect(page.getByText('已发布').first()).toBeVisible()

    const disableResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().includes('/api/admin/service-definitions/') &&
        response.url().endsWith('/status') &&
        response.status() < 500,
    )
    await page.getByLabel(`停用服务 ${serviceCode}`).click()
    await page.getByRole('button', { name: /确\s*定/ }).click()
    const disableResponse = await disableResponsePromise
    expect(disableResponse.status()).toBe(200)
    await expect(page.getByText('已停用').first()).toBeVisible()

    const invalidDraft = await createSimpleQueryDraft(request, {
      serviceCode: invalidServiceCode,
      serviceName: `Invalid ${invalidServiceCode}`,
      connectionId: baseConnection.connection.id!,
      catalogId: baseConnection.catalogs[0].id!,
      params: [],
      fields: [],
    })

    await page.reload()
    const invalidCell = page.locator('.ant-table-tbody').first().getByRole('cell', { name: invalidServiceCode, exact: true })
    await expect(invalidCell).toBeVisible()
    await invalidCell.click()
    await page.getByLabel(`发布服务 ${invalidServiceCode}`).click()
    await expect(page.locator('.ant-message-notice').getByText('发布失败: 未配置参数')).toBeVisible()
    await expect(page.getByText('草稿').first()).toBeVisible()

  })

  test('联邦平台对非法 SQL 和来源不一致展示明确错误', async ({ page, request }) => {
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

    await page.goto('/federation')
    await expect(page.getByRole('heading', { name: '联邦 SQL 平台' })).toBeVisible()
    await page.locator('.ant-table-tbody').first().getByRole('cell', { name: draft.definition.serviceCode, exact: true }).click()
    await expect(page.getByLabel('联邦 SQL')).toBeVisible()

    await page.getByLabel('联邦 SQL').fill('SELECT FROM')
    await page.getByRole('button', { name: '保存并校验' }).click()
    await expect(page.locator('.ant-message-notice').getByText(/联邦 SQL 解析失败/).first()).toBeVisible()

    await page.getByLabel('联邦 SQL').fill(
      'SELECT wrong_alias.customer_id FROM wrong_alias WHERE wrong_alias.customer_id = :customerId',
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
