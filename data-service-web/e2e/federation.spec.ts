import { expect, test } from '@playwright/test'
import { createFederatedDraft, createPostgresConnection, uniqueCode } from './support/admin-api'

async function chooseSelectOption(page: Parameters<typeof test>[0]['page'], inputSelector: string, optionTitle: string) {
  const input = page.locator(inputSelector)
  await input.focus()
  await input.press('ArrowDown')
  await page.locator(`.ant-select-dropdown:visible [title="${optionTitle}"]`).first().click()
}

test.describe('第二阶段联邦平台前端联调', () => {
  test('联邦 SQL 平台可创建草稿、保存联邦 SQL 并展示校验与计划结果', async ({ page, request }) => {
    test.setTimeout(60_000)
    const failedResponses: Array<{ url: string; status: number }> = []
    const consoleErrors: string[] = []
    const primary = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_page_primary'),
    })
    const child = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_fed_page_child'),
    })
    const serviceCode = uniqueCode('federated_pw')

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

    await page.goto('/federation')
    await expect(page.getByRole('heading', { name: '联邦 SQL 平台' })).toBeVisible()
    await expect(page.getByText('联邦服务列表')).toBeVisible()

    await page.getByRole('button', { name: '新建联邦服务' }).click()
    await expect(page.getByText('新建联邦服务草稿')).toBeVisible()

    await page.getByLabel('服务编码').fill(serviceCode)
    await page.getByLabel('服务名称').fill('联邦 Playwright 联调服务')

    await page.getByRole('button', { name: '新增来源' }).click()
    await page.locator('#sources_0_catalogId').fill(String(primary.catalogs[0].id))
    await page.locator('#sources_0_sourceAlias').fill('pg_customer')
    await page.locator('#sources_0_sourceValue').fill('customer_base')

    await page.getByRole('button', { name: '新增来源' }).click()
    await page.locator('#sources_1_catalogId').fill(String(child.catalogs[0].id))
    await page.locator('#sources_1_sourceAlias').fill('pg_order')
    await page.locator('#sources_1_sourceValue').fill('customer_order_ext')

    await page.getByRole('button', { name: '新增参数' }).click()
    await page.locator('#params_0_paramName').fill('customerId')
    await page.locator('#params_0_displayName').fill('客户号')
    await chooseSelectOption(page, '#params_0_paramType', '长整型')
    await page.locator('#params_0_sqlPlaceholder').fill('customerId')

    await page.getByRole('button', { name: '新增字段' }).click()
    await page.locator('#fields_0_sourceAlias').fill('pg_customer')
    await page.locator('#fields_0_sourceColumn').fill('customer_id')
    await page.locator('#fields_0_fieldName').fill('customerId')
    await page.locator('#fields_0_displayName').fill('客户号')
    await chooseSelectOption(page, '#fields_0_fieldType', '长整型')

    await page.getByRole('button', { name: '新增字段' }).click()
    await page.locator('#fields_1_sourceAlias').fill('pg_customer')
    await page.locator('#fields_1_sourceColumn').fill('customer_name')
    await page.locator('#fields_1_fieldName').fill('customerName')
    await page.locator('#fields_1_displayName').fill('客户名称')

    await page.getByRole('button', { name: '新增字段' }).click()
    await page.locator('#fields_2_sourceAlias').fill('pg_order')
    await page.locator('#fields_2_sourceColumn').fill('order_amount')
    await page.locator('#fields_2_fieldName').fill('orderAmount')
    await page.locator('#fields_2_displayName').fill('订单金额')
    await chooseSelectOption(page, '#fields_2_fieldType', '小数')

    await page.getByRole('button', { name: /保存草稿/ }).click()
    await expect(page.locator('.ant-message-notice').getByText('联邦服务草稿已创建')).toBeVisible()
    const createdCell = page.locator('.ant-table-tbody').first().getByRole('cell', { name: serviceCode, exact: true })
    await expect(createdCell).toBeVisible()

    await createdCell.click()
    await expect(page.getByLabel('联邦 SQL')).toBeVisible()

    await page.getByLabel('联邦 SQL').fill(
      'SELECT pg_customer.customer_id, pg_customer.customer_name, pg_order.order_amount FROM pg_customer JOIN pg_order ON pg_customer.customer_id = pg_order.customer_id WHERE pg_customer.customer_id = :customerId',
    )
    await page.getByLabel('草稿说明').fill('Playwright 联邦 SQL 联调草稿')
    await page.getByRole('button', { name: '保存并校验' }).click()

    await expect(page.locator('.ant-message-notice').getByText('联邦 SQL 草稿已保存并完成校验规划')).toBeVisible()
    await expect(page.getByText(/^校验日志 \(\d+\)$/)).toBeVisible()
    await expect(page.getByText(/^计划与诊断 \(\d+\)$/)).toBeVisible()

    await page.getByText(/^校验日志 \(\d+\)$/).click()
    const workspaceTableBody = page.locator('.federation-stack .ant-table-tbody').first()
    await expect(workspaceTableBody.getByRole('cell', { name: 'PARSE', exact: true })).toBeVisible()
    await expect(workspaceTableBody.getByRole('cell', { name: 'SEMANTIC', exact: true })).toBeVisible()
    await expect(workspaceTableBody.getByRole('cell', { name: 'CAPABILITY', exact: true })).toBeVisible()
    await expect(workspaceTableBody.getByRole('cell', { name: 'PLAN', exact: true })).toBeVisible()

    await page.getByText(/^计划与诊断 \(\d+\)$/).click()
    await expect(page.getByText('阶段 · LOGICAL')).toBeVisible()
    await expect(page.getByText('阶段 · OPTIMIZED')).toBeVisible()
    await expect(page.getByText('阶段 · SPLIT')).toBeVisible()

    await page.getByLabel(`发布联邦服务 ${serviceCode}`).click()
    await expect(page.locator('.ant-message-notice').getByText('联邦服务已发布')).toBeVisible()
    await expect(page.getByText('已发布').first()).toBeVisible()

    expect(consoleErrors).toEqual([])
    expect(failedResponses).toEqual([])
  })
})
