import { Alert, App, Button, Descriptions, Drawer, Form, Input, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { formatDateTime } from '../api/format'
import { getAuditDetail, listAudits, type AuditLogDetail, type AuditLogQuery, type AuditLogSummary } from '../api/audits'
import { ApiRequestError } from '../api/request'
import { Icons } from '../components/icons'
import { TableActionButton } from '../components/TableActionButton'

type AuditFilterFormValue = {
  serviceCode?: string
  operator?: string
  eventType?: string
  operationResult?: string
  traceId?: string
  startAt?: string
  endAt?: string
}

const DEFAULT_PAGE = 1
const DEFAULT_SIZE = 10

const eventTypeOptions = [
  { value: 'QUERY_EXECUTE', label: 'QUERY_EXECUTE' },
  { value: 'QUERY_TENANT_ACCESS_DENIED', label: 'QUERY_TENANT_ACCESS_DENIED' },
  { value: 'ADMIN_ACCESS_DENIED', label: 'ADMIN_ACCESS_DENIED' },
  { value: 'CREATE_CONNECTION', label: 'CREATE_CONNECTION' },
  { value: 'UPDATE_CONNECTION', label: 'UPDATE_CONNECTION' },
  { value: 'UPDATE_CONNECTION_STATUS', label: 'UPDATE_CONNECTION_STATUS' },
  { value: 'TEST_CONNECTION', label: 'TEST_CONNECTION' },
  { value: 'CREATE_SERVICE', label: 'CREATE_SERVICE' },
  { value: 'UPDATE_SERVICE', label: 'UPDATE_SERVICE' },
  { value: 'DISABLE_SERVICE', label: 'DISABLE_SERVICE' },
  { value: 'PREVIEW_SERVICE', label: 'PREVIEW_SERVICE' },
  { value: 'PUBLISH_SERVICE', label: 'PUBLISH_SERVICE' },
  { value: 'UPSERT_CACHE_POLICY', label: 'UPSERT_CACHE_POLICY' },
  { value: 'CLEAR_CACHE_POLICY_CACHE', label: 'CLEAR_CACHE_POLICY_CACHE' },
]

const operationResultOptions = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILURE', label: '失败' },
]

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '操作失败'
}

function normalizeFilters(values: AuditFilterFormValue, page: number, size: number): AuditLogQuery {
  return {
    serviceCode: values.serviceCode?.trim() || undefined,
    operator: values.operator?.trim() || undefined,
    eventType: values.eventType || undefined,
    operationResult: values.operationResult || undefined,
    traceId: values.traceId?.trim() || undefined,
    startAt: values.startAt?.trim() || undefined,
    endAt: values.endAt?.trim() || undefined,
    page,
    size,
  }
}

function JsonBlock({ title, content }: { title: string; content?: string }) {
  return (
    <div className="audit-json-block">
      <Typography.Text strong>{title}</Typography.Text>
      <pre>{content?.trim() ? content : '暂无数据'}</pre>
    </div>
  )
}

export default function AuditsPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm<AuditFilterFormValue>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const [items, setItems] = useState<AuditLogSummary[]>([])
  const [page, setPage] = useState(DEFAULT_PAGE)
  const [size, setSize] = useState(DEFAULT_SIZE)
  const [total, setTotal] = useState(0)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detail, setDetail] = useState<AuditLogDetail>()

  const loadAudits = useCallback(async (nextPage = page, nextSize = size) => {
    setLoading(true)
    setError(undefined)
    try {
      const values = form.getFieldsValue()
      const response = await listAudits(normalizeFilters(values, nextPage, nextSize))
      setItems(response.items)
      setPage(response.page)
      setSize(response.size)
      setTotal(response.total)
    } catch (err) {
      setError(errorMessage(err))
      setItems([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }, [form, page, size])

  useEffect(() => {
    void loadAudits(DEFAULT_PAGE, DEFAULT_SIZE)
  }, [loadAudits])

  const handleSearch = async () => {
    setPage(DEFAULT_PAGE)
    await loadAudits(DEFAULT_PAGE, size)
  }

  const handleReset = async () => {
    form.resetFields()
    setPage(DEFAULT_PAGE)
    setSize(DEFAULT_SIZE)
    await loadAudits(DEFAULT_PAGE, DEFAULT_SIZE)
  }

  const openDetail = useCallback(async (record: AuditLogSummary) => {
    setDetailOpen(true)
    setDetail(undefined)
    setDetailLoading(true)
    try {
      setDetail(await getAuditDetail(record.id))
    } catch (err) {
      message.error(errorMessage(err))
      setDetailOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }, [message])

  const columns = useMemo<ColumnsType<AuditLogSummary>>(
    () => [
      { title: '时间', dataIndex: 'createdAt', width: 170, render: formatDateTime },
      { title: '事件', dataIndex: 'eventType', width: 220 },
      { title: '结果', dataIndex: 'operationResult', width: 90, render: (value?: string) => value === 'SUCCESS' ? '成功' : value === 'FAILURE' ? '失败' : '-' },
      { title: '服务编码', dataIndex: 'serviceCode', width: 160, render: (value?: string) => value || '-' },
      { title: '操作人', dataIndex: 'operator', width: 140 },
      { title: '目标标识', dataIndex: 'targetId', width: 160 },
      { title: 'TraceId', dataIndex: 'traceId', width: 180, render: (value?: string) => value || '-' },
      { title: '摘要', dataIndex: 'changeSummary', render: (value?: string) => value || '-' },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 80,
        render: (_, record) => (
          <TableActionButton
            label="查看审计详情"
            icon={<Icons.eye />}
            onClick={() => void openDetail(record)}
            testId={`audit-detail-${record.id}`}
          />
        ),
      },
    ],
    [openDetail],
  )

  const handleTableChange = async (pagination: TablePaginationConfig) => {
    const nextPage = pagination.current ?? DEFAULT_PAGE
    const nextSize = pagination.pageSize ?? DEFAULT_SIZE
    await loadAudits(nextPage, nextSize)
  }

  return (
    <div className="module-page" data-testid="audits-page">
      <div className="module-hero">
        <Typography.Title level={5}>审计日志</Typography.Title>
        <Button icon={<Icons.reload />} onClick={() => void loadAudits(page, size)}>
          刷新
        </Button>
      </div>

      <div className="li-page-main-card">
        <Form form={form} layout="vertical" className="form-grid form-grid-four audit-filter-form">
          <Form.Item label="服务编码" name="serviceCode">
            <Input placeholder="如 svc_audit" allowClear />
          </Form.Item>
          <Form.Item label="操作人" name="operator">
            <Input placeholder="如 web-admin" allowClear />
          </Form.Item>
          <Form.Item label="事件类型" name="eventType">
            <Select allowClear placeholder="全部事件" options={eventTypeOptions} />
          </Form.Item>
          <Form.Item label="结果" name="operationResult">
            <Select allowClear placeholder="全部结果" options={operationResultOptions} />
          </Form.Item>
          <Form.Item label="TraceId" name="traceId">
            <Input placeholder="如 trace-1" allowClear />
          </Form.Item>
          <Form.Item label="开始时间" name="startAt" tooltip="ISO 8601，例如 2026-04-25T00:00:00">
            <Input placeholder="2026-04-25T00:00:00" allowClear />
          </Form.Item>
          <Form.Item label="结束时间" name="endAt" tooltip="ISO 8601，例如 2026-04-25T23:59:59">
            <Input placeholder="2026-04-25T23:59:59" allowClear />
          </Form.Item>
          <div className="audit-filter-actions">
            <Space>
              <Button type="primary" onClick={() => void handleSearch()}>
                查询
              </Button>
              <Button onClick={() => void handleReset()}>
                重置
              </Button>
            </Space>
          </div>
        </Form>

        {error ? <Alert type="error" showIcon message="审计日志加载失败" description={error} style={{ marginBottom: 16 }} /> : null}

        <Table
          className="li-zebra-table"
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={items}
          scroll={{ x: 1380 }}
          locale={{ emptyText: error ? '请先处理接口错误后重试' : '暂无审计日志' }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100].map(String),
          }}
          onChange={handleTableChange}
        />
      </div>

      <Drawer
        title={detail ? `审计详情 #${detail.id}` : '审计详情'}
        width={760}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        destroyOnHidden
        loading={detailLoading}
      >
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="事件类型">{detail.eventType}</Descriptions.Item>
              <Descriptions.Item label="结果">{detail.operationResult === 'SUCCESS' ? '成功' : '失败'}</Descriptions.Item>
              <Descriptions.Item label="服务编码">{detail.serviceCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="连接 ID">{detail.connectionId ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="操作人">{detail.operator}</Descriptions.Item>
              <Descriptions.Item label="操作角色">{detail.operatorRole}</Descriptions.Item>
              <Descriptions.Item label="目标类型">{detail.targetType}</Descriptions.Item>
              <Descriptions.Item label="目标标识">{detail.targetId}</Descriptions.Item>
              <Descriptions.Item label="TraceId">{detail.traceId || '-'}</Descriptions.Item>
              <Descriptions.Item label="请求 IP">{detail.requestIp || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.createdBy || '-'}</Descriptions.Item>
              <Descriptions.Item label="摘要" span={2}>{detail.changeSummary || '-'}</Descriptions.Item>
            </Descriptions>
            <JsonBlock title="detailJson" content={detail.detailJson} />
            <JsonBlock title="contextSummaryJson" content={detail.contextSummaryJson} />
          </Space>
        ) : null}
      </Drawer>
    </div>
  )
}
