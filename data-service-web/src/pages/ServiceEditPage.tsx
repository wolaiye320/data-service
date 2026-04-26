import { ArrowLeftOutlined, PlayCircleOutlined, SaveOutlined, ThunderboltOutlined, HistoryOutlined, SettingOutlined, DatabaseOutlined, CheckCircleOutlined, CloseCircleOutlined, CodeOutlined, FileTextOutlined, SafetyOutlined } from '@ant-design/icons'
import { Alert, App, Badge, Button, Card, Collapse, Descriptions, Empty, Form, Input, InputNumber, Select, Space, Table, Tabs, Tag, Tooltip, Typography, theme } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { listConnections, type ConnectionDetail } from '../api/connections'
import { formatDateTime } from '../api/format'
import { SqlCodeEditor } from '../components/SqlCodeEditor'
import {
  createService,
  getCachePolicy,
  listServices,
  previewService,
  saveCachePolicy,
  updateService,
  type CachePolicy,
  type CachePolicyPayload,
  type ServiceDetail,
  type ServicePayload,
  type ServicePreviewResponse,
  type ServiceStatus,
  type ServiceVersion,
} from '../api/services'
import { ApiRequestError } from '../api/request'

type ServiceFormValue = ServicePayload

type ParamDefinitionFormValue = {
  paramName: string
  paramType?: string
  defaultValue?: string
}



type CachePolicyFormValue = Omit<CachePolicyPayload, 'contextKeys'> & {
  contextKeysText?: string
}

type SourceSnapshot = {
  connectionCode?: string
  schemaName?: string
  databaseName?: string
  tableName?: string
  alias?: string
  dbType?: string
}

type ParamSnapshot = {
  paramName?: string
  paramType?: string
  placeholder?: string
  collection?: boolean
  defaultValue?: string
}

type FieldSnapshot = {
  fieldName?: string
  expression?: string
  sortOrder?: number
}

type ValidationSnapshot = {
  status?: string
  result?: string
  success?: boolean
  errors?: unknown[]
  warnings?: unknown[]
  [key: string]: unknown
}

const sqlTypeOptions = [
  { value: 'SIMPLE_SQL', label: '简单 SQL' },
  { value: 'FEDERATED_SQL', label: '联邦 SQL' },
]

const paramTypeOptions = [
  { value: 'STRING', label: 'STRING' },
  { value: 'LONG', label: 'LONG' },
  { value: 'INTEGER', label: 'INTEGER' },
  { value: 'DOUBLE', label: 'DOUBLE' },
  { value: 'DECIMAL', label: 'DECIMAL' },
  { value: 'BOOLEAN', label: 'BOOLEAN' },
  { value: 'DATE', label: 'DATE' },
  { value: 'DATETIME', label: 'DATETIME' },
]

const sensitiveKeyPattern = /(password|passwd|pwd|secret|token|accessKey|privateKey|credential|身份证|手机号|phone|mobile|email|邮箱)/i

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '操作失败'
}

function statusText(status: ServiceStatus) {
  if (status === 'PUBLISHED') {
    return '已发布'
  }
  if (status === 'DISABLED') {
    return '已停用'
  }
  return '草稿'
}

function statusColor(status: ServiceStatus): string {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DISABLED') return 'default'
  return 'processing'
}

function parseSnapshot<T>(json?: string): { data?: T; error?: string } {
  if (!json?.trim()) {
    return {}
  }
  try {
    return { data: JSON.parse(json) as T }
  } catch {
    return { error: '快照 JSON 解析失败' }
  }
}

function snapshotSize(value: unknown) {
  return Array.isArray(value) ? value.length : value ? 1 : 0
}

function validationStatus(snapshot?: ValidationSnapshot) {
  if (!snapshot) {
    return { color: 'default' as const, text: '未生成' }
  }
  if (snapshot.success === true || snapshot.status === 'PASS' || snapshot.result === 'PASS') {
    return { color: 'success' as const, text: '通过' }
  }
  if (snapshot.success === false || snapshot.status === 'FAIL' || snapshot.result === 'FAIL') {
    return { color: 'error' as const, text: '失败' }
  }
  return { color: 'processing' as const, text: '已生成' }
}

function formatSnapshotValue(value: unknown) {
  if (value == null || value === '') {
    return '-'
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return JSON.stringify(value)
}

function toParamDefinitions(paramSnapshotJson?: string): ParamDefinitionFormValue[] {
  const { data } = parseSnapshot<ParamSnapshot[]>(paramSnapshotJson)
  if (!data?.length) {
    return []
  }
  return data
    .filter((item): item is ParamSnapshot & { paramName: string } => Boolean(item.paramName))
    .map((item) => ({
      paramName: item.paramName,
      paramType: item.paramType && item.paramType !== 'UNKNOWN' ? item.paramType : undefined,
      defaultValue: item.defaultValue ?? undefined,
    }))
}

function parseJsonObject(text?: string) {
  if (!text?.trim()) {
    return {}
  }
  const value = JSON.parse(text) as unknown
  if (!value || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('请输入 JSON 对象')
  }
  return value as Record<string, unknown>
}

function maskSensitiveValue(key: string, value: unknown) {
  if (value == null || value === '') {
    return value
  }
  return sensitiveKeyPattern.test(key) ? '******' : value
}

function formatSafeValue(key: string, value: unknown) {
  return formatSnapshotValue(maskSensitiveValue(key, value))
}

function safeRows(rows?: Record<string, unknown>[] | null) {
  return Array.isArray(rows) ? rows : []
}

function validateContextKeysText(text?: string) {
  const keys = parseContextKeys(text) ?? []
  const invalid = keys.find((key) => key.length > 64)
  if (invalid) {
    throw new Error(`上下文键最多 64 个字符：${invalid}`)
  }
}

function parseContextKeys(text?: string) {
  if (!text?.trim()) {
    return undefined
  }
  return text
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function contextKeysText(keys?: string[]) {
  return keys?.join('\n')
}

function cachePolicyInitialValues(policy?: CachePolicy): CachePolicyFormValue {
  return {
    enabled: policy?.enabled ?? false,
    ttlSeconds: policy?.ttlSeconds,
    cacheKeyTemplate: policy?.cacheKeyTemplate,
    maxEntries: policy?.maxEntries,
    contextKeysText: contextKeysText(policy?.contextKeys),
    remark: policy?.remark,
  }
}

// 统计卡片组件
function StatCard({ label, value, icon, color }: { label: string; value: React.ReactNode; icon: React.ReactNode; color?: string }) {
  return (
    <div className="stat-card">
      <div className="stat-icon" style={{ backgroundColor: color || '#1677ff' }}>{icon}</div>
      <div className="stat-content">
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
    </div>
  )
}

// 快照面板组件
function SnapshotPanel({ version }: { version?: ServiceVersion }) {
  const source = parseSnapshot<SourceSnapshot[]>(version?.sourceSnapshotJson)
  const params = parseSnapshot<ParamSnapshot[]>(version?.paramSnapshotJson)
  const fields = parseSnapshot<FieldSnapshot[]>(version?.fieldSnapshotJson)
  const validation = parseSnapshot<ValidationSnapshot>(version?.validationSnapshotJson)
  const validationTag = validationStatus(validation.data)

  const items = useMemo(() => [
    {
      key: 'source',
      label: (
        <Space>
          <DatabaseOutlined />
          <span>数据来源</span>
          {source.data?.length ? <Badge count={source.data.length} style={{ backgroundColor: '#1677ff' }} /> : null}
        </Space>
      ),
      children: source.data?.length ? (
        <Table<SourceSnapshot>
          rowKey={(record, index) => `${record.connectionCode ?? '-'}-${record.tableName ?? '-'}-${index}`}
          size="small"
          pagination={false}
          dataSource={source.data}
          columns={[
            { title: '连接', dataIndex: 'connectionCode', width: 180 },
            { title: '库', dataIndex: 'databaseName', render: formatSnapshotValue, width: 120 },
            { title: 'Schema', dataIndex: 'schemaName', render: formatSnapshotValue, width: 120 },
            { title: '表', dataIndex: 'tableName', render: formatSnapshotValue, width: 150 },
            { title: '别名', dataIndex: 'alias', render: formatSnapshotValue, width: 120 },
            { title: '类型', dataIndex: 'dbType', render: formatSnapshotValue, width: 120 },
          ]}
          scroll={{ x: 'max-content' }}
        />
      ) : (
        <Empty description="暂无来源快照" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ),
    },
    {
      key: 'params',
      label: (
        <Space>
          <CodeOutlined />
          <span>参数定义</span>
          {params.data?.length ? <Badge count={params.data.length} style={{ backgroundColor: '#13c2c2' }} /> : null}
        </Space>
      ),
      children: params.data?.length ? (
        <Table<ParamSnapshot>
          rowKey={(record, index) => `${record.paramName ?? '-'}-${index}`}
          size="small"
          pagination={false}
          dataSource={params.data}
          columns={[
            { title: '参数名', dataIndex: 'paramName', render: formatSnapshotValue, width: 180 },
            { title: '类型', dataIndex: 'paramType', render: formatSnapshotValue, width: 120 },
            { title: '集合', dataIndex: 'collection', render: (value?: boolean) => (value ? '是' : '否'), width: 80 },
            { title: '默认值', dataIndex: 'defaultValue', render: (_: unknown, record) => formatSafeValue(record.paramName ?? '', record.defaultValue), width: 150 },
            { title: '占位符', dataIndex: 'placeholder', render: formatSnapshotValue },
          ]}
          scroll={{ x: 'max-content' }}
        />
      ) : (
        <Empty description="暂无参数快照" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ),
    },
    {
      key: 'fields',
      label: (
        <Space>
          <FileTextOutlined />
          <span>返回字段</span>
          {fields.data?.length ? <Badge count={fields.data.length} style={{ backgroundColor: '#52c41a' }} /> : null}
        </Space>
      ),
      children: fields.data?.length ? (
        <Table<FieldSnapshot>
          rowKey={(record, index) => `${record.fieldName ?? '-'}-${record.sortOrder ?? index}`}
          size="small"
          pagination={false}
          dataSource={fields.data}
          columns={[
            { title: '序号', dataIndex: 'sortOrder', width: 80, render: formatSnapshotValue },
            { title: '字段名', dataIndex: 'fieldName', render: formatSnapshotValue, width: 200 },
            { title: '表达式', dataIndex: 'expression', render: formatSnapshotValue },
          ]}
          scroll={{ x: 'max-content' }}
        />
      ) : (
        <Empty description="暂无字段快照" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ),
    },
  ], [version, source, params, fields, validationTag])

  if (!version) {
    return (
      <Card className="snapshot-panel" size="small" title={<><CheckCircleOutlined /> SQL 解析与校验结果</>}>
        <Empty description="保存草稿后生成来源、参数、字段和校验快照" image={Empty.PRESENTED_IMAGE_SIMPLE}>
          <Typography.Text type="secondary">请先完成基础配置并保存</Typography.Text>
        </Empty>
      </Card>
    )
  }

  return (
    <Card className="snapshot-panel" size="small" title={<><CheckCircleOutlined /> SQL 解析与校验结果</>}>
      <Collapse defaultActiveKey={['source']} items={items} ghost />
    </Card>
  )
}

// 版本历史组件
function VersionHistory({ service }: { service?: ServiceDetail }) {
  const versions = service?.versionHistory ?? []

  if (!service) {
    return (
      <Card className="version-panel" size="small" title={<><HistoryOutlined /> 版本历史</>}>
        <Empty description="保存草稿后可查看版本信息" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    )
  }

  return (
    <Card className="version-panel" size="small" title={<><HistoryOutlined /> 版本历史</>}>
      <div className="version-overview">
        <div className="version-stat">
          <span className="version-stat-label">当前版本</span>
          <span className="version-stat-value">{service.currentVersion ?? '-'}</span>
        </div>
        <div className="version-stat">
          <span className="version-stat-label">草稿版本</span>
          <span className="version-stat-value">{service.draftVersion?.version ?? '-'}</span>
        </div>
        <div className="version-stat">
          <span className="version-stat-label">状态</span>
          <Tag color={statusColor(service.status)}>{statusText(service.status)}</Tag>
        </div>
        <div className="version-stat">
          <span className="version-stat-label">历史版本</span>
          <span className="version-stat-value">{versions.length}</span>
        </div>
      </div>
      {versions.length > 0 && (
        <Table<ServiceVersion>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={versions}
          columns={[
            { title: '版本', dataIndex: 'version', width: 80 },
            { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag>{formatSnapshotValue(v)}</Tag> },
            { title: 'SQL 类型', dataIndex: 'sqlType', width: 120, render: formatSnapshotValue },
            { title: '计划快照', dataIndex: 'planSnapshotJson', width: 100, render: (value?: string) => (value ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : '-') },
            { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: formatDateTime },
          ]}
          className="version-table"
        />
      )}
    </Card>
  )
}

// 预览结果组件
function PreviewResult({ result }: { result?: ServicePreviewResponse }) {
  if (!result) return null

  const rows = safeRows(result.rows)
  const rowKeys = Array.from(new Set(rows.flatMap((row) => Object.keys(row))))
  const diagnostics = result.diagnosticSummary ? Object.entries(result.diagnosticSummary) : []

  return (
    <div className="preview-result">
      <div className="preview-stats">
        <StatCard label="草稿版本" value={result.draftVersion} icon={<HistoryOutlined />} color="#722ed1" />
        <StatCard label="返回行数" value={rows.length} icon={<FileTextOutlined />} color="#52c41a" />
        <StatCard label="耗时" value={`${result.elapsedMs ?? '-'} ms`} icon={<ThunderboltOutlined />} color="#fa8c16" />
      </div>

      {rows.length > 0 && (
        <Card size="small" title="预览数据" className="preview-data-card">
          <Table<Record<string, unknown>>
            rowKey={(_, index) => String(index)}
            size="small"
            pagination={{ pageSize: 10, size: 'small' }}
            scroll={{ x: 'max-content' }}
            dataSource={rows}
            columns={rowKeys.map((key) => ({
              title: key,
              dataIndex: key,
              render: (value: unknown) => formatSafeValue(key, value),
            }))}
          />
        </Card>
      )}

      {diagnostics.length > 0 && (
        <Card size="small" title="诊断信息" className="preview-diag-card">
          <Descriptions size="small" bordered column={1}>
            {diagnostics.map(([key, value]) => (
              <Descriptions.Item key={key} label={key}>
                {formatSafeValue(key, value)}
              </Descriptions.Item>
            ))}
          </Descriptions>
        </Card>
      )}
    </div>
  )
}

export default function ServiceEditPageOptimized() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const { token } = theme.useToken()
  const isNew = !id

  const [form] = Form.useForm<ServiceFormValue>()
  const [cachePolicyForm] = Form.useForm<CachePolicyFormValue>()

  const [service, setService] = useState<ServiceDetail>()
  const [connections, setConnections] = useState<ConnectionDetail[]>([])
  const [loading, setLoading] = useState(!isNew)
  const [loadError, setLoadError] = useState<string>()
  const [saving, setSaving] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [previewResult, setPreviewResult] = useState<ServicePreviewResponse>()
  const [previewError, setPreviewError] = useState<string>()
  const [cachePolicy, setCachePolicy] = useState<CachePolicy>()
  const [cachePolicyLoading, setCachePolicyLoading] = useState(false)
  const [cachePolicySaving, setCachePolicySaving] = useState(false)
  const [activeTab, setActiveTab] = useState('basic')
  const cachePolicyRequestIdRef = useRef(0)

  const editingParamDefinitions = useMemo(
    () => toParamDefinitions(service?.draftVersion?.paramSnapshotJson),
    [service?.draftVersion?.paramSnapshotJson],
  )

  const connectionOptions = connections.map((connection) => ({
    value: connection.connectionCode,
    label: `${connection.connectionName} (${connection.connectionCode})`,
  }))

  useEffect(() => {
    void listConnections('ENABLED')
      .then(setConnections)
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (isNew) {
      setService(undefined)
      setLoadError(undefined)
      setPreviewResult(undefined)
      setPreviewError(undefined)
      setCachePolicy(undefined)
      form.setFieldsValue({
        sqlType: 'SIMPLE_SQL',
        maxBatchSize: 100,
        maxResultRows: 1000,
        queryTimeoutSeconds: 30,
        federatedQueryTimeoutSeconds: 60,
        paramDefinitions: [],
      })
      return
    }

    setLoading(true)
    setLoadError(undefined)
    void listServices()
      .then((all) => {
        const found = all.find((s) => String(s.id) === id)
        if (!found) {
          setLoadError('未找到该数据服务')
          return
        }
        setService(found)
        form.setFieldsValue({
          serviceCode: found.serviceCode,
          serviceName: found.serviceName,
          sqlType: found.sqlType,
          defaultConnectionCode: found.defaultConnectionCode,
          sqlText: found.draftVersion?.sqlText ?? '',
          maxBatchSize: found.maxBatchSize,
          maxResultRows: found.maxResultRows,
          queryTimeoutSeconds: found.queryTimeoutSeconds,
          federatedQueryTimeoutSeconds: found.federatedQueryTimeoutSeconds,
          paramDefinitions: toParamDefinitions(found.draftVersion?.paramSnapshotJson),
          remark: found.remark,
        })
      })
      .catch((err) => {
        setLoadError(errorMessage(err))
      })
      .finally(() => {
        setLoading(false)
      })
  }, [id, isNew, form])

  useEffect(() => {
    if (isNew || !service) {
      return
    }
    const cachePolicyRequestId = cachePolicyRequestIdRef.current + 1
    cachePolicyRequestIdRef.current = cachePolicyRequestId
    setCachePolicyLoading(true)
    void getCachePolicy(service.id)
      .then((policy) => {
        if (cachePolicyRequestIdRef.current !== cachePolicyRequestId) {
          return
        }
        setCachePolicy(policy)
        cachePolicyForm.setFieldsValue(cachePolicyInitialValues(policy))
      })
      .catch(() => {})
      .finally(() => {
        if (cachePolicyRequestIdRef.current === cachePolicyRequestId) {
          setCachePolicyLoading(false)
        }
      })
  }, [isNew, service, cachePolicyForm])

  const submitForm = async () => {
    if (!service && !isNew) {
      return
    }
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (!isNew && service) {
        await updateService(service.id, values)
        message.success('服务草稿已保存')
      } else {
        await createService(values)
        message.success('服务已创建')
      }
      navigate('/services')
    } catch (err) {
      message.error(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleQuickPreview = async () => {
    if (!service) {
      message.warning('请先保存草稿后再预览')
      return
    }
    // 收集所有参数的默认值
    const paramDefinitions = form.getFieldValue('paramDefinitions') || []
    const previewParams: Record<string, string> = {}
    paramDefinitions.forEach((def: { paramName: string; defaultValue?: string }) => {
      if (def.defaultValue) {
        previewParams[def.paramName] = def.defaultValue
      }
    })

    setPreviewing(true)
    setPreviewResult(undefined)
    setPreviewError(undefined)
    try {
      const result = await previewService(service.id, {
        previewParams,
        requestContext: {},
      })
      setPreviewResult(result)
      message.success('预览执行完成')
    } catch (err) {
      const detail = errorMessage(err)
      setPreviewError(detail)
      message.error(detail)
    } finally {
      setPreviewing(false)
    }
  }

  const handleSaveCachePolicy = async () => {
    if (!service) {
      message.warning('请先保存草稿后再维护缓存策略')
      return
    }
    const values = await cachePolicyForm.validateFields()
    setCachePolicySaving(true)
    try {
      const saved = await saveCachePolicy(service.id, {
        enabled: values.enabled ?? false,
        ttlSeconds: values.ttlSeconds,
        cacheKeyTemplate: values.cacheKeyTemplate,
        maxEntries: values.maxEntries,
        contextKeys: parseContextKeys(values.contextKeysText),
        remark: values.remark,
      })
      setCachePolicy(saved)
      cachePolicyForm.setFieldsValue(cachePolicyInitialValues(saved))
      message.success('缓存策略已保存')
    } catch (err) {
      message.error(errorMessage(err))
    } finally {
      setCachePolicySaving(false)
    }
  }

  const handleBack = useCallback(() => {
    navigate('/services')
  }, [navigate])

  const tabItems = [
    {
      key: 'basic',
      label: (
        <span>
          <SettingOutlined /> 基础配置
        </span>
      ),
      children: (
        <div className="tab-content">
          <Form form={form} layout="vertical" requiredMark className="optimized-form">
            <div className="form-section">
              <div className="section-title">基本信息</div>
              <div className="form-row">
                {isNew && (
                  <Form.Item
                    name="serviceCode"
                    label="服务编码"
                    rules={[{ required: true, message: '请输入服务编码' }, { max: 64, message: '最多 64 个字符' }]}
                    className="form-col"
                  >
                    <Input placeholder="如 customer_order_query" />
                  </Form.Item>
                )}
                <Form.Item
                  name="serviceName"
                  label="服务名称"
                  rules={[{ required: true, message: '请输入服务名称' }, { max: 128, message: '最多 128 个字符' }]}
                  className="form-col"
                >
                  <Input placeholder="如客户订单查询" />
                </Form.Item>
                <Form.Item name="remark" label="备注" rules={[{ max: 512, message: '最多 512 个字符' }]} className="form-col remark-col">
                  <Input placeholder="可选说明" />
                </Form.Item>
              </div>
              <div className="form-row">
                <Form.Item
                  name="sqlType"
                  label="SQL 类型"
                  rules={[{ required: true, message: '请选择 SQL 类型' }]}
                  className="form-col"
                >
                  <Select options={sqlTypeOptions} />
                </Form.Item>
                <Form.Item
                  name="defaultConnectionCode"
                  label="默认连接"
                  dependencies={['sqlType']}
                  rules={[
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (getFieldValue('sqlType') !== 'SIMPLE_SQL' || value) {
                          return Promise.resolve()
                        }
                        return Promise.reject(new Error('简单 SQL 必须选择默认连接'))
                      },
                    }),
                  ]}
                  className="form-col"
                >
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    options={connectionOptions}
                    placeholder="简单 SQL 请选择默认连接"
                  />
                </Form.Item>
              </div>
              <Form.Item
                name="sqlText"
                label="SQL 文本"
                rules={[{ required: true, message: '请输入 SQL 文本' }]}
              >
                <SqlCodeEditor
                  rows={8}
                  placeholder="select ... from schema.table where id = /* id */0"
                  className="sql-editor"
                />
              </Form.Item>
            </div>

            {editingParamDefinitions.length > 0 && (
              <div className="form-section">
                <div className="section-title-with-action">
                  <span className="section-title-text">参数定义</span>
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    loading={previewing}
                    disabled={!service}
                    onClick={() => void handleQuickPreview()}
                  >
                    执行预览
                  </Button>
                </div>
                <Card
                  size="small"
                  className="param-defs-card"
                >
                  <Form.List name="paramDefinitions">
                    {(fields) => (
                      <Table<{ key: number; field: { name: number; key: number }; paramName: string }>
                        size="small"
                        pagination={false}
                        dataSource={fields.map((field) => ({
                          key: field.key,
                          field,
                          paramName: editingParamDefinitions[field.name]?.paramName ?? '',
                        }))}
                        columns={[
                          {
                            title: '参数名',
                            dataIndex: 'paramName',
                            width: 180,
                            render: (paramName: string, record: { field: { name: number } }) => (
                              <>
                                <Form.Item name={[record.field.name, 'paramName']} hidden>
                                  <Input />
                                </Form.Item>
                                <Tag color="blue">{paramName}</Tag>
                              </>
                            ),
                          },
                          {
                            title: '类型',
                            width: 150,
                            render: (_: unknown, record: { field: { name: number } }) => (
                              <Form.Item
                                name={[record.field.name, 'paramType']}
                                rules={[{ required: true, message: '请选择参数类型' }]}
                                style={{ marginBottom: 0 }}
                              >
                                <Select options={paramTypeOptions} placeholder="选择类型" style={{ width: 130 }} />
                              </Form.Item>
                            ),
                          },
                          {
                            title: '默认值',
                            width: 150,
                            render: (_: unknown, record: { field: { name: number } }) => (
                              <Form.Item
                                name={[record.field.name, 'defaultValue']}
                                style={{ marginBottom: 0 }}
                              >
                                <Input placeholder="输入默认值" style={{ width: 130 }} />
                              </Form.Item>
                            ),
                          },
                          {
                            title: '占位符',
                            width: 180,
                            render: (_: unknown, record: { field: { name: number } }) => (
                              <Typography.Text type="secondary" style={{ fontFamily: 'monospace' }}>
                                /* {editingParamDefinitions[record.field.name]?.paramName} */
                              </Typography.Text>
                            ),
                          },
                        ]}
                      />
                    )}
                  </Form.List>
                </Card>
                {/* 预览结果展示 */}
                {previewError && (
                  <Alert
                    className="preview-error"
                    type="error"
                    showIcon
                    message="预览执行失败"
                    description={previewError}
                    closable
                    onClose={() => setPreviewError(undefined)}
                    style={{ marginTop: 16 }}
                  />
                )}
                {previewResult && (
                  <Card
                    size="small"
                    title="预览结果"
                    style={{ marginTop: 16 }}
                    extra={
                      <Button type="link" size="small" onClick={() => setPreviewResult(undefined)}>
                        清除
                      </Button>
                    }
                  >
                    <PreviewResult result={previewResult} />
                  </Card>
                )}
              </div>
            )}
          </Form>
        </div>
      ),
    },
    {
      key: 'snapshot',
      label: (
        <span>
          <CheckCircleOutlined /> 解析结果
        </span>
      ),
      children: (
        <div className="tab-content">
          <SnapshotPanel version={service?.draftVersion} />
          <VersionHistory service={service} />
        </div>
      ),
    },
    {
      key: 'cache',
      label: (
        <span>
          <SettingOutlined /> 其他配置
        </span>
      ),
      children: (
        <div className="tab-content">
          {/* 限制配置 */}
          <Card
            size="small"
            title={<><SafetyOutlined /> 限制配置</>}
            className="limit-config-card"
            style={{ marginBottom: 16 }}
          >
            <Form form={form} layout="vertical" className="optimized-form">
              <div className="form-row four-cols">
                <Form.Item name="maxBatchSize" label="批量上限" rules={[{ required: true }]} className="form-col">
                  <InputNumber min={1} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="maxResultRows" label="结果行上限" rules={[{ required: true }]} className="form-col">
                  <InputNumber min={1} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="queryTimeoutSeconds" label="单源超时(秒)" rules={[{ required: true }]} className="form-col">
                  <InputNumber min={1} max={3600} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="federatedQueryTimeoutSeconds" label="联邦超时(秒)" rules={[{ required: true }]} className="form-col">
                  <InputNumber min={1} max={3600} style={{ width: '100%' }} />
                </Form.Item>
              </div>
            </Form>
          </Card>

          {/* 缓存配置 */}
          <Card
            size="small"
            title={<><ThunderboltOutlined /> 缓存配置</>}
            loading={cachePolicyLoading}
            className="cache-config-card"
            extra={!service && <Tag color="warning">需先保存服务</Tag>}
          >
            <Form form={cachePolicyForm} layout="vertical" className="optimized-form">
              {!service ? (
                <Empty description="保存草稿后可维护缓存策略" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <>
                  <div className="form-row">
                  <Form.Item
                    name="enabled"
                    label="启用缓存"
                    rules={[{ required: true, message: '请选择是否启用缓存' }]}
                    className="form-col"
                  >
                    <Select
                      options={[
                        { value: true, label: '启用' },
                        { value: false, label: '停用' },
                      ]}
                      disabled={!service}
                    />
                  </Form.Item>
                  <Form.Item
                    name="ttlSeconds"
                    label="TTL 秒数"
                    dependencies={['enabled']}
                    rules={[
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (!getFieldValue('enabled') || value) {
                            return Promise.resolve()
                          }
                          return Promise.reject(new Error('启用缓存时必须填写 TTL 秒数'))
                        },
                      }),
                    ]}
                    className="form-col"
                  >
                    <InputNumber min={1} max={86400} style={{ width: '100%' }} disabled={!service} />
                  </Form.Item>
                </div>
                <div className="form-row">
                  <Form.Item
                    name="cacheKeyTemplate"
                    label="缓存键模板"
                    rules={[{ max: 128, message: '最多 128 个字符' }]}
                    className="form-col"
                  >
                    <Input placeholder="data-service:{serviceCode}:v{version}:{paramHash}" disabled={!service} />
                  </Form.Item>
                  <Form.Item name="maxEntries" label="最大条目数" className="form-col">
                    <InputNumber min={1} max={100000} style={{ width: '100%' }} disabled={!service} />
                  </Form.Item>
                </div>
                <Form.Item
                  name="contextKeysText"
                  label="上下文隔离键"
                  extra="多个键可用逗号或换行分隔"
                  rules={[
                    {
                      validator(_, value) {
                        try {
                          validateContextKeysText(value)
                          return Promise.resolve()
                        } catch (err) {
                          return Promise.reject(err instanceof Error ? err : new Error('上下文隔离键格式错误'))
                        }
                      },
                    },
                  ]}
                >
                  <Input.TextArea rows={2} placeholder="tenantId, callerId" disabled={!service} />
                </Form.Item>
                <Form.Item name="remark" label="缓存备注" rules={[{ max: 512, message: '最多 512 个字符' }]}>
                  <Input placeholder="可选说明" disabled={!service} />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button
                      type="primary"
                      icon={<SaveOutlined />}
                      loading={cachePolicySaving}
                      disabled={!service}
                      onClick={() => void handleSaveCachePolicy()}
                    >
                      保存缓存策略
                    </Button>
                    {cachePolicy?.updatedAt && (
                      <Typography.Text type="secondary">
                        最近更新：{formatDateTime(cachePolicy.updatedAt)}
                      </Typography.Text>
                    )}
                  </Space>
                </Form.Item>
                </>
              )}
            </Form>
          </Card>
        </div>
      ),
    },
  ]

  return (
    <div className="module-page" data-testid="service-edit-page">
      {/* 页面头部 */}
      <div className="module-hero">
        <Typography.Title level={5}>{isNew ? '新增数据服务' : '编辑数据服务'}</Typography.Title>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回</Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={() => void submitForm()}
          >
            保存草稿
          </Button>
        </Space>
      </div>

      {/* 错误提示 */}
      {loadError && (
        <Alert
          className="page-error"
          type="error"
          showIcon
          message="加载失败"
          description={loadError}
          closable
        />
      )}

      {/* 标签页内容 */}
      <div className="li-page-main-card">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          type="card"
          className="optimized-tabs"
        />
      </div>
    </div>
  )
}
