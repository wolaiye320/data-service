import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Descriptions, Form, Input, Segmented, Space, Spin, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useAppFeedback } from '../../components/useAppFeedback'
import { getJson, postJson, resolveErrorMessage } from '../../services/http'
import type { ApiResponse } from '../../services/http'
import type { QueryDebugRequest, ServiceDefinition } from '../../types/admin'

type QueryFormValue = {
  serviceCode: string
  paramsText: string
  batchParamsText: string
  traceId: string
  operator: string
}

type QueryMode = 'single' | 'batch'

const DEFAULT_FORM: QueryFormValue = {
  serviceCode: '',
  paramsText: '{\n  "id": 1\n}',
  batchParamsText: '[\n  {\n    "id": 1\n  },\n  {\n    "id": 2\n  }\n]',
  traceId: '',
  operator: 'web-admin',
}

function buildColumns(rows: Array<Record<string, unknown>>): ColumnsType<Record<string, unknown>> {
  const keys = Array.from(
    rows.reduce((set, row) => {
      Object.keys(row).forEach((key) => set.add(key))
      return set
    }, new Set<string>()),
  )

  return keys.map((key) => ({
    title: key,
    dataIndex: key,
    key,
    render: (value: unknown) => formatCellValue(value),
  }))
}

function formatCellValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  if (typeof value === 'object') {
    return <Typography.Text code>{JSON.stringify(value)}</Typography.Text>
  }
  return String(value)
}

export function QueryDebugModulePage() {
  const { message } = useAppFeedback()
  const [mode, setMode] = useState<QueryMode>('single')
  const [loadingServices, setLoadingServices] = useState(true)
  const [executing, setExecuting] = useState(false)
  const [services, setServices] = useState<ServiceDefinition[]>([])
  const [result, setResult] = useState<ApiResponse<unknown> | null>(null)
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([])
  const [batchRows, setBatchRows] = useState<Array<Record<string, unknown>>>([])
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [form] = Form.useForm<QueryFormValue>()

  useEffect(() => {
    form.setFieldsValue(DEFAULT_FORM)
    void loadServices()
  }, [form])

  async function loadServices() {
    setLoadingServices(true)
    try {
      const response = await getJson<ServiceDefinition[]>('/api/admin/service-definitions')
      const published = response.data.filter((item) => item.status === 'PUBLISHED')
      setServices(published)
      if (published.length > 0) {
        form.setFieldValue('serviceCode', published[0].serviceCode)
      }
    } finally {
      setLoadingServices(false)
    }
  }

  const rowColumns = useMemo(() => buildColumns(rows), [rows])
  const batchColumns = useMemo(() => buildColumns(batchRows), [batchRows])

  async function handleSubmit(values: QueryFormValue) {
    setExecuting(true)
    try {
      const request: QueryDebugRequest = {
        serviceCode: values.serviceCode.trim(),
        params: mode === 'single' ? parseJsonObject(values.paramsText, 'params') : undefined,
        batchParams: mode === 'batch' ? parseJsonArray(values.batchParamsText, 'batchParams') : undefined,
        context: {
          traceId: values.traceId.trim() || undefined,
          operator: values.operator.trim() || undefined,
        },
      }
      const response = await postJson<unknown, QueryDebugRequest>('/api/data-services/query', request)
      setErrorMessage(null)
      setResult(response)
      if (mode === 'batch') {
        const items = Array.isArray(response.data) ? (response.data as Array<Record<string, unknown>>) : []
        setBatchRows(items)
        setRows([])
      } else {
        const items = Array.isArray(response.data) ? (response.data as Array<Record<string, unknown>>) : []
        setRows(items)
        setBatchRows([])
      }
      message.success('统一查询执行成功')
    } catch (error) {
      setResult(null)
      setRows([])
      setBatchRows([])
      const messageText = resolveErrorMessage(error)
      setErrorMessage(messageText)
      message.error(messageText)
    } finally {
      setExecuting(false)
    }
  }

  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={5}>统一查询调试</Typography.Title>
        </div>
        <Segmented<QueryMode>
          value={mode}
          onChange={(value) => setMode(value)}
          options={[
            { label: '单次查询', value: 'single' },
            { label: '批量查询', value: 'batch' },
          ]}
        />
      </div>

      <div className="module-grid">
        <Card title="请求配置" className="li-page-main-card">
          {loadingServices ? (
            <Spin />
          ) : (
            <Form<QueryFormValue> form={form} layout="vertical" onFinish={(values) => void handleSubmit(values)}>
              <div className="form-grid form-grid-two">
                <Form.Item label="服务编码" name="serviceCode" rules={[{ required: true }]}>
                  <Input list="service-code-options" placeholder="选择或输入已发布服务编码" />
                </Form.Item>
                <Form.Item label="调用方" name="operator">
                  <Input placeholder="默认 web-admin" />
                </Form.Item>
                <Form.Item label="traceId" name="traceId">
                  <Input placeholder="可选，用于串联排查" />
                </Form.Item>
              </div>

              <datalist id="service-code-options">
                {services.map((item) => (
                  <option key={item.serviceCode} value={item.serviceCode}>
                    {item.serviceName}
                  </option>
                ))}
              </datalist>

              {mode === 'single' ? (
                <Form.Item label="params JSON" name="paramsText" rules={[{ required: true }]}>
                  <Input.TextArea rows={12} />
                </Form.Item>
              ) : (
                <Form.Item label="batchParams JSON" name="batchParamsText" rules={[{ required: true }]}>
                  <Input.TextArea rows={12} />
                </Form.Item>
              )}

              <Space>
                <Button type="primary" loading={executing} onClick={() => void form.submit()}>
                  执行调试
                </Button>
                <Button
                  onClick={() => {
                    form.setFieldsValue(DEFAULT_FORM)
                    setResult(null)
                    setRows([])
                    setBatchRows([])
                    setErrorMessage(null)
                  }}
                >
                  重置
                </Button>
              </Space>
            </Form>
          )}
        </Card>

        <Card title="执行结果" className="li-page-main-card">
          {errorMessage ? (
            <Alert type="error" showIcon message="执行失败" description={errorMessage} />
          ) : !result ? (
            <Alert type="info" showIcon message="填写服务编码和参数后执行调试" />
          ) : (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions size="small" column={2} bordered>
                <Descriptions.Item label="响应码">{result.code}</Descriptions.Item>
                <Descriptions.Item label="消息">{result.message}</Descriptions.Item>
                <Descriptions.Item label="服务编码">{String(result.meta?.serviceCode ?? '-')}</Descriptions.Item>
                <Descriptions.Item label="版本">{String(result.meta?.version ?? '-')}</Descriptions.Item>
                <Descriptions.Item label="耗时">{String(result.meta?.durationMs ?? '-')} ms</Descriptions.Item>
                <Descriptions.Item label="返回行数">{String(result.meta?.rowCount ?? '-')}</Descriptions.Item>
                <Descriptions.Item label="批量模式">{String(result.meta?.batch ?? false)}</Descriptions.Item>
                <Descriptions.Item label="批量数量">{String(result.meta?.batchSize ?? '-')}</Descriptions.Item>
                <Descriptions.Item label="缓存命中">{String(result.meta?.cacheHit ?? false)}</Descriptions.Item>
                <Descriptions.Item label="命中次数">{String(result.meta?.cacheHitCount ?? 0)}</Descriptions.Item>
                <Descriptions.Item label="调用方">{String(result.meta?.operator ?? '-')}</Descriptions.Item>
                <Descriptions.Item label="traceId">{String(result.meta?.traceId ?? '-')}</Descriptions.Item>
              </Descriptions>

              {mode === 'batch' ? (
                <Table<Record<string, unknown>>
                  rowKey={(record) => String(record.index ?? Math.random())}
                  size="small"
                  pagination={false}
                  dataSource={batchRows}
                  columns={batchColumns.length === 0 ? [{ title: '结果', dataIndex: 'rows', render: formatCellValue }] : batchColumns}
                  scroll={{ x: 'max-content' }}
                />
              ) : (
                <Table<Record<string, unknown>>
                  rowKey={(_, index) => String(index)}
                  size="small"
                  pagination={false}
                  dataSource={rows}
                  columns={rowColumns.length === 0 ? [{ title: '结果', dataIndex: 'value', render: formatCellValue }] : rowColumns}
                  locale={{ emptyText: '查询成功但无返回数据' }}
                  scroll={{ x: 'max-content' }}
                />
              )}

              <Card size="small" title="原始响应">
                <Typography.Paragraph className="json-block">
                  <pre>{JSON.stringify(result, null, 2)}</pre>
                </Typography.Paragraph>
              </Card>
            </Space>
          )}
        </Card>
      </div>
    </div>
  )
}

function parseJsonObject(text: string, fieldName: string) {
  const value = parseJson(text, fieldName)
  if (Array.isArray(value) || value === null || typeof value !== 'object') {
    throw new Error(`${fieldName} 必须是 JSON 对象`)
  }
  return value as Record<string, unknown>
}

function parseJsonArray(text: string, fieldName: string) {
  const value = parseJson(text, fieldName)
  if (!Array.isArray(value)) {
    throw new Error(`${fieldName} 必须是 JSON 数组`)
  }
  return value as Array<Record<string, unknown>>
}

function parseJson(text: string, fieldName: string) {
  try {
    return JSON.parse(text)
  } catch (error) {
    throw new Error(`${fieldName} 不是合法 JSON: ${error instanceof Error ? error.message : '解析失败'}`)
  }
}
