import { expect, test } from '@playwright/test'
import { createFederatedDraft, createPostgresConnection, saveFederatedSql, uniqueCode } from './support/admin-api'

test.describe('统一数据服务页联邦能力前端联调', () => {
  test('数据服务页可预览执行联邦 SQL 并展示校验、计划与发布结果', async ({ page, request }) => {
    test.setTimeout(120_000)
    const failedResponses: Array<{ url: string; status: number }> = []
    const consoleErrors: string[] = []
    const primary = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_page_primary'),
    })
    const child = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_page_child'),
    })
    const serviceCode = uniqueCode('federated_pw')
    const federatedSql =
      'select pg_customer.customer_id, pg_customer.customer_name, pg_order.order_amount from pg_customer join pg_order on pg_customer.customer_id = pg_order.customer_id where pg_customer.customer_id = :customerId'

    const draft = await createFederatedDraft(request, {
      serviceCode,
      serviceName: '联邦 Playwright 联调服务',
      primaryConnectionId: primary.connection.id!,
      primaryCatalogId: primary.catalogs[0].id!,
      childConnectionId: child.connection.id!,
      childCatalogId: child.catalogs[0].id!,
      sourceJoinKey: 'customer_id',
      fieldJoinKeys: {
        customerId: false,
        customerName: false,
        orderAmount: false,
      },
    })
    await saveFederatedSql(request, draft.definition.id!, federatedSql, 'Playwright 联邦 SQL 联调草稿')

    page.on('response', (response) => {
      if (response.url().includes('/api/') && response.status() >= 400) {
        failedResponses.push({ url: response.url(), status: response.status() })
      }
    })
    page.on('console', (message) => {
      const text = message.text()
      if (message.type() === 'error') {
        if (text.includes('Failed to load resource') && text.includes('favicon.ico')) {
          return
        }
        if (text.includes('Instance created by `useForm`')) {
          return
        }
        if (text.includes('antd v5 support React is 16 ~ 18')) {
          return
        }
        consoleErrors.push(text)
      }
    })

    const listResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/admin/service-definitions') &&
        response.status() === 200,
    )
    await page.goto('/service')
    const listResponse = await listResponsePromise
    expect(listResponse.status()).toBe(200)

    await expect(page.locator('.module-hero').getByText('数据服务')).toBeVisible()
    await expect(page.getByText('服务列表')).toBeVisible()

    const serviceRow = page.locator('.ant-table-tbody').first().getByRole('row', {
      name: new RegExp(`${serviceCode}\\s+联邦 Playwright 联调服务`),
    })
    await expect(serviceRow).toBeVisible()

    const workspaceResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes(`/api/admin/service-definitions/${draft.definition.id}/federated-metadata`) &&
        response.status() === 200,
    )
    await serviceRow.click()
    const workspaceResponse = await workspaceResponsePromise
    expect(workspaceResponse.status()).toBe(200)

    await expect(page.getByLabel('联邦 SQL')).toHaveValue(federatedSql)
    await expect(page.getByText(/^校验日志 \(4\)$/)).toBeVisible()
    await expect(page.getByText(/^计划与诊断 \(3\)$/)).toBeVisible()

    const previewResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes(`/api/admin/service-definitions/${draft.definition.id}/federated-preview`) &&
        response.status() === 200,
    )
    await page.getByLabel('预览参数 JSON').fill('{\n  "customerId": 3001\n}')
    await page.getByRole('button', { name: '预览执行' }).click()
    const previewResponse = await previewResponsePromise
    expect(previewResponse.status()).toBe(200)

    await expect(page.locator('.ant-message-notice').getByText('联邦 SQL 预览执行完成')).toBeVisible()
    await expect(page.locator('.ant-card-head-title', { hasText: '联邦 SQL 预览执行' })).toBeVisible()
    await expect(page.locator('.ant-card-head-title', { hasText: '执行摘要' })).toBeVisible()
    await expect(page.getByRole('cell', { name: '3001', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Alice', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: '128', exact: true })).toBeVisible()
    await expect(page.getByText(/"rowCount": 1/)).toBeVisible()
    await expect(page.getByText(/"executedStageCount": 2/)).toBeVisible()

    await page.getByText(/^校验日志 \(4\)$/).click()
    await expect(page.getByRole('cell', { name: 'PARSE', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'SEMANTIC', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'CAPABILITY', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'PLAN', exact: true })).toBeVisible()

    await page.getByText(/^计划与诊断 \(3\)$/).click()
    await expect(page.getByText('阶段 · LOGICAL')).toBeVisible()
    await expect(page.getByText('阶段 · OPTIMIZED')).toBeVisible()
    await expect(page.getByText('阶段 · SPLIT')).toBeVisible()

    const publishResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes(`/api/admin/service-definitions/${draft.definition.id}/publish`) &&
        response.status() === 200,
    )
    await page.getByLabel(`发布服务 ${serviceCode}`).click()
    const publishResponse = await publishResponsePromise
    expect(publishResponse.status()).toBe(200)

    await expect(page.locator('.ant-message-notice').getByText('服务已发布')).toBeVisible()
    await expect(page.getByText('已发布').first()).toBeVisible()
    await expect(page.getByText(serviceCode, { exact: true }).first()).toBeVisible()

    expect(consoleErrors).toEqual([])
    expect(failedResponses).toEqual([])
  })
})
