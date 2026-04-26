import { expect, test } from '@playwright/test'

async function fillConnectionForm(
  page: import('@playwright/test').Page,
  payload: {
    connectionCode: string
    connectionName: string
    host: string
    port: string
    username: string
    password: string
    databaseName: string
    remark: string
  },
) {
  await page.getByLabel('连接编码').fill(payload.connectionCode)
  await page.getByLabel('连接名称').fill(payload.connectionName)
  await page.getByLabel('主机').fill(payload.host)
  await page.getByLabel('端口').fill(payload.port)
  await page.getByLabel('用户名').fill(payload.username)
  await page.getByLabel('密码').fill(payload.password)
  await page.getByLabel('数据库/服务名').fill(payload.databaseName)
  await page.getByLabel('备注').fill(payload.remark)
}

test('连接列表、新增和编辑闭环并断言接口成功', async ({ page }) => {
  const apiResponses = {
    list: [] as number[],
    create: [] as number[],
    update: [] as number[],
  }
  page.on('response', (response) => {
    if (!response.url().includes('/api/admin/connections')) {
      return
    }
    const method = response.request().method()
    if (method === 'GET') {
      apiResponses.list.push(response.status())
      return
    }
    if (method === 'POST') {
      apiResponses.create.push(response.status())
      return
    }
    if (method === 'PUT' && !response.url().includes('/status')) {
      apiResponses.update.push(response.status())
    }
  })

  const suffix = Date.now().toString().slice(-6)
  const connectionCode = `E2E_PG_${suffix}`
  const connectionName = `E2E PG ${suffix}`
  const updatedConnectionName = `E2E PG Updated ${suffix}`

  await page.goto('/connections')
  await expect(page.getByTestId('connections-page')).toBeVisible()
  await expect(page.getByRole('heading', { name: '数据源连接' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新增连接' })).toBeVisible()
  await expect.poll(() => apiResponses.list.some((status) => status >= 200 && status < 300)).toBeTruthy()

  await page.getByRole('button', { name: '新增连接' }).click()
  await expect(page.getByText('新增数据源连接')).toBeVisible()
  await fillConnectionForm(page, {
    connectionCode,
    connectionName,
    host: '127.0.0.1',
    port: '5432',
    username: 'postgres',
    password: 'postgres',
    databaseName: 'data_service',
    remark: 'e2e create connection',
  })
  await page.getByRole('button', { name: /保\s*存/ }).click()

  await expect.poll(() => apiResponses.create.some((status) => status >= 200 && status < 300)).toBeTruthy()
  await expect(page.getByText(connectionCode)).toBeVisible()
  await expect(page.getByText(connectionName)).toBeVisible()

  const connectionRow = page.locator('tr', { hasText: connectionCode }).first()
  await connectionRow.getByTestId(/connection-edit-/).click()
  await expect(page.getByText('编辑数据源连接')).toBeVisible()
  await page.getByLabel('连接名称').fill(updatedConnectionName)
  await page.getByLabel('主机').fill('127.0.0.1')
  await page.getByLabel('端口').fill('5432')
  await page.getByLabel('用户名').fill('postgres')
  await page.getByLabel('密码').fill('postgres')
  await page.getByLabel('数据库/服务名').fill('data_service')
  await page.getByLabel('备注').fill('e2e update connection')
  await page.getByRole('button', { name: /保\s*存/ }).click()

  await expect.poll(() => apiResponses.update.some((status) => status >= 200 && status < 300)).toBeTruthy()
  await expect(page.getByText(updatedConnectionName)).toBeVisible()
})

test('连接测试、启停和失败提示闭环并断言接口成功', async ({ page }) => {
  test.setTimeout(60_000)

  const apiResponses = {
    list: [] as number[],
    create: [] as number[],
    test: [] as number[],
    status: [] as number[],
  }
  page.on('response', (response) => {
    if (!response.url().includes('/api/admin/connections')) {
      return
    }
    const method = response.request().method()
    if (method === 'GET') {
      apiResponses.list.push(response.status())
      return
    }
    if (method === 'POST' && response.url().includes('/test')) {
      apiResponses.test.push(response.status())
      return
    }
    if (method === 'POST') {
      apiResponses.create.push(response.status())
      return
    }
    if (method === 'PUT' && response.url().includes('/status')) {
      apiResponses.status.push(response.status())
    }
  })

  const suffix = Date.now().toString().slice(-6)
  const connectionCode = `E2E_PG_SWITCH_${suffix}`
  const connectionName = `E2E PG Switch ${suffix}`
  const invalidCode = `E2E_BAD_PG_${suffix}`

  await page.goto('/connections')
  await expect(page.getByTestId('connections-page')).toBeVisible()
  await expect.poll(() => apiResponses.list.some((status) => status >= 200 && status < 300)).toBeTruthy()

  await page.getByRole('button', { name: '新增连接' }).click()
  await expect(page.getByText('新增数据源连接')).toBeVisible()
  await fillConnectionForm(page, {
    connectionCode,
    connectionName,
    host: '127.0.0.1',
    port: '5432',
    username: 'postgres',
    password: 'postgres',
    databaseName: 'data_service',
    remark: 'e2e switch connection',
  })
  await page.getByRole('button', { name: /保\s*存/ }).click()

  await expect.poll(() => apiResponses.create.some((status) => status >= 200 && status < 300)).toBeTruthy()
  await expect(page.getByText(connectionCode)).toBeVisible()

  let connectionRow = page.locator('tr', { hasText: connectionCode }).first()
  await connectionRow.getByTestId(/connection-test-/).click()
  await expect.poll(() => apiResponses.test.some((status) => status >= 200 && status < 300)).toBeTruthy()
  await expect(page.getByText('连接成功')).toBeVisible()

  await connectionRow.getByTestId(/connection-status-/).click()
  await page.getByRole('button', { name: /确\s*定/ }).click()
  await expect.poll(() => apiResponses.status.filter((status) => status >= 200 && status < 300).length).toBeGreaterThanOrEqual(1)
  await expect(page.locator('tr', { hasText: connectionCode }).first()).toContainText('停用')

  connectionRow = page.locator('tr', { hasText: connectionCode }).first()
  await connectionRow.getByTestId(/connection-status-/).click()
  await page.getByRole('button', { name: /确\s*定/ }).click()
  await expect.poll(() => apiResponses.status.filter((status) => status >= 200 && status < 300).length).toBeGreaterThanOrEqual(2)
  await expect(page.locator('tr', { hasText: connectionCode }).first()).toContainText('启用')

  await page.getByRole('button', { name: '新增连接' }).click()
  await expect(page.getByText('新增数据源连接')).toBeVisible()
  await fillConnectionForm(page, {
    connectionCode: invalidCode,
    connectionName: `E2E Bad PG ${suffix}`,
    host: '127.0.0.1',
    port: '1',
    username: 'postgres',
    password: 'postgres',
    databaseName: 'data_service',
    remark: 'e2e bad connection',
  })
  await page.getByRole('button', { name: /保\s*存/ }).click()
  await expect(page.getByText(/数据源连接测试失败|请求处理失败/)).toBeVisible()
})
