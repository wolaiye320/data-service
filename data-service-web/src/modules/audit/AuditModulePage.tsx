import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import { getJson, resolveErrorMessage } from '../../services/http'
import type { AuditLogItem, AuditLogPage } from '../../types/admin'

type AuditFilterForm = {
  operator?: string
  eventType?: string
  operationResult?: string
}

const DEFAULT_PAGE_NO = 1
const DEFAULT_PAGE_SIZE = 10

const RESULT_OPTIONS = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

function renderOperationResult(value: string) {
  if (value === 'SUCCESS') {
    return <span className="status-text-success">成功</span>
  }
  if (value === 'FAILED') {
    return <span className="status-text-error">失败</span>
  }
  return <span className="status-text">{value}</span>
}

function safePrettyJson(raw?: string | null) {
  if (!raw) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

export function AuditModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [records, setRecords] = useState<AuditLogItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(DEFAULT_PAGE_NO)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [selectedRecord, setSelectedRecord] = useState<AuditLogItem | null>(null)
  const [activeFilters, setActiveFilters] = useState<AuditFilterForm>({})
  const [form] = Form.useForm<AuditFilterForm>()

  useEffect(() => {
    void loadAuditLogs(DEFAULT_PAGE_NO, DEFAULT_PAGE_SIZE, {})
  }, [])

  const columns = useMemo<ColumnsType<AuditLogItem>>(
    () => [
      {
        title: '时间',
        dataIndex: 'createdAt',
        width: 180,
        render: (value: string) => formatDateTime(value),
      },
      {
        title: '事件',
        dataIndex: 'eventType',
        width: 220,
      },
      {
        title: '操作人',
        dataIndex: 'operator',
        width: 140,
      },
      {
        title: '角色',
        dataIndex: 'operatorRole',
        width: 120,
      },
      {
        title: '结果',
        dataIndex: 'operationResult',
        width: 100,
        render: (value: string) => renderOperationResult(value),
      },
      {
        title: '目标对象',
        width: 220,
        render: (_, record) => (
          <Space size={4} wrap>
            <span>{record.targetId || '-'}</span>
            {record.targetType ? <Tag>{record.targetType}</Tag> : null}
          </Space>
        ),
      },
      {
        title: '变更摘要',
        dataIndex: 'changeSummary',
        ellipsis: true,
        render: (value: string | null | undefined) => value || '-',
      },
      {
        title: '操作',
        key: 'actions',
        width: 88,
        render: (_, record) => (
          <ActionIconButton
            icon={<EyeOutlined />}
            label="查看审计详情"
            onClick={() => void openDetail(record.id)}
          />
        ),
      },
    ],
    [],
  )

  async function loadAuditLogs(nextPageNo: number, nextPageSize: number, filters: AuditFilterForm) {
    setLoading(true)
    try {
      const query = new URLSearchParams({
        pageNo: String(nextPageNo),
        pageSize: String(nextPageSize),
      })
      if (filters.operator?.trim()) {
        query.set('operator', filters.operator.trim())
      }
      if (filters.eventType?.trim()) {
        query.set('eventType', filters.eventType.trim())
      }
      if (filters.operationResult?.trim()) {
        query.set('operationResult', filters.operationResult.trim())
      }
      const response = await getJson<AuditLogPage>(`/api/admin/audit-logs?${query.toString()}`)
      setRecords(response.data.records)
      setTotal(response.data.total)
      setPageNo(nextPageNo)
      setPageSize(nextPageSize)
      setActiveFilters(filters)
      setLoadError(null)
    } catch (error) {
      const errorMessage = resolveErrorMessage(error)
      setRecords([])
      setTotal(0)
      setLoadError(errorMessage)
      message.error(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  async function openDetail(id: number) {
    setDetailLoading(true)
    setDrawerOpen(true)
    try {
      const response = await getJson<AuditLogItem>(`/api/admin/audit-logs/${id}`)
      setSelectedRecord(response.data)
    } catch (error) {
      setDrawerOpen(false)
      message.error(resolveErrorMessage(error))
    } finally {
      setDetailLoading(false)
    }
  }

  async function handleSearch(values: AuditFilterForm) {
    await loadAuditLogs(DEFAULT_PAGE_NO, pageSize, values)
  }

  async function handleReset() {
    form.resetFields()
    await loadAuditLogs(DEFAULT_PAGE_NO, DEFAULT_PAGE_SIZE, {})
  }

  async function handleTableChange(pagination: TablePaginationConfig) {
    await loadAuditLogs(pagination.current ?? DEFAULT_PAGE_NO, pagination.pageSize ?? DEFAULT_PAGE_SIZE, activeFilters)
  }

  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={5}>审计日志</Typography.Title>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void loadAuditLogs(pageNo, pageSize, activeFilters)}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="module-grid">
        <Card title="检索条件" className="li-page-main-card">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              type="info"
              showIcon
              message="支持按操作人、事件类型和执行结果检索审计记录，并可查看结构化详情。"
            />
            <Form<AuditFilterForm> form={form} layout="vertical" onFinish={(values) => void handleSearch(values)}>
              <div className="form-grid form-grid-two">
                <Form.Item label="操作人" name="operator">
                  <Input placeholder="例如：admin-a" allowClear />
                </Form.Item>
                <Form.Item label="事件类型" name="eventType">
                  <Input placeholder="例如：PUBLISH_SERVICE" allowClear />
                </Form.Item>
                <Form.Item label="执行结果" name="operationResult">
                  <Select allowClear options={RESULT_OPTIONS} placeholder="选择结果" />
                </Form.Item>
              </div>
              <Space>
                <Button type="primary" onClick={() => void form.submit()}>
                  查询
                </Button>
                <Button onClick={() => void handleReset()}>重置</Button>
              </Space>
            </Form>
          </Space>
        </Card>

        <Card title={`检索结果 (${total})`} className="li-page-main-card">
          {loading ? (
            <Spin />
          ) : loadError ? (
            <Alert type="error" showIcon message="审计日志加载失败" description={loadError} />
          ) : (
            <Table<AuditLogItem>
              rowKey={(record) => String(record.id)}
              size="small"
              columns={columns}
              dataSource={records}
              scroll={{ x: 1200 }}
              locale={{ emptyText: <Empty description="当前条件下没有审计记录" /> }}
              pagination={{
                current: pageNo,
                pageSize,
                total,
                showSizeChanger: true,
                showTotal: (value) => `共 ${value} 条`,
              }}
              onChange={handleTableChange}
            />
          )}
        </Card>
      </div>

      <Drawer
        title="审计详情"
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
      >
        {detailLoading ? (
          <div className="audit-detail-loading">
            <Spin />
          </div>
        ) : !selectedRecord ? (
          <Empty description="未找到审计详情" />
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="事件类型">{selectedRecord.eventType}</Descriptions.Item>
              <Descriptions.Item label="执行结果">{renderOperationResult(selectedRecord.operationResult)}</Descriptions.Item>
              <Descriptions.Item label="操作人">{selectedRecord.operator}</Descriptions.Item>
              <Descriptions.Item label="角色">{selectedRecord.operatorRole}</Descriptions.Item>
              <Descriptions.Item label="目标类型">{selectedRecord.targetType || '-'}</Descriptions.Item>
              <Descriptions.Item label="目标标识">{selectedRecord.targetId || '-'}</Descriptions.Item>
              <Descriptions.Item label="服务 ID">{selectedRecord.serviceId ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="连接 ID">{selectedRecord.connectionId ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="请求 IP">{selectedRecord.requestIp || '-'}</Descriptions.Item>
              <Descriptions.Item label="traceId">{selectedRecord.traceId || '-'}</Descriptions.Item>
              <Descriptions.Item label="发生时间">{formatDateTime(selectedRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="创建人">{selectedRecord.createdBy || '-'}</Descriptions.Item>
              <Descriptions.Item label="变更摘要" span={2}>
                {selectedRecord.changeSummary || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title="结构化详情">
              <Typography.Paragraph className="json-block">
                <pre>{safePrettyJson(selectedRecord.detailJson)}</pre>
              </Typography.Paragraph>
            </Card>
          </Space>
        )}
      </Drawer>
    </div>
  )
}
