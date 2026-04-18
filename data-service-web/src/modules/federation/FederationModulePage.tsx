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
  Select,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd'
import {
  EditOutlined,
  RocketOutlined,
  StopOutlined,
  SaveOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import { getJson, postJson, putJson, resolveErrorMessage } from '../../services/http'
import type {
  ConnectionItem,
  FederatedMetadata,
  FieldItem,
  ParamItem,
  ServiceDefinition,
  ServiceDefinitionDetail,
  ServiceDefinitionUpsertPayload,
  SourceCapabilityItem,
  SourceItem,
  SqlPlanItem,
  SqlValidateLogItem,
} from '../../types/admin'

const DEFAULT_FEDERATED_DEFINITION: ServiceDefinitionUpsertPayload = {
  serviceCode: '',
  serviceName: '',
  serviceType: 'FEDERATED_QUERY',
  status: 'DRAFT',
  sqlTemplate: '',
  sqlType: 'FEDERATED_SQL',
  executionMode: 'REMOTE_PLUS_LOCAL',
  planStatus: 'UNPLANNED',
  currentSqlVersion: 0,
  version: 0,
  maxBatchSize: 20,
  maxResultRows: 200,
  queryTimeoutSeconds: 20,
  federatedQueryTimeoutSeconds: 40,
  remark: '',
  sources: [],
  params: [],
  fields: [],
}

type SqlDraftForm = {
  federatedSqlText: string
  sqlComment?: string
}

type CapabilityRow = SourceCapabilityItem & {
  connectionCode: string
  connectionName: string
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

function renderValidateResultText(result: string) {
  if (result === 'PASS') {
    return <span className="status-text-success">通过</span>
  }
  if (result === 'FAIL') {
    return <span className="status-text-error">失败</span>
  }
  return <span className="status-text">{result}</span>
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

export function FederationModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(true)
  const [workspaceLoading, setWorkspaceLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [sqlSaving, setSqlSaving] = useState(false)
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [definitions, setDefinitions] = useState<ServiceDefinition[]>([])
  const [connections, setConnections] = useState<ConnectionItem[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<ServiceDefinitionDetail | null>(null)
  const [metadata, setMetadata] = useState<FederatedMetadata | null>(null)
  const [capabilities, setCapabilities] = useState<Record<number, SourceCapabilityItem[]>>({})
  const [editingDefinition, setEditingDefinition] = useState<ServiceDefinition | null>(null)
  const [pendingDefinitionValues, setPendingDefinitionValues] = useState<ServiceDefinitionUpsertPayload | null>(null)
  const [definitionForm] = Form.useForm<ServiceDefinitionUpsertPayload>()
  const [sqlForm] = Form.useForm<SqlDraftForm>()

  async function loadDefinitions(nextSelectedId?: number | null) {
    setLoading(true)
    try {
      const [definitionResponse, connectionResponse] = await Promise.all([
        getJson<ServiceDefinition[]>('/api/admin/service-definitions'),
        getJson<ConnectionItem[]>('/api/admin/connections'),
      ])
      const nextDefinitions = definitionResponse.data.filter((item) => item.serviceType === 'FEDERATED_QUERY')
      setDefinitions(nextDefinitions)
      setConnections(connectionResponse.data)
      const effectiveId =
        nextSelectedId ?? (selectedId && nextDefinitions.some((item) => item.id === selectedId) ? selectedId : nextDefinitions[0]?.id)
      setSelectedId(effectiveId ?? null)
    } finally {
      setLoading(false)
    }
  }

  async function loadWorkspace(id: number) {
    setWorkspaceLoading(true)
    try {
      const [detailResponse, metadataResponse] = await Promise.all([
        getJson<ServiceDefinitionDetail>(`/api/admin/service-definitions/${id}`),
        getJson<FederatedMetadata>(`/api/admin/service-definitions/${id}/federated-metadata`),
      ])
      const nextDetail = detailResponse.data
      setDetail(nextDetail)
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
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setDetail(null)
      setMetadata(null)
      setCapabilities({})
      return
    }
    void loadWorkspace(selectedId)
  }, [selectedId, sqlForm])

  useEffect(() => {
    if (!metadata) {
      return
    }
    sqlForm.setFieldsValue({
      federatedSqlText: metadata.draftSql?.sqlText ?? '',
      sqlComment: metadata.draftSql?.sqlComment ?? '',
    })
  }, [metadata, sqlForm])

  useEffect(() => {
    if (!drawerOpen || !pendingDefinitionValues) {
      return
    }
    definitionForm.setFieldsValue(pendingDefinitionValues)
    setPendingDefinitionValues(null)
  }, [definitionForm, drawerOpen, pendingDefinitionValues])

  const columns = useMemo<ColumnsType<ServiceDefinition>>(
    () => [
      { title: '服务编码', dataIndex: 'serviceCode' },
      { title: '服务名称', dataIndex: 'serviceName' },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => renderServiceStatusText(value),
      },
      {
        title: '规划',
        dataIndex: 'planStatus',
        render: (value: string) => renderPlanStatusText(value),
      },
      {
        title: 'SQL 版本',
        dataIndex: 'currentSqlVersion',
        render: (value: number | null | undefined) => value ?? 0,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space size={4}>
            <ActionIconButton
              icon={<EditOutlined />}
              label={`编辑联邦服务 ${record.serviceCode}`}
              onClick={() => void openEdit(record.id ?? null)}
            />
            {record.status === 'DRAFT' ? (
              <ActionIconButton
                icon={<RocketOutlined />}
                label={`发布联邦服务 ${record.serviceCode}`}
                loading={actionLoading === record.id}
                onClick={() => void handlePublish(record.id)}
              />
            ) : null}
            {record.status === 'PUBLISHED' ? (
              <ActionIconButton
                icon={<StopOutlined />}
                label={`停用联邦服务 ${record.serviceCode}`}
                confirmTitle="确认停用该联邦服务？"
                onClick={() => void handleDisable(record.id)}
              />
            ) : null}
          </Space>
        ),
      },
    ],
    [actionLoading],
  )

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

  async function openEdit(id: number | null) {
    if (!id) {
      setEditingDefinition(null)
      setPendingDefinitionValues(DEFAULT_FEDERATED_DEFINITION)
      setDrawerOpen(true)
      return
    }
    const response = await getJson<ServiceDefinitionDetail>(`/api/admin/service-definitions/${id}`)
    const nextDetail = response.data
    setEditingDefinition(nextDetail.definition)
    setPendingDefinitionValues({
      serviceCode: nextDetail.definition.serviceCode,
      serviceName: nextDetail.definition.serviceName,
      serviceType: nextDetail.definition.serviceType,
      status: nextDetail.definition.status,
      sqlTemplate: nextDetail.definition.sqlTemplate ?? '',
      sqlType: nextDetail.definition.sqlType,
      executionMode: nextDetail.definition.executionMode,
      planStatus: nextDetail.definition.planStatus,
      currentSqlVersion: nextDetail.definition.currentSqlVersion ?? 0,
      version: nextDetail.definition.version ?? 0,
      maxBatchSize: nextDetail.definition.maxBatchSize ?? 20,
      maxResultRows: nextDetail.definition.maxResultRows ?? 200,
      queryTimeoutSeconds: nextDetail.definition.queryTimeoutSeconds ?? 20,
      federatedQueryTimeoutSeconds: nextDetail.definition.federatedQueryTimeoutSeconds ?? 40,
      remark: nextDetail.definition.remark ?? '',
      sources: nextDetail.sources.map((item) => ({
        connectionId: item.connectionId,
        catalogId: item.catalogId ?? undefined,
        sourceAlias: item.sourceAlias,
        sourceType: item.sourceType,
        sourceValue: item.sourceValue,
        joinKey: item.joinKey ?? '',
        configJson: item.configJson ?? '',
        status: item.status ?? 'ENABLED',
        remark: item.remark ?? '',
      })),
      params: nextDetail.params.map((item) => ({
        paramName: item.paramName,
        displayName: item.displayName,
        paramType: item.paramType,
        sqlPlaceholder: item.sqlPlaceholder,
        required: item.required ?? true,
        defaultValue: item.defaultValue ?? '',
        sortOrder: item.sortOrder,
        remark: item.remark ?? '',
      })),
      fields: nextDetail.fields.map((item) => ({
        sourceAlias: item.sourceAlias ?? '',
        sourceColumn: item.sourceColumn,
        fieldName: item.fieldName,
        displayName: item.displayName,
        fieldType: item.fieldType,
        sortOrder: item.sortOrder,
        primaryKey: item.primaryKey ?? false,
        joinKey: item.joinKey ?? false,
        remark: item.remark ?? '',
        })),
    })
    setDrawerOpen(true)
  }

  async function handleSubmit(values: ServiceDefinitionUpsertPayload) {
    setSaving(true)
    const payload: ServiceDefinitionUpsertPayload = {
      ...values,
      serviceType: 'FEDERATED_QUERY',
      sqlType: 'FEDERATED_SQL',
      sqlTemplate: values.sqlTemplate ?? '',
    }
    try {
      if (editingDefinition?.id) {
        await putJson<ServiceDefinitionDetail, ServiceDefinitionUpsertPayload>(
          `/api/admin/service-definitions/${editingDefinition.id}`,
          payload,
        )
        message.success('联邦服务草稿已更新')
        await loadDefinitions(editingDefinition.id)
        await loadWorkspace(editingDefinition.id)
      } else {
        const response = await postJson<ServiceDefinitionDetail, ServiceDefinitionUpsertPayload>(
          '/api/admin/service-definitions',
          payload,
        )
        const nextId = response.data.definition.id ?? null
        message.success('联邦服务草稿已创建')
        await loadDefinitions(nextId)
        if (nextId) {
          await loadWorkspace(nextId)
        }
      }
      setDrawerOpen(false)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  async function handleSaveSqlDraft(values: SqlDraftForm) {
    if (!selectedId) {
      return
    }
    setSqlSaving(true)
    try {
      await putJson<FederatedMetadata, SqlDraftForm>(`/api/admin/service-definitions/${selectedId}/federated-sql`, values)
      message.success('联邦 SQL 草稿已保存并完成校验规划')
      await loadDefinitions(selectedId)
      await loadWorkspace(selectedId)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSqlSaving(false)
    }
  }

  async function handlePublish(id?: number) {
    if (!id) {
      return
    }
    setActionLoading(id)
    try {
      await postJson<ServiceDefinitionDetail, undefined>(`/api/admin/service-definitions/${id}/publish`)
      message.success('联邦服务已发布')
      await loadDefinitions(id)
      await loadWorkspace(id)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setActionLoading(null)
    }
  }

  async function handleDisable(id?: number) {
    if (!id) {
      return
    }
    setActionLoading(id)
    try {
      await putJson<ServiceDefinitionDetail, { status: string }>(`/api/admin/service-definitions/${id}/status`, {
        status: 'DISABLED',
      })
      message.success('联邦服务已停用')
      await loadDefinitions(id)
      await loadWorkspace(id)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setActionLoading(null)
    }
  }

  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={5}>联邦 SQL 平台</Typography.Title>
        </div>
        <Space>
          <Button onClick={() => selectedId && void loadWorkspace(selectedId)} disabled={!selectedId}>
            刷新诊断
          </Button>
          <Button type="primary" onClick={() => void openEdit(null)}>
            新建联邦服务
          </Button>
        </Space>
      </div>

      <div className="federation-layout">
        <Card title="联邦服务列表" className="li-page-main-card">
          {loading ? (
            <Spin />
          ) : (
            <Table
              rowKey={(record) => String(record.id)}
              columns={columns}
              dataSource={definitions}
              pagination={false}
              size="small"
              locale={{
                emptyText: <Empty description="还没有联邦服务，先创建一个草稿" />,
              }}
              onRow={(record) => ({
                onClick: () => {
                  if (record.id) {
                    setSelectedId(record.id)
                  }
                },
              })}
              rowClassName={(record) => (record.id === selectedId ? 'li-table-row-selected' : '')}
            />
          )}
        </Card>

        <div className="federation-stack">
          {workspaceLoading ? (
            <Card className="li-page-main-card federation-empty-card">
              <Spin size="large" />
            </Card>
          ) : !detail || !metadata ? (
            <Card className="li-page-main-card federation-empty-card">
              <Empty description="请选择一个联邦服务查看工作区" />
            </Card>
          ) : (
            <>
              <Card title="联邦服务概览" className="federation-plan-card">
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  <Descriptions size="small" column={2} bordered>
                    <Descriptions.Item label="服务编码">{detail.definition.serviceCode}</Descriptions.Item>
                    <Descriptions.Item label="状态">{renderServiceStatusText(detail.definition.status)}</Descriptions.Item>
                    <Descriptions.Item label="计划状态">{renderPlanStatusText(detail.definition.planStatus)}</Descriptions.Item>
                    <Descriptions.Item label="执行模式">{detail.definition.executionMode}</Descriptions.Item>
                    <Descriptions.Item label="SQL 版本">{detail.definition.currentSqlVersion ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="联邦超时">{detail.definition.federatedQueryTimeoutSeconds ?? '-'} 秒</Descriptions.Item>
                    <Descriptions.Item label="来源数">{detail.sources.length}</Descriptions.Item>
                    <Descriptions.Item label="参数数">{detail.params.length}</Descriptions.Item>
                    <Descriptions.Item label="字段数">{detail.fields.length}</Descriptions.Item>
                    <Descriptions.Item label="备注">{detail.definition.remark || '-'}</Descriptions.Item>
                  </Descriptions>

                  <div className="federation-summary-grid">
                    <div className="federation-summary-card">
                      <Typography.Text type="secondary">最近草稿版本</Typography.Text>
                      <Typography.Title level={4}>{metadata.draftSql?.version ?? 0}</Typography.Title>
                    </div>
                    <div className="federation-summary-card">
                      <Typography.Text type="secondary">校验日志条数</Typography.Text>
                      <Typography.Title level={4}>{metadata.validateLogs.length}</Typography.Title>
                    </div>
                    <div className="federation-summary-card">
                      <Typography.Text type="secondary">计划产物条数</Typography.Text>
                      <Typography.Title level={4}>{metadata.plans.length}</Typography.Title>
                    </div>
                  </div>
                </Space>
              </Card>

              <Card
                title="联邦 SQL 草稿"
                extra={
                  <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    loading={sqlSaving}
                    onClick={() => void sqlForm.submit()}
                  >
                    保存并校验
                  </Button>
                }
                className="federation-plan-card"
              >
                <Form<SqlDraftForm> form={sqlForm} layout="vertical" onFinish={(values) => void handleSaveSqlDraft(values)}>
                  <div className="federation-toolbar">
                    <Typography.Text type="secondary">
                      保存后将调用后端联邦 Parser、Validator、Planner、Optimizer，并刷新诊断结果。
                    </Typography.Text>
                    <span className="status-text">{detail.definition.executionMode}</span>
                  </div>
                  <Form.Item
                    label="联邦 SQL"
                    name="federatedSqlText"
                    rules={[{ required: true, message: '请输入联邦 SQL' }]}
                    className="federation-sql-editor"
                  >
                    <Input.TextArea rows={8} placeholder="SELECT ... FROM mysql_orders JOIN pg_customers ..." />
                  </Form.Item>
                  <Form.Item label="草稿说明" name="sqlComment">
                    <Input.TextArea rows={2} placeholder="说明本次联邦 SQL 草稿的变更目的与注意事项" />
                  </Form.Item>
                </Form>
              </Card>

              <Collapse
                items={[
                  {
                    key: 'validate',
                    label: `校验日志 (${metadata.validateLogs.length})`,
                    children: <ValidateLogPanel logs={metadata.validateLogs} />,
                  },
                  {
                    key: 'plans',
                    label: `计划与诊断 (${metadata.plans.length})`,
                    children: <PlanPanel plans={metadata.plans} />,
                  },
                  {
                    key: 'capability',
                    label: `来源能力 (${capabilityRows.length})`,
                    children: <CapabilityPanel rows={capabilityRows} />,
                  },
                ]}
              />
            </>
          )}
        </div>
      </div>

      <Drawer
        title={editingDefinition ? `编辑联邦服务 · ${editingDefinition.serviceCode}` : '新建联邦服务'}
        width={800}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void definitionForm.submit()}>
              保存草稿
            </Button>
          </Space>
        }
      >
        <Form<ServiceDefinitionUpsertPayload>
          form={definitionForm}
          layout="vertical"
          className="compact-form"
          onFinish={(values) => void handleSubmit(values)}
        >
          <div className="form-grid form-grid-two">
            <Form.Item label="服务编码" name="serviceCode" rules={[{ required: true }]}>
              <Input size="small" />
            </Form.Item>
            <Form.Item label="服务名称" name="serviceName" rules={[{ required: true }]}>
              <Input size="small" />
            </Form.Item>
            <Form.Item label="服务类型" name="serviceType">
              <Select size="small" disabled options={[{ label: '联邦查询', value: 'FEDERATED_QUERY' }]} />
            </Form.Item>
            <Form.Item label="SQL 类型" name="sqlType">
              <Select size="small" disabled options={[{ label: '联邦 SQL', value: 'FEDERATED_SQL' }]} />
            </Form.Item>
            <Form.Item label="执行模式" name="executionMode" rules={[{ required: true }]}>
              <Select
                size="small"
                options={[
                  { label: '远端优先 + 本地补算', value: 'REMOTE_PLUS_LOCAL' },
                  { label: '远端执行', value: 'REMOTE_ONLY' },
                ]}
              />
            </Form.Item>
            <Form.Item label="计划状态" name="planStatus" rules={[{ required: true }]}>
              <Select
                size="small"
                options={[
                  { label: '未规划', value: 'UNPLANNED' },
                  { label: '已规划', value: 'PLANNED' },
                  { label: '已发布', value: 'PUBLISHED' },
                ]}
              />
            </Form.Item>
            <Form.Item label="批量上限" name="maxBatchSize">
              <InputNumber size="small" style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item label="结果上限" name="maxResultRows">
              <InputNumber size="small" style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item label="查询超时（秒）" name="queryTimeoutSeconds">
              <InputNumber size="small" style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item label="联邦超时（秒）" name="federatedQueryTimeoutSeconds">
              <InputNumber size="small" style={{ width: '100%' }} min={1} />
            </Form.Item>
          </div>

          <Form.Item label="说明备注" name="remark">
            <Input size="small" />
          </Form.Item>

          <Form.List name="sources">
            {(fields, { add, remove }) => (
              <EditableSection
                title="来源定义"
                actionLabel="新增来源"
                emptyText="联邦服务至少需要配置一个来源；后续 SQL 校验会识别涉及的数据源。"
                onAdd={() =>
                  add({
                    connectionId: connections[0]?.id ?? 0,
                    catalogId: undefined,
                    sourceAlias: '',
                    sourceType: 'TABLE',
                    sourceValue: '',
                    joinKey: '',
                    configJson: '',
                    status: 'ENABLED',
                    remark: '',
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
                        label={`删除联邦来源 ${field.name + 1}`}
                        danger
                        onClick={() => remove(field.name)}
                      />
                    }
                  >
                    <div className="form-grid form-grid-three">
                      <Form.Item label="连接" name={[field.name, 'connectionId']} rules={[{ required: true }]}>
                        <Select
                          size="small"
                          options={connections.map((item) => ({
                            label: `${item.connectionName} (${item.connectionCode})`,
                            value: item.id,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item label="catalogId" name={[field.name, 'catalogId']}>
                        <InputNumber size="small" style={{ width: '100%' }} min={1} />
                      </Form.Item>
                      <Form.Item label="来源别名" name={[field.name, 'sourceAlias']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="来源类型" name={[field.name, 'sourceType']} rules={[{ required: true }]}>
                        <Select size="small" options={[{ label: '表', value: 'TABLE' }]} />
                      </Form.Item>
                      <Form.Item label="来源值" name={[field.name, 'sourceValue']} rules={[{ required: true }]}>
                        <Input size="small" />
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
                emptyText="联邦 SQL 发布前必须至少配置一个参数。"
                onAdd={() =>
                  add({
                    paramName: '',
                    displayName: '',
                    paramType: 'STRING',
                    sqlPlaceholder: '',
                    required: true,
                    defaultValue: '',
                    sortOrder: fields.length + 1,
                    remark: '',
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
                        label={`删除联邦参数 ${field.name + 1}`}
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
                      <Form.Item label="参数类型" name={[field.name, 'paramType']} rules={[{ required: true }]}>
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
                      <Form.Item label="SQL 占位符" name={[field.name, 'sqlPlaceholder']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="排序" name={[field.name, 'sortOrder']} rules={[{ required: true }]}>
                        <InputNumber size="small" style={{ width: '100%' }} min={1} />
                      </Form.Item>
                      <Form.Item label="默认值" name={[field.name, 'defaultValue']}>
                        <Input size="small" />
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
                title="返回字段"
                actionLabel="新增字段"
                emptyText="联邦 SQL 发布前必须至少配置一个返回字段。"
                onAdd={() =>
                  add({
                    sourceAlias: '',
                    sourceColumn: '',
                    fieldName: '',
                    displayName: '',
                    fieldType: 'STRING',
                    sortOrder: fields.length + 1,
                    primaryKey: false,
                    joinKey: false,
                    remark: '',
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
                        label={`删除联邦字段 ${field.name + 1}`}
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
                      <Form.Item label="字段类型" name={[field.name, 'fieldType']} rules={[{ required: true }]}>
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

function ValidateLogPanel({ logs }: { logs: SqlValidateLogItem[] }) {
  return (
    <Table<SqlValidateLogItem>
      rowKey={(record) => `${record.id ?? record.validateStage}-${record.version ?? 0}`}
      size="small"
      pagination={false}
      dataSource={logs}
      locale={{ emptyText: '还没有校验日志，先保存一次联邦 SQL 草稿。' }}
      columns={[
        { title: '阶段', dataIndex: 'validateStage' },
        {
          title: '结果',
          dataIndex: 'result',
          render: (value: string) => renderValidateResultText(value),
        },
        { title: '说明', dataIndex: 'message' },
        { title: '版本', dataIndex: 'version' },
        { title: '创建人', dataIndex: 'createdBy' },
        { title: '时间', dataIndex: 'createdAt' },
        {
          title: '详情',
          dataIndex: 'detailJson',
          render: (value: string | null | undefined) => (
            <Typography.Text type="secondary">{value ? '见下方 JSON' : '-'}</Typography.Text>
          ),
        },
      ]}
      expandable={{
        expandedRowRender: (record) => (
          <div className="json-block">
            <pre>{safePrettyJson(record.detailJson)}</pre>
          </div>
        ),
      }}
    />
  )
}

function PlanPanel({ plans }: { plans: SqlPlanItem[] }) {
  if (plans.length === 0) {
    return <Empty description="还没有计划产物，先保存一次联邦 SQL 草稿。" />
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {plans.map((plan) => (
        <Card key={`${plan.id ?? plan.planStage}-${plan.version ?? 0}`} size="small" title={`阶段 · ${plan.planStage}`}>
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="版本">{plan.version ?? 0}</Descriptions.Item>
            <Descriptions.Item label="数据源范围">{plan.datasourceScope ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="格式">{plan.planFormat ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建人">{plan.createdBy ?? '-'}</Descriptions.Item>
          </Descriptions>
          <Space direction="vertical" size={12} style={{ width: '100%', marginTop: 12 }}>
            <div className="json-block">
              <pre>{safePrettyJson(plan.planContent)}</pre>
            </div>
            <div className="detail-split-grid">
              <div className="json-block">
                <pre>{safePrettyJson(plan.pushdownSummary)}</pre>
              </div>
              <div className="json-block">
                <pre>{safePrettyJson(plan.costSummary)}</pre>
              </div>
            </div>
            <div className="detail-split-grid">
              <div className="json-block">
                <pre>{safePrettyJson(plan.stageGraphJson)}</pre>
              </div>
              <div className="json-block">
                <pre>{safePrettyJson(plan.localExecutionSummary)}</pre>
              </div>
            </div>
            <div className="json-block">
              <pre>{safePrettyJson(plan.fallbackReason)}</pre>
            </div>
          </Space>
        </Card>
      ))}
    </Space>
  )
}

function CapabilityPanel({ rows }: { rows: CapabilityRow[] }) {
  return (
    <Table<CapabilityRow>
      rowKey={(record) => `${record.connectionId}-${record.capabilityCode}-${record.capabilityValue}`}
      size="small"
      pagination={false}
      dataSource={rows}
      locale={{ emptyText: '当前来源连接还没有录入能力矩阵。' }}
      columns={[
        { title: '连接', render: (_, record) => `${record.connectionName} (${record.connectionCode})` },
        { title: '数据库', dataIndex: 'dbType' },
        { title: '能力项', dataIndex: 'capabilityCode' },
        { title: '能力值', dataIndex: 'capabilityValue' },
        { title: '范围', dataIndex: 'scope' },
        {
          title: '启用',
          dataIndex: 'enabled',
          render: (value: boolean | null | undefined) => (value === false ? '否' : '是'),
        },
        { title: '备注', dataIndex: 'remark' },
      ]}
      expandable={{
        expandedRowRender: (record) => (
          <div className="json-block">
            <pre>{safePrettyJson(record.capabilityDetailJson)}</pre>
          </div>
        ),
      }}
    />
  )
}

type EditableSectionProps = {
  title: string
  actionLabel: string
  emptyText: string
  onAdd: () => void
  children: ReactNode
}

function EditableSection({ title, actionLabel, emptyText, onAdd, children }: EditableSectionProps) {
  const childArray = Array.isArray(children) ? children : [children]
  const hasItems = childArray.some(Boolean)

  return (
    <Space direction="vertical" size={12} style={{ width: '100%', marginBottom: 16 }}>
      <div className="section-header">
        <Typography.Title level={5}>{title}</Typography.Title>
        <Button size="small" onClick={onAdd}>{actionLabel}</Button>
      </div>
      {!hasItems ? <Alert type="info" showIcon message={emptyText} /> : null}
      {children}
    </Space>
  )
}
