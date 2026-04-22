import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Alert,
  Button,
  Card,
  Collapse,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Select,
  Space,
  Spin,
  Table,
  Tooltip,
  Typography,
  Tag,
} from 'antd'
import {
  EditOutlined,
  EyeOutlined,
  RocketOutlined,
  StopOutlined,
  DeleteOutlined,
  SaveOutlined,
  PlayCircleOutlined,
  SearchOutlined,
  CheckOutlined,
  CodeOutlined,
  TableOutlined,
  InfoCircleOutlined,
  DatabaseOutlined,
  SettingOutlined,
  ReloadOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import { getJson, postJson, putJson, resolveErrorMessage } from '../../services/http'
import type {
  CatalogItem,
  ConnectionItem,
  ConnectionDetail,
  FederatedMetadata,
  FederatedPreviewRequest,
  FieldItem,
  ParamItem,
  SqlAutoDetectResponse,
  ServiceDefinition,
  ServiceDefinitionDetail,
  ServiceDefinitionUpsertPayload,
  ServiceVersionItem,
  SourceCapabilityItem,
  SourceItem,
  SqlPlanItem,
  SqlValidateLogItem,
} from '../../types/admin'

const DEFAULT_DEFINITION: ServiceDefinitionUpsertPayload = {
  serviceCode: '',
  serviceName: '',
  serviceType: 'SIMPLE_QUERY',
  status: 'DRAFT',
  sqlTemplate: 'select 1 as demo_value',
  sqlType: 'SIMPLE_SQL',
  currentSqlVersion: 0,
  version: 0,
  remark: '',
  sources: [],
  params: [],
  fields: [],
}

const FEDERATED_DEFAULTS: Partial<ServiceDefinitionUpsertPayload> = {
  serviceType: 'FEDERATED_QUERY',
  sqlTemplate: '',
  sqlType: 'FEDERATED_SQL',
}

type SqlDraftForm = {
  federatedSqlText: string
  sqlComment?: string
}

type PreviewForm = {
  paramsText: string
}

type AutoDetectForm = {
  draftSqlText: string
  defaultConnectionId?: number
  defaultCatalogId?: number
}

type PreviewResult = {
  rows: Array<Record<string, unknown>>
  meta: Record<string, unknown>
}

type CapabilityRow = SourceCapabilityItem & {
  connectionCode: string
  connectionName: string
}

function renderCatalogLabel(catalog: CatalogItem) {
  return catalog.catalogValue
}

function renderServiceStatusText(status: string) {
  if (status === 'PUBLISHED') {
    return <span className="status-text-success">已发布</span>
  }
  if (status === 'DISABLED') {
    return <span className="status-text">已停用</span>
  }
  return <span className="status-text">草稿</span>
}

function renderPlanStatusText(status: string) {
  if (status === 'PLANNED') {
    return <span className="status-text">已规划</span>
  }
  if (status === 'PUBLISHED') {
    return <span className="status-text-success">已发布</span>
  }
  return <span className="status-text">未规划</span>
}

function renderSqlTypeText(sqlType: string) {
  return sqlType === 'FEDERATED_SQL' ? '联邦 SQL' : '简单 SQL'
}

function renderValidateResultText(result: string) {
  if (result === 'PASS') {
    return <span className="status-text-success">通过</span>
  }
  if (result === 'FAIL') {
    return <span className="status-text-error">失败</span>
  }
  return <span className="status-text">{result}</span>
}

function isFederatedDefinition(definition?: { sqlType?: string | null; serviceType?: string | null } | null) {
  return definition?.sqlType === 'FEDERATED_SQL' || definition?.serviceType === 'FEDERATED_QUERY'
}

function renderExecutionModeLabel(executionMode: string) {
  return executionMode === 'REMOTE_PLUS_LOCAL' ? '远端优先 + 本地补算' : '远端执行'
}

function renderPlanStatusLabel(planStatus: string) {
  if (planStatus === 'PUBLISHED') {
    return '已发布'
  }
  if (planStatus === 'PLANNED') {
    return '已规划'
  }
  return '未规划'
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

function parseJsonObject(text: string, fieldName: string) {
  if (!text?.trim()) {
    return {}
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`${fieldName} 必须是有效的 JSON 对象`)
  }
}

const SQL_KEYWORDS = [
  'select', 'from', 'where', 'join', 'left', 'right', 'inner', 'outer', 'full', 'on', 'and', 'or',
  'group', 'by', 'order', 'having', 'limit', 'offset', 'union', 'all', 'distinct', 'as', 'case',
  'when', 'then', 'else', 'end', 'in', 'is', 'null', 'not', 'exists', 'like', 'between', 'with',
  'insert', 'into', 'update', 'delete', 'create', 'table', 'view'
]

const LEGACY_NAMED_PARAM_PATTERN = /(^|[^:]):([A-Za-z][A-Za-z0-9_]*)/g

function escapeHtml(text: string) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function highlightSql(text: string) {
  let html = escapeHtml(text)
  html = html.replace(/(--.*)$/gm, '<span class="sql-token-comment">$1</span>')
  html = html.replace(/('[^']*(?:''[^']*)*')/g, '<span class="sql-token-string">$1</span>')
  html = html.replace(/(\/\*%\/?(?:if|end)[\s\S]*?\*\/)/g, '<span class="sql-token-directive">$1</span>')
  html = html.replace(/(\/\*\s*[A-Za-z][A-Za-z0-9_]*\s*\*\/)/g, '<span class="sql-token-param">$1</span>')
  const keywordPattern = new RegExp(`\\b(${SQL_KEYWORDS.join('|')})\\b`, 'gi')
  html = html.replace(keywordPattern, '<span class="sql-token-keyword">$1</span>')
  return html
}

function convertLegacyNamedParamsToDoma(
  sqlText?: string | null,
  params?: Array<Pick<ParamItem, 'paramName' | 'paramType'>>
) {
  if (!sqlText) {
    return ''
  }
  const paramTypeMap = new Map((params ?? []).map((item) => [item.paramName, item.paramType]))
  return sqlText.replace(LEGACY_NAMED_PARAM_PATTERN, (match, prefix: string, name: string) => {
    const defaultLiteral = resolveDomaDefaultLiteral(paramTypeMap.get(name))
    return `${prefix}/* ${name} */${defaultLiteral}`
  })
}

function resolveDomaDefaultLiteral(paramType?: string) {
  switch (paramType) {
    case 'BOOLEAN':
      return 'true'
    case 'LONG':
    case 'NUMBER':
    case 'INTEGER':
    case 'INT':
    case 'DECIMAL':
      return '0'
    case 'LIST':
      return '(0)'
    default:
      return "'demo'"
  }
}

function SqlHighlightEditor({
  value,
  onChange,
  placeholder,
  rows = 8,
}: {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
  rows?: number
}) {
  const normalizedValue = value ?? ''
  const lineHeight = 24
  const minHeight = rows * lineHeight + 32

  return (
    <div className="sql-highlight-editor" style={{ minHeight }}>
      {normalizedValue ? (
        <pre
          className="sql-highlight-editor__backdrop"
          aria-hidden="true"
          dangerouslySetInnerHTML={{ __html: highlightSql(normalizedValue) + '\n' }}
        />
      ) : (
        <div className="sql-highlight-editor__placeholder" aria-hidden="true">
          {placeholder}
        </div>
      )}
      <Input.TextArea
        value={normalizedValue}
        onChange={(event) => onChange?.(event.target.value)}
        autoSize={{ minRows: rows }}
        className="sql-highlight-editor__input"
        spellCheck={false}
      />
    </div>
  )
}

function EditableSection({
  title,
  actionLabel,
  emptyText,
  onAdd,
  children,
}: {
  title: string
  actionLabel: string
  emptyText: string
  onAdd: () => void
  children: ReactNode
}) {
  const hasChildren = Array.isArray(children) ? children.length > 0 : Boolean(children)
  return (
    <div className="editable-section">
      <div className="editable-section-header">
        <span className="editable-section-title">{title}</span>
        <Button type="link" size="small" onClick={onAdd}>
          + {actionLabel}
        </Button>
      </div>
      {hasChildren ? (
        <div className="editable-section-content">{children}</div>
      ) : (
        <div className="editable-section-empty">
          <span>{emptyText}</span>
          <Button type="link" size="small" onClick={onAdd}>
            立即添加
          </Button>
        </div>
      )}
    </div>
  )
}

function buildPreviewColumns(rows: Array<Record<string, unknown>>): ColumnsType<Record<string, unknown>> {
  if (rows.length === 0) {
    return []
  }
  return Object.keys(rows[0]).map((key) => ({
    title: key,
    dataIndex: key,
    key,
    render: (value: unknown) => {
      if (value === null || value === undefined) {
        return <span className="text-gray-400">NULL</span>
      }
      if (typeof value === 'object') {
        return <pre className="text-xs">{JSON.stringify(value, null, 2)}</pre>
      }
      return String(value)
    },
  }))
}

function ValidateLogPanel({ logs }: { logs: SqlValidateLogItem[] }) {
  if (logs.length === 0) {
    return <Alert type="info" showIcon message="暂无校验日志" />
  }
  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      {logs.map((log) => (
        <Card key={log.id} size="small">
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Space>
              <Typography.Text strong>阶段: {log.validateStage}</Typography.Text>
              {renderValidateResultText(log.result)}
            </Space>
            {log.message ? <Typography.Text type="secondary">{log.message}</Typography.Text> : null}
          </Space>
        </Card>
      ))}
    </Space>
  )
}

function PlanPanel({ plans }: { plans: SqlPlanItem[] }) {
  if (plans.length === 0) {
    return <Alert type="info" showIcon message="暂无计划产物" />
  }
  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      {plans.map((plan) => (
        <Card key={plan.id} size="small" title={`阶段 ${plan.planStage}`}>
          <Descriptions size="small" column={2}>
            <Descriptions.Item label="格式">{plan.planFormat || '-'}</Descriptions.Item>
            <Descriptions.Item label="下推摘要">{plan.pushdownSummary || '-'}</Descriptions.Item>
            <Descriptions.Item label="回退原因">{plan.fallbackReason || '-'}</Descriptions.Item>
            <Descriptions.Item label="成本摘要">{plan.costSummary || '-'}</Descriptions.Item>
            <Descriptions.Item label="本地执行摘要">{plan.localExecutionSummary || '-'}</Descriptions.Item>
            <Descriptions.Item label="数据源范围">{plan.datasourceScope || '-'}</Descriptions.Item>
          </Descriptions>
          {plan.planContent && (
            <Descriptions size="small" column={1} style={{ marginTop: 8 }}>
              <Descriptions.Item label="计划内容">
                <pre style={{ fontSize: 11, overflow: 'auto', maxHeight: 200 }}>{plan.planContent}</pre>
              </Descriptions.Item>
            </Descriptions>
          )}
        </Card>
      ))}
    </Space>
  )
}

function CapabilityPanel({ rows }: { rows: CapabilityRow[] }) {
  if (rows.length === 0) {
    return <Alert type="info" showIcon message="暂无来源能力数据" />
  }
  return (
    <Table<CapabilityRow>
      rowKey={(record) => `${record.connectionId}-${record.capabilityCode}-${record.scopeValue || ''}`}
      size="small"
      pagination={false}
      dataSource={rows}
      columns={[
        { title: '连接', dataIndex: 'connectionName' },
        { title: '数据库类型', dataIndex: 'dbType' },
        { title: '能力代码', dataIndex: 'capabilityCode' },
        { title: '能力值', dataIndex: 'capabilityValue' },
        { title: '范围', dataIndex: 'scope' },
        { title: '范围值', dataIndex: 'scopeValue' },
      ]}
    />
  )
}

export default function ServiceModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(false)
  const [workspaceLoading, setWorkspaceLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [sqlSaving, setSqlSaving] = useState(false)
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [previewExecuting, setPreviewExecuting] = useState(false)
  const [detectingSql, setDetectingSql] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [definitions, setDefinitions] = useState<ServiceDefinition[]>([])
  const [searchKeyword, setSearchKeyword] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<ServiceDefinitionDetail | null>(null)
  const [versions, setVersions] = useState<ServiceVersionItem[]>([])
  const [connections, setConnections] = useState<ConnectionItem[]>([])
  const [connectionCatalogs, setConnectionCatalogs] = useState<Record<number, CatalogItem[]>>({})
  const [metadata, setMetadata] = useState<FederatedMetadata | null>(null)
  const [capabilities, setCapabilities] = useState<Record<number, SourceCapabilityItem[]>>({})
  const [previewResult, setPreviewResult] = useState<PreviewResult | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [editingDefinition, setEditingDefinition] = useState<ServiceDefinition | null>(null)
  const [form] = Form.useForm<ServiceDefinitionUpsertPayload>()
  const [autoDetectForm] = Form.useForm<AutoDetectForm>()
  const [sqlForm] = Form.useForm<SqlDraftForm>()
  const [previewForm] = Form.useForm<PreviewForm>()

  async function ensureConnectionCatalogs(connectionId?: number) {
    if (!connectionId) {
      return []
    }
    if (connectionCatalogs[connectionId]) {
      return connectionCatalogs[connectionId]
    }
    try {
      const response = await getJson<ConnectionDetail>(`/api/admin/connections/${connectionId}`)
      setConnectionCatalogs((current) => ({
        ...current,
        [connectionId]: response.data.catalogs,
      }))
      return response.data.catalogs
    } catch (err) {
      message.error(resolveErrorMessage(err))
      return []
    }
  }

  async function handleSourceConnectionChange(connectionId?: number, sourceIndex?: number) {
    if (sourceIndex === undefined) {
      return
    }
    const catalogs = await ensureConnectionCatalogs(connectionId)
    form.setFieldValue(['sources', sourceIndex, 'catalogId'], catalogs.length === 1 ? catalogs[0]?.id : undefined)
  }

  async function loadDefinitions(nextSelectedId?: number | null) {
    setLoading(true)
    try {
      const [definitionResponse, connectionResponse] = await Promise.all([
        getJson<ServiceDefinition[]>('/api/admin/service-definitions'),
        getJson<ConnectionItem[]>('/api/admin/connections'),
      ])
      const nextDefinitions = definitionResponse.data
      setDefinitions(nextDefinitions)
      setConnections(connectionResponse.data)
      const effectiveId =
        nextSelectedId ?? (selectedId && nextDefinitions.some((item) => item.id === selectedId) ? selectedId : null)
      setSelectedId(effectiveId ?? null)
    } finally {
      setLoading(false)
    }
  }

  async function loadWorkspace(id: number) {
    setWorkspaceLoading(true)
    try {
      const [detailResponse, versionsResponse] = await Promise.all([
        getJson<ServiceDefinitionDetail>(`/api/admin/service-definitions/${id}`),
        getJson<ServiceVersionItem[]>(`/api/admin/service-definitions/${id}/versions`),
      ])
      const nextDetail = detailResponse.data
      setDetail(nextDetail)
      setVersions(versionsResponse.data)

      if (!isFederatedDefinition(nextDetail.definition)) {
        setMetadata(null)
        setCapabilities({})
        return
      }

      const metadataResponse = await getJson<FederatedMetadata>(`/api/admin/service-definitions/${id}/federated-metadata`)
      setMetadata(metadataResponse.data)

      const distinctConnectionIds = [...new Set(nextDetail.sources.map((item) => item.connectionId).filter(Boolean))] as number[]
      const capabilityEntries = await Promise.all(
        distinctConnectionIds.map(async (connectionId) => {
          const response = await getJson<SourceCapabilityItem[]>(`/api/admin/connections/${connectionId}/capabilities`)
          return [connectionId, response.data] as const
        }),
      )
      setCapabilities(Object.fromEntries(capabilityEntries))
    } finally {
      setWorkspaceLoading(false)
    }
  }

  useEffect(() => {
    void loadDefinitions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (detailOpen && selectedId) {
      void loadWorkspace(selectedId)
      return
    }
    if (!detailOpen) {
      setDetail(null)
      setVersions([])
      setMetadata(null)
      setCapabilities({})
      setPreviewResult(null)
      setPreviewError(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailOpen, selectedId])

  const capabilityRows = useMemo<CapabilityRow[]>(() => {
    const connectionMap = new Map(connections.map((item) => [item.id, item]))
    return Object.entries(capabilities).flatMap(([connectionId, items]) => {
      const connection = connectionMap.get(Number(connectionId))
      return items.map((item) => ({
        ...item,
        connectionCode: connection?.connectionCode ?? String(connectionId),
        connectionName: connection?.connectionName ?? `连接 ${connectionId}`,
      }))
    })
  }, [capabilities, connections])

  const filteredDefinitions = useMemo(() => {
    if (!searchKeyword.trim()) {
      return definitions
    }
    const keyword = searchKeyword.toLowerCase()
    return definitions.filter(
      (item) =>
        item.serviceName?.toLowerCase().includes(keyword) ||
        item.serviceCode?.toLowerCase().includes(keyword),
    )
  }, [definitions, searchKeyword])

  const paginatedDefinitions = useMemo(() => {
    const start = (currentPage - 1) * pageSize
    return filteredDefinitions.slice(start, start + pageSize)
  }, [filteredDefinitions, currentPage, pageSize])

  const previewColumns = useMemo(() => buildPreviewColumns(previewResult?.rows ?? []), [previewResult])

  const listColumns = useMemo<ColumnsType<ServiceDefinition>>(
    () => [
      {
        title: '服务编码',
        dataIndex: 'serviceCode',
        key: 'serviceCode',
        render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
      },
      {
        title: '服务名称',
        dataIndex: 'serviceName',
        key: 'serviceName',
      },
      {
        title: '类型',
        key: 'serviceType',
        render: (_, record) => (isFederatedDefinition(record) ? '联邦服务' : '本地服务'),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        render: (value: string) => renderServiceStatusText(value),
      },
      {
        title: '操作',
        key: 'actions',
        width: 180,
        render: (_, record) => (
          <Space size={4} onClick={(event) => event.stopPropagation()}>
            <ActionIconButton
              icon={<EyeOutlined />}
              label="查看服务详情"
              onClick={() => {
                if (record.id) {
                  setSelectedId(record.id)
                  setDetailOpen(true)
                }
              }}
            />
            <ActionIconButton
              icon={<EditOutlined />}
              label="编辑服务"
              onClick={() => void openEdit(record.id ?? null)}
            />
            {record.status === 'DRAFT' ? (
              <ActionIconButton
                icon={<RocketOutlined />}
                label="发布服务"
                loading={actionLoading === record.id}
                onClick={() => void handlePublish(record.id)}
              />
            ) : null}
            {record.status === 'PUBLISHED' ? (
              <ActionIconButton
                icon={<StopOutlined />}
                label="停用服务"
                confirmTitle="确认停用该服务？"
                onClick={() => void handleDisable(record.id)}
              />
            ) : null}
          </Space>
        ),
      },
    ],
    [actionLoading],
  )

  async function openEdit(id: number | null) {
    if (!id) {
      setEditingDefinition(null)
      form.setFieldsValue(DEFAULT_DEFINITION)
      autoDetectForm.setFieldsValue({
        draftSqlText: '',
        defaultConnectionId: connections.length === 1 ? connections[0].id : undefined,
      })
      setDrawerOpen(true)
      return
    }
    const response = await getJson<ServiceDefinitionDetail>(`/api/admin/service-definitions/${id}`)
    const editingDetail = response.data
    const displaySqlTemplate = convertLegacyNamedParamsToDoma(editingDetail.definition.sqlTemplate, editingDetail.params)
    setEditingDefinition(editingDetail.definition)
    form.setFieldsValue({
      ...DEFAULT_DEFINITION,
      ...(isFederatedDefinition(editingDetail.definition) ? FEDERATED_DEFAULTS : {}),
      ...editingDetail.definition,
      sqlTemplate: displaySqlTemplate,
      sources: editingDetail.sources,
      params: editingDetail.params,
      fields: editingDetail.fields,
    } as ServiceDefinitionUpsertPayload)
    autoDetectForm.setFieldsValue({
      draftSqlText: displaySqlTemplate,
      defaultConnectionId: editingDetail.sources[0]?.connectionId ?? undefined,
      defaultCatalogId: editingDetail.sources[0]?.catalogId ?? undefined,
    })
    void ensureConnectionCatalogs(editingDetail.sources[0]?.connectionId ?? undefined)
    setDrawerOpen(true)
  }

  async function handleAutoDetect() {
    const values = await autoDetectForm.validateFields()
    setDetectingSql(true)
    try {
      const sqlType = form.getFieldValue('sqlType') || 'FEDERATED_SQL'
      const response = await postJson<SqlAutoDetectResponse, {
        sqlText: string
        sqlType: string
        defaultConnectionId?: number
        defaultCatalogId?: number
      }>('/api/admin/service-definitions/sql-auto-detect', {
        sqlText: values.draftSqlText,
        sqlType,
        defaultConnectionId: values.defaultConnectionId,
        defaultCatalogId: values.defaultCatalogId,
      })
      applyAutoDetectResult(response.data, values.draftSqlText)
      message.success('SQL 已自动识别并回填')
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setDetectingSql(false)
    }
  }

  function handleDefaultConnectionChange(connectionId?: number) {
    autoDetectForm.setFieldValue('defaultCatalogId', undefined)
    void ensureConnectionCatalogs(connectionId)
  }

  function applyAutoDetectResult(result: SqlAutoDetectResponse, sqlText: string) {
    const nextValues: Partial<ServiceDefinitionUpsertPayload> = {
      sqlType: result.sqlType,
      serviceType: result.serviceType,
      sqlTemplate: sqlText,
      sources: result.sources.map((item, index) => ({
        connectionId: item.connectionId ?? undefined,
        catalogId: item.catalogId ?? undefined,
        sourceAlias: item.sourceAlias,
        sourceType: item.sourceType,
        sourceValue: item.sourceValue,
        joinKey: result.fields.find((field) => field.sourceAlias === item.sourceAlias && field.joinKey)?.sourceColumn ?? '',
        configJson: '',
      })),
      params: result.params.map((item) => ({
        paramName: item.paramName,
        displayName: item.displayName,
        paramType: item.paramType,
        sqlPlaceholder: item.sqlPlaceholder,
        required: item.required,
        defaultValue: '',
        sortOrder: item.sortOrder,
      })),
      fields: result.fields.map((item) => ({
        sourceAlias: item.sourceAlias ?? '',
        sourceColumn: item.sourceColumn,
        fieldName: item.fieldName,
        displayName: item.displayName,
        fieldType: item.fieldType,
        sortOrder: item.sortOrder,
        primaryKey: item.primaryKey,
        joinKey: item.joinKey,
      })),
    }
    form.setFieldsValue(nextValues as ServiceDefinitionUpsertPayload)
  }

  async function handleSubmit(values: ServiceDefinitionUpsertPayload) {
    setSaving(true)
    try {
      const sqlText = autoDetectForm.getFieldValue('draftSqlText')?.trim() || values.sqlTemplate?.trim() || ''
      const payload = {
        ...values,
        sqlTemplate: sqlText,
        executionMode: undefined,
        planStatus: undefined,
        maxBatchSize: undefined,
        maxResultRows: undefined,
        queryTimeoutSeconds: undefined,
        federatedQueryTimeoutSeconds: undefined,
        sources: (values.sources ?? []).map((item) => ({
          ...item,
          configJson: item.configJson ? parseJsonObject(item.configJson, '来源扩展配置') : undefined,
        })),
      }
      if (editingDefinition?.id) {
        await putJson(`/api/admin/service-definitions/${editingDefinition.id}`, payload)
        if (isFederatedDefinition(values) && sqlText) {
          await putJson(`/api/admin/service-definitions/${editingDefinition.id}/federated-sql`, {
            federatedSqlText: sqlText,
            sqlComment: '页面自动同步联邦 SQL',
          })
        }
        message.success('服务已更新')
      } else {
        const createResponse = await postJson<ServiceDefinitionDetail, typeof payload>('/api/admin/service-definitions', payload)
        if (isFederatedDefinition(values) && sqlText && createResponse.data.definition.id) {
          await putJson(`/api/admin/service-definitions/${createResponse.data.definition.id}/federated-sql`, {
            federatedSqlText: sqlText,
            sqlComment: '页面自动同步联邦 SQL',
          })
        }
        message.success('服务已创建')
      }
      setDrawerOpen(false)
      await loadDefinitions(selectedId)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  async function handlePublish(id?: number) {
    if (!id) {
      return
    }
    setActionLoading(id)
    try {
      await postJson(`/api/admin/service-definitions/${id}/publish`, {})
      message.success('服务已发布')
      await loadDefinitions(selectedId)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setActionLoading(null)
    }
  }

  async function handleDisable(id?: number) {
    if (!id) {
      return
    }
    try {
      await putJson(`/api/admin/service-definitions/${id}/status`, { status: 'DISABLED' })
      message.success('服务已停用')
      await loadDefinitions(selectedId)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    }
  }

  async function handleSaveSqlDraft(values: SqlDraftForm) {
    if (!selectedId) {
      return
    }
    setSqlSaving(true)
    try {
      await putJson(`/api/admin/service-definitions/${selectedId}/federated-sql`, {
        federatedSqlText: values.federatedSqlText,
        sqlComment: values.sqlComment,
      })
      message.success('SQL 草稿已保存')
      await loadWorkspace(selectedId)
    } catch (err) {
      message.error(resolveErrorMessage(err))
    } finally {
      setSqlSaving(false)
    }
  }

  async function handlePreviewExecute(values: PreviewForm) {
    if (!selectedId || !metadata?.draftSql?.sqlText) {
      return
    }
    setPreviewExecuting(true)
    setPreviewResult(null)
    setPreviewError(null)
    try {
      const params = parseJsonObject(values.paramsText || '{}', '参数')
      const requestBody: FederatedPreviewRequest = {
        federatedSqlText: metadata.draftSql.sqlText,
        params,
      }
      const response = await postJson<PreviewResult, FederatedPreviewRequest>(
        `/api/admin/service-definitions/${selectedId}/federated-preview`,
        requestBody,
      )
      setPreviewResult(response.data)
      message.success('预览执行成功')
    } catch (err) {
      const errorMessage = resolveErrorMessage(err)
      setPreviewError(errorMessage)
      message.error(errorMessage)
    } finally {
      setPreviewExecuting(false)
    }
  }

  return (
    <div className="module-page">
      {/* 页面头部 */}
      <div className="module-hero">
        <div>
          <Typography.Title level={5} style={{ margin: 0 }}>数据服务</Typography.Title>
        </div>
        <div className="service-title-actions">
          <Input
            placeholder="搜索服务..."
            prefix={<SearchOutlined />}
            value={searchKeyword}
            onChange={(e) => {
              setSearchKeyword(e.target.value)
              setCurrentPage(1)
            }}
            allowClear
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => void openEdit(null)}>
            新建服务
          </Button>
        </div>
      </div>

      <div className="datasource-workspace service-workspace">
        <Card className="li-page-main-card service-list-card" data-testid="service-list-card" styles={{ body: { padding: 0 } }}>
          <div className="service-list-panel">
            <div className="service-list-panel-body">
              {loading ? (
                <div className="service-list-panel-empty">
                  <Spin />
                </div>
              ) : filteredDefinitions.length === 0 ? (
                <div className="service-list-panel-empty">
                  <Empty description={definitions.length === 0 ? '还没有数据服务' : '未找到匹配的服务'} />
                </div>
              ) : (
                <>
                  <div className="service-list-table-wrapper">
                    <Table<ServiceDefinition>
                      rowKey={(record) => String(record.id)}
                      columns={listColumns}
                      dataSource={paginatedDefinitions}
                      pagination={false}
                      size="small"
                      scroll={{ x: 760 }}
                      rowClassName={() => 'service-list-row'}
                    />
                  </div>
                  <div className="service-list-pagination-bar">
                    <Pagination
                      current={currentPage}
                      pageSize={pageSize}
                      total={filteredDefinitions.length}
                      onChange={(page, size) => {
                        setCurrentPage(page)
                        if (size && size !== pageSize) {
                          setPageSize(size)
                        }
                      }}
                      showSizeChanger
                      pageSizeOptions={[5, 10, 20, 50]}
                      size="small"
                    />
                  </div>
                </>
              )}
            </div>
          </div>
        </Card>
      </div>

      <Modal
        title="服务详情"
        width={1200}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        destroyOnHidden
        styles={{ body: { maxHeight: '75vh', overflow: 'auto', padding: 24 } }}
      >
        {workspaceLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 320 }}>
            <Spin size="large" />
          </div>
        ) : !detail ? (
          <Empty description="请选择一个数据服务查看详情" style={{ marginTop: 80 }} />
        ) : (
          <div className="detail-content-v1">
              {/* 服务概览卡片 */}
              <Card
                className="overview-card-v1"
                title={<span className="card-title-v1">服务概览</span>}
                extra={<Tag color={isFederatedDefinition(detail.definition) ? 'blue' : 'green'}>{renderSqlTypeText(detail.definition.sqlType)}</Tag>}
                bordered={false}
              >
                <div className="overview-grid-v1">
                  <div className="overview-col-v1">
                    <div className="overview-item-v1">
                      <label>服务编码</label>
                      <span className="mono">{detail.definition.serviceCode}</span>
                    </div>
                    <div className="overview-item-v1">
                      <label>服务名称</label>
                      <span>{detail.definition.serviceName}</span>
                    </div>
                    <div className="overview-item-v1">
                      <label>SQL 类型</label>
                      <span>{renderSqlTypeText(detail.definition.sqlType)}</span>
                    </div>
                  </div>
                  <div className="overview-col-v1">
                    <div className="overview-item-v1">
                      <label>状态</label>
                      <span className="flex items-center gap-2">
                        <span className={`status-dot ${detail.definition.status?.toLowerCase()}`} />
                        {detail.definition.status === 'PUBLISHED'
                          ? '已发布'
                          : detail.definition.status === 'DISABLED'
                            ? '已停用'
                            : '草稿'}
                      </span>
                    </div>
                    <div className="overview-item-v1">
                      <label>服务类型</label>
                      <span>{isFederatedDefinition(detail.definition) ? '联邦服务' : '普通服务'}</span>
                    </div>
                    <div className="overview-item-v1">
                      <label>执行模式</label>
                      <span>{renderExecutionModeLabel(detail.definition.executionMode ?? '')}</span>
                    </div>
                  </div>
                  <div className="overview-col-v1">
                    <div className="overview-item-v1">
                      <label>计划状态</label>
                      <span className="flex items-center gap-2">
                        {detail.definition.planStatus === 'PLANNED' ? (
                          <>
                            <CheckOutlined style={{ color: '#52c41a' }} />
                            <span>{renderPlanStatusLabel(detail.definition.planStatus ?? '')}</span>
                          </>
                        ) : (
                          <span>{renderPlanStatusLabel(detail.definition.planStatus ?? '')}</span>
                        )}
                      </span>
                    </div>
                    <div className="overview-item-v1">
                      <label>当前版本</label>
                      <span>{detail.definition.version ?? 0}</span>
                    </div>
                    <div className="overview-item-v1">
                      <label>资源限制</label>
                      <span>系统统一配置</span>
                    </div>
                  </div>
                </div>
              </Card>

              {/* 来源与参数 - 两列布局 */}
              <div className="two-column-grid-v1">
                <Card
                  className="content-card-v1"
                  title={
                    <span className="card-title-v1">
                      <DatabaseOutlined /> 来源 ({detail.sources.length})
                    </span>
                  }
                  bordered={false}
                >
                  <table className="data-table-v1">
                    <thead>
                      <tr>
                        <th>别名</th>
                        <th>连接</th>
                        <th>类型</th>
                        <th>对象</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.sources.map((source) => (
                        <tr key={source.sourceAlias}>
                          <td><code>{source.sourceAlias}</code></td>
                          <td>{source.connectionId}</td>
                          <td><Tag color="blue">{source.sourceType}</Tag></td>
                          <td><code>{source.sourceValue}</code></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Card>

                <Card
                  className="content-card-v1"
                  title={
                    <span className="card-title-v1">
                      <SettingOutlined /> 参数 ({detail.params.length})
                    </span>
                  }
                  bordered={false}
                >
                  <table className="data-table-v1">
                    <thead>
                      <tr>
                        <th>参数名</th>
                        <th>展示名</th>
                        <th>类型</th>
                        <th>必填</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.params.map((param) => (
                        <tr key={param.paramName}>
                          <td><code>{param.paramName}</code></td>
                          <td>{param.displayName}</td>
                          <td>
                            <Tag color={param.paramType === 'STRING' ? 'green' : param.paramType === 'LONG' ? 'orange' : 'blue'}>
                              {param.paramType}
                            </Tag>
                          </td>
                          <td>
                            {param.required !== false ? (
                              <span style={{ color: '#52c41a' }}>是</span>
                            ) : (
                              <span style={{ color: '#999' }}>否</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Card>
              </div>

              {/* 字段映射 */}
              <Card
                className="content-card-v1"
                title={<span className="card-title-v1"><TableOutlined /> 字段映射 ({detail.fields.length})</span>}
                bordered={false}
              >
                <table className="data-table-v1">
                  <thead>
                    <tr>
                      <th>字段名</th>
                      <th>展示名</th>
                      <th>来源别名</th>
                      <th>来源列</th>
                      <th>类型</th>
                      <th>主键</th>
                      <th>关联键</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.fields.map((field) => (
                      <tr key={`${field.fieldName}-${field.sourceColumn}`}>
                        <td><code>{field.fieldName}</code></td>
                        <td>{field.displayName}</td>
                        <td><code>{field.sourceAlias || '-'}</code></td>
                        <td><code>{field.sourceColumn}</code></td>
                        <td>
                          <Tag color={field.fieldType === 'STRING' ? 'green' : field.fieldType === 'LONG' ? 'orange' : 'blue'}>
                            {field.fieldType}
                          </Tag>
                        </td>
                        <td>
                          {field.primaryKey ? (
                            <span style={{ color: '#52c41a' }}>是</span>
                          ) : (
                            <span style={{ color: '#999' }}>否</span>
                          )}
                        </td>
                        <td>
                          {field.joinKey ? (
                            <span style={{ color: '#52c41a' }}>是</span>
                          ) : (
                            <span style={{ color: '#999' }}>否</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>

              {/* 联邦SQL编辑器 - 仅联邦服务显示 */}
              {isFederatedDefinition(detail.definition) && (
                <Card
                  className="content-card-v1"
                  title={
                    <span className="card-title-v1">
                      <CodeOutlined /> 联邦 SQL
                    </span>
                  }
                  extra={
                    <Space>
                      <Button
                        icon={<PlayCircleOutlined />}
                        loading={previewExecuting}
                        onClick={() => void previewForm.submit()}
                      >
                        预览执行
                      </Button>
                      <Button
                        type="primary"
                        icon={<SaveOutlined />}
                        loading={sqlSaving}
                        onClick={() => void sqlForm.submit()}
                      >
                        保存并校验
                      </Button>
                    </Space>
                  }
                  bordered={false}
                >
                  <Form<SqlDraftForm> form={sqlForm} layout="vertical" onFinish={(values) => void handleSaveSqlDraft(values)}>
                    <Alert
                      type="info"
                      showIcon
                      message="保存后将调用后端联邦 Parser、Validator、Planner、Optimizer，并刷新诊断结果。"
                      style={{ marginBottom: 16 }}
                    />
                    <Form.Item
                      name="federatedSqlText"
                      rules={[{ required: true, message: '请输入联邦 SQL' }]}
                    >
                      <Input.TextArea
                        rows={8}
                        placeholder="SELECT ... FROM mysql_orders JOIN pg_customers ..."
                        className="sql-editor-v1"
                      />
                    </Form.Item>
                    <Form.Item name="sqlComment" label="草稿说明">
                      <Input.TextArea rows={2} placeholder="说明本次联邦 SQL 草稿的变更目的与注意事项" />
                    </Form.Item>
                  </Form>

                  {/* 预览执行表单 */}
                  <Form<PreviewForm> form={previewForm} layout="vertical" onFinish={(values) => void handlePreviewExecute(values)}>
                    <Form.Item
                      name="paramsText"
                      label="预览参数 (JSON)"
                      initialValue="{}"
                    >
                      <Input.TextArea rows={3} placeholder='{"customerId": 123}' />
                    </Form.Item>
                  </Form>

                  {previewError && (
                    <Alert type="error" showIcon message="预览执行失败" description={previewError} style={{ marginTop: 16 }} />
                  )}

                  {previewResult && (
                    <div style={{ marginTop: 16 }}>
                      <Typography.Title level={5}>预览结果</Typography.Title>
                      <Table
                        size="small"
                        scroll={{ x: 'max-content' }}
                        dataSource={previewResult.rows}
                        columns={previewColumns}
                        pagination={false}
                      />
                    </div>
                  )}
                </Card>
              )}

              {/* 折叠面板 */}
              {isFederatedDefinition(detail.definition) && (
                <Collapse
                  className="collapse-v1"
                  items={[
                    {
                      key: 'validate',
                      label: `校验日志 (${metadata?.validateLogs.length ?? 0})`,
                      children: <ValidateLogPanel logs={metadata?.validateLogs ?? []} />,
                    },
                    {
                      key: 'plans',
                      label: `计划与诊断 (${metadata?.plans.length ?? 0})`,
                      children: <PlanPanel plans={metadata?.plans ?? []} />,
                    },
                    {
                      key: 'capability',
                      label: `来源能力 (${capabilityRows.length})`,
                      children: <CapabilityPanel rows={capabilityRows} />,
                    },
                  ]}
                />
              )}
            </div>
          )}
      </Modal>

      <Drawer
        title={editingDefinition ? '编辑服务' : '新建服务'}
        width={980}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void form.submit()}>
              保存草稿
            </Button>
          </Space>
        }
      >
        <Form<ServiceDefinitionUpsertPayload>
          form={form}
          layout="vertical"
          className="compact-form service-create-form"
          onFinish={(values) => void handleSubmit(values)}
        >
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.sqlType !== next.sqlType}>
            {({ getFieldValue }) => {
              const federated = getFieldValue('sqlType') === 'FEDERATED_SQL'
              return (
                <>
                  <div className="service-create-top-grid">
                    <Form.Item label="服务编码" name="serviceCode" rules={[{ required: true }]}>
                      <Input size="small" />
                    </Form.Item>
                    <Form.Item label="服务名称" name="serviceName" rules={[{ required: true }]}>
                      <Input size="small" />
                    </Form.Item>
                    <Form.Item label="SQL 类型" name="sqlType" rules={[{ required: true }]}>
                      <Select
                        size="small"
                        options={[
                          { label: '简单 SQL', value: 'SIMPLE_SQL' },
                          { label: '联邦 SQL', value: 'FEDERATED_SQL' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item label="服务类型" name="serviceType">
                      <Select
                        size="small"
                        disabled
                        options={[
                          {
                            label: federated ? '联邦查询' : '简单查询',
                            value: federated ? 'FEDERATED_QUERY' : 'SIMPLE_QUERY',
                          },
                        ]}
                      />
                    </Form.Item>
                    <Form<AutoDetectForm> form={autoDetectForm} layout="vertical" component={false}>
                      <Form.Item name="defaultConnectionId" label="默认连接">
                        <Select
                          size="small"
                          allowClear
                          showSearch
                          optionFilterProp="label"
                          onChange={handleDefaultConnectionChange}
                          options={connections.map((c) => ({
                            label: `${c.connectionName} (${c.connectionCode})`,
                            value: c.id,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item noStyle shouldUpdate={(prev, next) => prev.defaultConnectionId !== next.defaultConnectionId}>
                        {() => {
                          const defaultConnectionId = autoDetectForm.getFieldValue('defaultConnectionId')
                          const catalogs = defaultConnectionId ? connectionCatalogs[defaultConnectionId] ?? [] : []
                          return (
                            <Form.Item name="defaultCatalogId" label="默认 Schema">
                              <Select
                                size="small"
                                allowClear
                                disabled={!defaultConnectionId}
                                placeholder={catalogs.length > 0 ? '可选默认 Schema' : '未加载 Schema，可留空'}
                                onDropdownVisibleChange={(open) => {
                                  if (open) {
                                    void ensureConnectionCatalogs(defaultConnectionId)
                                  }
                                }}
                                options={catalogs.map((catalog) => ({
                                  label: renderCatalogLabel(catalog),
                                  value: catalog.id,
                                }))}
                              />
                            </Form.Item>
                          )
                        }}
                      </Form.Item>
                    </Form>
                  </div>

                  <Form<AutoDetectForm> form={autoDetectForm} layout="vertical" component={false}>
                    <Form.Item
                      name="draftSqlText"
                      label={
                        <span className="service-create-sql-label">
                          <span>SQL</span>
                          <Tooltip title="粘贴 SQL 后点击解析，系统自动识别数据来源、参数与字段映射。">
                            <Button
                              type="text"
                              size="small"
                              aria-label="查看 SQL 解析说明"
                              icon={<InfoCircleOutlined />}
                            />
                          </Tooltip>
                        </span>
                      }
                      rules={[{ required: true, message: '请输入 SQL' }]}
                    >
                      <SqlHighlightEditor placeholder={`SELECT c.customer_id, o.order_amount
FROM customer_base c
JOIN customer_order o ON c.customer_id = o.customer_id
WHERE 1 = 1
/*%if customerId != null */
  AND c.customer_id = /* customerId */0
/*%end*/`} />
                    </Form.Item>
                  </Form>

                  <Form.Item label="SQL 模板" name="sqlTemplate" hidden>
                    <Input.TextArea rows={4} />
                  </Form.Item>

                  <Form.Item label="备注" name="remark">
                    <Input size="small" />
                  </Form.Item>

                  <div className="service-create-action-row">
                    <Button
                      type="primary"
                      icon={<ReloadOutlined />}
                      loading={detectingSql}
                      onClick={() => void handleAutoDetect()}
                    >
                      SQL 解析
                    </Button>
                  </div>
                </>
              )
            }}
          </Form.Item>

          <Form.List name="sources">
            {(fields, { add, remove }) => (
              <EditableSection
                title="来源定义"
                actionLabel="新增来源"
                emptyText="至少配置一个来源，发布前会强校验。"
                onAdd={() =>
                  add({
                    connectionId: connections[0]?.id ?? 0,
                    catalogId: undefined,
                    sourceAlias: '',
                    sourceType: 'TABLE',
                    sourceValue: '',
                    joinKey: '',
                    configJson: '',
                  })
                }
              >
                {fields.map((field) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`来源 #${field.name + 1}`}
                    extra={
                      <ActionIconButton
                        icon={<DeleteOutlined />}
                        label={`删除来源 ${field.name + 1}`}
                        danger
                        onClick={() => remove(field.name)}
                      />
                    }
                  >
                    <div className="form-grid form-grid-three">
                      <Form.Item label="连接" name={[field.name, 'connectionId']} rules={[{ required: true }]}>
                        <Select
                          size="small"
                          options={connections.map((c) => ({
                            label: `${c.connectionName} (${c.connectionCode})`,
                            value: c.id,
                          }))}
                          onChange={(value) => {
                            void handleSourceConnectionChange(value, field.name)
                          }}
                        />
                      </Form.Item>
                      <Form.Item noStyle shouldUpdate>
                        {() => {
                          const connectionId = form.getFieldValue(['sources', field.name, 'connectionId'])
                          const catalogs = connectionId ? connectionCatalogs[connectionId] ?? [] : []
                          return (
                            <Form.Item label="目标库" name={[field.name, 'catalogId']}>
                              <Select
                                size="small"
                                allowClear
                                disabled={!connectionId}
                                placeholder={connectionId ? '请选择目标库' : '请先选择连接'}
                                onDropdownVisibleChange={(open) => {
                                  if (open) {
                                    void ensureConnectionCatalogs(connectionId)
                                  }
                                }}
                                options={catalogs.map((catalog) => ({
                                  label: renderCatalogLabel(catalog),
                                  value: catalog.id,
                                }))}
                              />
                            </Form.Item>
                          )
                        }}
                      </Form.Item>
                      <Form.Item label="别名" name={[field.name, 'sourceAlias']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="类型" name={[field.name, 'sourceType']} rules={[{ required: true }]}>
                        <Select
                          size="small"
                          options={[
                            { label: '表', value: 'TABLE' },
                            { label: '视图', value: 'VIEW' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="对象" name={[field.name, 'sourceValue']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="关联键" name={[field.name, 'joinKey']}>
                        <Input size="small" placeholder="例如 customer_id" />
                      </Form.Item>
                      <Form.Item label="扩展配置 JSON" name={[field.name, 'configJson']}>
                        <Input size="small" />
                      </Form.Item>
                    </div>
                  </Card>
                ))}
              </EditableSection>
            )}
          </Form.List>

          <Form.List name="params">
            {(fields, { add, remove }) => (
              <EditableSection
                title="参数定义"
                actionLabel="新增参数"
                emptyText="按需配置查询参数。"
                onAdd={() =>
                  add({
                    paramName: '',
                    displayName: '',
                    paramType: 'STRING',
                    sqlPlaceholder: '',
                    required: true,
                    defaultValue: '',
                    sortOrder: fields.length,
                  })
                }
              >
                {fields.map((field) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`参数 #${field.name + 1}`}
                    extra={
                      <ActionIconButton
                        icon={<DeleteOutlined />}
                        label={`删除参数 ${field.name + 1}`}
                        danger
                        onClick={() => remove(field.name)}
                      />
                    }
                  >
                    <div className="form-grid form-grid-three">
                      <Form.Item label="参数名" name={[field.name, 'paramName']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="展示名" name={[field.name, 'displayName']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="类型" name={[field.name, 'paramType']} rules={[{ required: true }]}>
                        <Select
                          size="small"
                          options={[
                            { label: '字符串', value: 'STRING' },
                            { label: '数字', value: 'NUMBER' },
                            { label: '布尔', value: 'BOOLEAN' },
                            { label: '长整型', value: 'LONG' },
                            { label: '列表', value: 'LIST' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="占位符" name={[field.name, 'sqlPlaceholder']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="默认值" name={[field.name, 'defaultValue']}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="排序" name={[field.name, 'sortOrder']} rules={[{ required: true }]}>
                        <InputNumber size="small" style={{ width: '100%' }} min={1} />
                      </Form.Item>
                    </div>
                  </Card>
                ))}
              </EditableSection>
            )}
          </Form.List>

          <Form.List name="fields">
            {(fields, { add, remove }) => (
              <EditableSection
                title="字段映射"
                actionLabel="新增字段"
                emptyText="配置返回字段映射。"
                onAdd={() =>
                  add({
                    sourceAlias: '',
                    sourceColumn: '',
                    fieldName: '',
                    displayName: '',
                    fieldType: 'STRING',
                    sortOrder: fields.length,
                    primaryKey: false,
                    joinKey: false,
                  })
                }
              >
                {fields.map((field) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`字段 #${field.name + 1}`}
                    extra={
                      <ActionIconButton
                        icon={<DeleteOutlined />}
                        label={`删除字段 ${field.name + 1}`}
                        danger
                        onClick={() => remove(field.name)}
                      />
                    }
                  >
                    <div className="form-grid form-grid-three">
                      <Form.Item label="来源别名" name={[field.name, 'sourceAlias']}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="来源列" name={[field.name, 'sourceColumn']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="字段名" name={[field.name, 'fieldName']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="展示名" name={[field.name, 'displayName']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="类型" name={[field.name, 'fieldType']} rules={[{ required: true }]}>
                        <Select
                          size="small"
                          options={[
                            { label: '字符串', value: 'STRING' },
                            { label: '数字', value: 'NUMBER' },
                            { label: '布尔', value: 'BOOLEAN' },
                            { label: '长整型', value: 'LONG' },
                            { label: '小数', value: 'DECIMAL' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="排序" name={[field.name, 'sortOrder']} rules={[{ required: true }]}>
                        <InputNumber size="small" style={{ width: '100%' }} min={1} />
                      </Form.Item>
                    </div>
                  </Card>
                ))}
              </EditableSection>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </div>
  )
}
