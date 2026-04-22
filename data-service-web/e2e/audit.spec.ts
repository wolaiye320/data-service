import { expect, test } from '@playwright/test'

test.describe('审计日志页面', () => {
  test('管理员可检索审计日志并查看详情，首屏接口成功', async ({ page, request }) => {
    test.setTimeout(60_000)
    const failedResponses: Array<{ url: string; status: number }> = []
    const consoleErrors: string[] = []
    const baselineResponse = await request.get('/api/platform/baseline', {
      headers: {
        'X-Operator': 'audit-e2e-admin',
        'X-Operator-Role': 'ADMIN',
      },
    })
    expect(baselineResponse.ok()).toBeTruthy()

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
        if (text.includes('antd v5 support React is 16 ~ 18')) {
          return
        }
        consoleErrors.push(text)
      }
    })

    const listResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/admin/audit-logs?pageNo=1&pageSize=10') &&
        response.status() === 200,
    )
    await page.goto('/audit')
    const listResponse = await listResponsePromise
    expect(listResponse.status()).toBe(200)

    await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()
    await expect(page.getByText(/检索结果 \(\d+\)/)).toBeVisible()

    await page.getByLabel('操作人').fill('audit-e2e-admin')
    await page.getByLabel('事件类型').fill('VIEW_PLATFORM_BASELINE')
    const filterResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/admin/audit-logs?') &&
        response.url().includes('operator=audit-e2e-admin') &&
        response.url().includes('eventType=VIEW_PLATFORM_BASELINE') &&
        response.status() === 200,
    )
    await page.getByRole('button', { name: /查\s*询/ }).click()
    const filterResponse = await filterResponsePromise
    expect(filterResponse.status()).toBe(200)

    await expect(page.getByRole('cell', { name: 'VIEW_PLATFORM_BASELINE', exact: true }).first()).toBeVisible()
    const detailResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        /\/api\/admin\/audit-logs\/\d+$/.test(response.url()) &&
        response.status() === 200,
    )
    await page.getByLabel(/查看审计详情/).first().click()
    const detailResponse = await detailResponsePromise
    expect(detailResponse.status()).toBe(200)

    await expect(page.getByRole('dialog', { name: '审计详情' })).toBeVisible()
    await expect(page.getByText('结构化详情', { exact: true })).toBeVisible()
    await expect(page.getByText('VIEW_PLATFORM_BASELINE').last()).toBeVisible()

    expect(consoleErrors).toEqual([])
    expect(failedResponses).toEqual([])
  })

  test('非管理员访问审计日志页面时展示明确错误，不静默吞错', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('data-service.operator', 'caller-audit')
      window.localStorage.setItem('data-service.operatorRole', 'CALLER')
    })

    const failedResponses: Array<{ url: string; status: number }> = []
    page.on('response', (response) => {
      if (response.url().includes('/api/') && response.status() >= 400) {
        failedResponses.push({ url: response.url(), status: response.status() })
      }
    })

    await page.goto('/audit')
    await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()
    await expect(page.getByText('审计日志加载失败')).toBeVisible()
    await expect(page.getByText('当前角色无权访问接口: /api/admin/audit-logs').first()).toBeVisible()
    expect(failedResponses.some((item) => item.url.includes('/api/admin/audit-logs') && item.status === 403)).toBe(true)
  })
})
