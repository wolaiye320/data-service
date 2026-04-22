import { expect, test } from '@playwright/test'
import { createPostgresConnection, createSimpleQueryDraft, publishService, uniqueCode } from './support/admin-api'

test.describe('第一阶段前端联调', () => {
  test('关键页面可加载并展示真实后端数据', async ({ page, request }) => {
    const failedResponses: Array<{ url: string; status: number }> = []
    const consoleErrors: string[] = []
    const connection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_query_list_conn'),
    })
    const draft = await createSimpleQueryDraft(request, {
      serviceCode: uniqueCode('pw_query_list_service'),
      connectionId: connection.connection.id!,
      catalogId: connection.catalogs[0].id!,
    })
    await publishService(request, draft.definition.id!)

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
        if (text.includes('There may be circular references')) {
          return
        }
        if (text.includes('[antd: Table] `index` parameter of `rowKey` function is deprecated')) {
          return
        }
        consoleErrors.push(text)
      }
    })

    await page.goto('/datasource')
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('heading', { name: '数据源连接' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '连接编码' })).toBeVisible()
    await expect(page.getByRole('cell', { name: connection.connection.connectionCode, exact: true })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: /目标库管理/ })).toHaveCount(0)

    await page.getByRole('menuitem', { name: /数据服务/ }).click()
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('heading', { name: '数据服务' })).toBeVisible()
    await page.getByPlaceholder('搜索服务...').fill(draft.definition.serviceCode)
    await expect(page.getByRole('cell', { name: draft.definition.serviceCode, exact: true })).toBeVisible()

    await page.getByRole('menuitem', { name: /发布管理/ }).click()
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('heading', { name: '发布管理' })).toBeVisible()
    await expect(page.getByRole('cell', { name: draft.definition.serviceCode, exact: true })).toBeVisible()

    await page.getByRole('menuitem', { name: /缓存策略/ }).click()
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('heading', { name: '缓存策略' })).toBeVisible()

    await page.getByRole('menuitem', { name: /审计日志/ }).click()
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()

    expect(failedResponses).toEqual([])
    expect(consoleErrors).toEqual([])
  })

  test('数据源连接页在常见笔记本宽度下展示列表并通过详情按钮打开抽屉', async ({ page, request }) => {
    const connection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_split_conn'),
    })
    const connectionCode = connection.connection.connectionCode

    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto('/datasource')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: '数据源连接' })).toBeVisible()
    const listCard = page.getByTestId('datasource-list-card')
    const connectionRow = listCard
      .locator('.ant-table-tbody tr:not(.ant-table-measure-row)')
      .filter({ hasText: connectionCode })
      .first()
    await expect(connectionRow).toBeVisible()

    const detailTrigger = connectionRow.getByLabel('查看连接详情')
    await expect(detailTrigger).toBeVisible()
    await detailTrigger.click()

    const detailDrawer = page.getByRole('dialog', { name: '连接详情' })
    await expect(detailDrawer).toBeVisible()
    await expect(detailDrawer.getByText(connectionCode)).toBeVisible()

    const viewport = page.viewportSize()
    const listBox = await listCard.boundingBox()
    const detailBox = await detailDrawer.boundingBox()

    expect(viewport).not.toBeNull()
    expect(listBox).not.toBeNull()
    expect(detailBox).not.toBeNull()

    expect(listBox!.y).toBeLessThan((viewport?.height ?? 0) / 2)
    expect(detailBox!.x).toBeGreaterThan(listBox!.x + 120)
  })

  test('统一查询调试页可通过前端代理完成真实查询', async ({ page, request }) => {
    const failedResponses: Array<{ url: string; status: number }> = []
    const consoleErrors: string[] = []
    const connection = await createPostgresConnection(request, {
      connectionCode: uniqueCode('pw_query_exec_conn'),
    })
    const serviceCode = uniqueCode('pw_query_exec_service')
    const draft = await createSimpleQueryDraft(request, {
      serviceCode,
      connectionId: connection.connection.id!,
      catalogId: connection.catalogs[0].id!,
      sqlTemplate:
        "select customer_id as customer_customer_id, 'Y' as customer_demovalue from customer_order where customer_id = /* customerId */0 and active = /* active */false",
      fields: [
        {
          sourceAlias: 'customer',
          sourceColumn: 'customer_id',
          fieldName: 'customerId',
          displayName: '客户号',
          fieldType: 'LONG',
          sortOrder: 1,
          primaryKey: true,
          joinKey: true,
        },
        {
          sourceAlias: 'customer',
          sourceColumn: 'demoValue',
          fieldName: 'demoValue',
          displayName: '演示值',
          fieldType: 'STRING',
          sortOrder: 2,
          primaryKey: false,
          joinKey: false,
        },
      ],
    })
    await publishService(request, draft.definition.id!)

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
        if (text.includes('There may be circular references')) {
          return
        }
        if (text.includes('[antd: Table] `index` parameter of `rowKey` function is deprecated')) {
          return
        }
        consoleErrors.push(text)
      }
    })

    await page.goto('/query-debug')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: '统一查询调试' })).toBeVisible()
    await page.getByLabel('服务编码').fill(serviceCode)
    await page.getByLabel('traceId').fill('pw-e2e-20260404')
    await page.getByLabel('params JSON').fill('{\n  "customerId": 1001,\n  "active": true\n}')
    await page.getByRole('button', { name: '执行调试' }).click()

    await expect(page.getByRole('columnheader', { name: 'demoValue' })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Y', exact: true })).toBeVisible()
    await expect(page.getByText(/"code":\s*"OK"/)).toBeVisible()
    await expect(page.getByText(new RegExp(`"serviceCode":\\s*"${serviceCode}"`))).toBeVisible()

    expect(failedResponses).toEqual([])
    expect(consoleErrors).toEqual([])
  })
})
