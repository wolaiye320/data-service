import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
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
  DeleteOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import { getJson, postJson, putJson, resolveErrorMessage } from '../../services/http'
import type {
  ConnectionItem,
  FieldItem,
  ParamItem,
  ServiceDefinition,
  ServiceDefinitionDetail,
  ServiceDefinitionUpsertPayload,
  ServiceVersionItem,
  SourceItem,
} from '../../types/admin'

const DEFAULT_DEFINITION: ServiceDefinitionUpsertPayload = {
  serviceCode: '',
  serviceName: '',
  serviceType: 'SIMPLE_QUERY',
  status: 'DRAFT',
  sqlTemplate: 'select 1 as demo_value',
  sqlType: 'SIMPLE_SQL',
  executionMode: 'REMOTE_ONLY',
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

function renderServiceStatusText(status: string) {
  if (status === 'PUBLISHED') {
    return <span className="status-text-success">已发布</span>
  }
  if (status === 'DISABLED') {
    return <span className="status-text">已停用</span>
  }
  return <span className="status-text">草稿</span>
}

export function ServiceModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [definitions, setDefinitions] = useState<ServiceDefinition[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<ServiceDefinitionDetail | null>(null)
  const [versions, setVersions] = useState<ServiceVersionItem[]>([])
  const [connections, setConnections] = useState<ConnectionItem[]>([])
  const [editingDefinition, setEditingDefinition] = useState<ServiceDefinition | null>(null)
  const [form] = Form.useForm<ServiceDefinitionUpsertPayload>()

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
        nextSelectedId ?? (selectedId && nextDefinitions.some((item) => item.id === selectedId) ? selectedId : nextDefinitions[0]?.id)
      setSelectedId(effectiveId ?? null)
    } finally {
      setLoading(false)
    }
  }

  async function loadDetail(id: number) {
    const [detailResponse, versionsResponse] = await Promise.all([
      getJson<ServiceDefinitionDetail>(`/api/admin/service-definitions/${id}`),
      getJson<ServiceVersionItem[]>(`/api/admin/service-definitions/${id}/versions`),
    ])
    setDetail(detailResponse.data)
    setVersions(versionsResponse.data)
  }

  useEffect(() => {
    void loadDefinitions()
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setDetail(null)
      setVersions([])
      return
    }
    void loadDetail(selectedId)
  }, [selectedId])

  const columns = useMemo<ColumnsType<ServiceDefinition>>(
    () => [
      {
        title: '服务编码',
        dataIndex: 'serviceCode',
      },
      {
        title: '名称',
        dataIndex: 'serviceName',
      },
      {
        title: '类型',
        dataIndex: 'serviceType',
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => renderServiceStatusText(value),
      },
      {
        title: '版本',
        dataIndex: 'version',
        render: (value: number | null | undefined) => value ?? 0,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space size={4}>
            <ActionIconButton
              icon={<EditOutlined />}
              label={`编辑服务 ${record.serviceCode}`}
              onClick={() => void openEdit(record.id ?? null)}
            />
            {record.status === 'DRAFT' ? (
              <ActionIconButton
                icon={<RocketOutlined />}
                label={`发布服务 ${record.serviceCode}`}
                loading={actionLoading === record.id}
                onClick={() => void handlePublish(record.id)}
              />
            ) : null}
            {record.status === 'PUBLISHED' ? (
              <ActionIconButton
                icon={<StopOutlined />}
                label={`停用服务 ${record.serviceCode}`}
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
      setDrawerOpen(true)
      return
    }
    const response = await getJson<ServiceDefinitionDetail>(`/api/admin/service-definitions/${id}`)
    const nextDetail = response.data
    setEditingDefinition(nextDetail.definition)
    form.setFieldsValue({
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
    try {
      if (editingDefinition?.id) {
        await putJson<ServiceDefinitionDetail, ServiceDefinitionUpsertPayload>(
          `/api/admin/service-definitions/${editingDefinition.id}`,
          values,
        )
        message.success('服务草稿已更新')
        await loadDefinitions(editingDefinition.id)
        await loadDetail(editingDefinition.id)
      } else {
        const response = await postJson<ServiceDefinitionDetail, ServiceDefinitionUpsertPayload>(
          '/api/admin/service-definitions',
          values,
        )
        message.success('服务草稿已创建')
        await loadDefinitions(response.data.definition.id ?? null)
      }
      setDrawerOpen(false)
    } catch (error) {
      message.error(resolveErrorMessage(error))
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
      await postJson<ServiceDefinitionDetail, undefined>(`/api/admin/service-definitions/${id}/publish`)
      message.success('服务已发布')
      await loadDefinitions(id)
      await loadDetail(id)
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
      await putJson<ServiceDefinitionDetail, { status: string }>(
        `/api/admin/service-definitions/${id}/status`,
        { status: 'DISABLED' },
      )
      message.success('服务已停用')
      await loadDefinitions(id)
      await loadDetail(id)
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
          <Typography.Title level={5}>数据服务配置与发布</Typography.Title>
          <Typography.Paragraph type="secondary">
            当前页覆盖服务草稿、来源、参数、字段、SQL、发布、停用与版本查看主流程。
          </Typography.Paragraph>
        </div>
        <Button type="primary" onClick={() => void openEdit(null)}>
          新建服务草稿
        </Button>
      </div>

      <div className="module-grid">
        <Card title="服务列表" className="li-page-main-card">
          {loading ? (
            <Spin />
          ) : (
            <Table
              rowKey={(record) => String(record.id)}
              columns={columns}
              dataSource={definitions}
              pagination={false}
              size="small"
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

        <Card title="服务详情与版本" className="li-page-main-card">
          {!detail ? (
            <Alert type="info" showIcon message="请选择一个服务查看详情" />
          ) : (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions size="small" column={2} bordered>
                <Descriptions.Item label="服务编码">{detail.definition.serviceCode}</Descriptions.Item>
                <Descriptions.Item label="状态">{renderServiceStatusText(detail.definition.status)}</Descriptions.Item>
                <Descriptions.Item label="服务类型">{detail.definition.serviceType}</Descriptions.Item>
                <Descriptions.Item label="执行模式">{detail.definition.executionMode}</Descriptions.Item>
                <Descriptions.Item label="SQL 类型">{detail.definition.sqlType}</Descriptions.Item>
                <Descriptions.Item label="计划状态">{detail.definition.planStatus}</Descriptions.Item>
                <Descriptions.Item label="批量上限">{detail.definition.maxBatchSize ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="结果上限">{detail.definition.maxResultRows ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="查询超时">{detail.definition.queryTimeoutSeconds ?? '-'} 秒</Descriptions.Item>
                <Descriptions.Item label="联邦超时">
                  {detail.definition.federatedQueryTimeoutSeconds ?? '-'} 秒
                </Descriptions.Item>
                <Descriptions.Item label="SQL" span={2}>
                  <Typography.Text code>{detail.definition.sqlTemplate || '未配置'}</Typography.Text>
                </Descriptions.Item>
              </Descriptions>

              <div className="detail-split-grid">
                <Card size="small" title={`来源 (${detail.sources.length})`}>
                  <Table<SourceItem>
                    rowKey={(record) => `${record.sourceAlias}-${record.sourceValue}`}
                    size="small"
                    pagination={false}
                    dataSource={detail.sources}
                    columns={[
                      { title: '别名', dataIndex: 'sourceAlias' },
                      { title: '连接', dataIndex: 'connectionId' },
                      { title: '类型', dataIndex: 'sourceType' },
                      { title: '对象', dataIndex: 'sourceValue' },
                    ]}
                  />
                </Card>
                <Card size="small" title={`参数 (${detail.params.length})`}>
                  <Table<ParamItem>
                    rowKey={(record) => record.paramName}
                    size="small"
                    pagination={false}
                    dataSource={detail.params}
                    columns={[
                      { title: '参数名', dataIndex: 'paramName' },
                      { title: '类型', dataIndex: 'paramType' },
                      { title: '占位符', dataIndex: 'sqlPlaceholder' },
                      {
                        title: '必填',
                        dataIndex: 'required',
                        render: (value: boolean | undefined) => (value === false ? '否' : '是'),
                      },
                    ]}
                  />
                </Card>
              </div>

              <Card size="small" title={`字段映射 (${detail.fields.length})`}>
                <Table<FieldItem>
                  rowKey={(record) => `${record.fieldName}-${record.sourceColumn}`}
                  size="small"
                  pagination={false}
                  dataSource={detail.fields}
                  columns={[
                    { title: '字段名', dataIndex: 'fieldName' },
                    { title: '展示名', dataIndex: 'displayName' },
                    { title: '来源列', dataIndex: 'sourceColumn' },
                    { title: '类型', dataIndex: 'fieldType' },
                    {
                      title: '主键',
                      dataIndex: 'primaryKey',
                      render: (value: boolean | undefined) => (value ? '是' : '否'),
                    },
                    {
                      title: '关联键',
                      dataIndex: 'joinKey',
                      render: (value: boolean | undefined) => (value ? '是' : '否'),
                    },
                  ]}
                />
              </Card>

              <Card size="small" title={`版本记录 (${versions.length})`}>
                <Table<ServiceVersionItem>
                  rowKey={(record) => record.id}
                  size="small"
                  pagination={false}
                  dataSource={versions}
                  columns={[
                    { title: '版本', dataIndex: 'version' },
                    {
                      title: '状态',
                      dataIndex: 'status',
                      render: (value: string) => renderServiceStatusText(value),
                    },
                    { title: '创建人', dataIndex: 'createdBy' },
                    { title: '创建时间', dataIndex: 'createdAt' },
                  ]}
                />
              </Card>
            </Space>
          )}
        </Card>
      </div>

      <Drawer
        title={editingDefinition ? `编辑服务 · ${editingDefinition.serviceCode}` : '新建服务草稿'}
        width={800}
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
            <Form.Item label="服务类型" name="serviceType" rules={[{ required: true }]}>
              <Select size="small" options={[{ label: '简单查询', value: 'SIMPLE_QUERY' }]} />
            </Form.Item>
            <Form.Item label="SQL 类型" name="sqlType" rules={[{ required: true }]}>
              <Select size="small" options={[{ label: '简单 SQL', value: 'SIMPLE_SQL' }]} />
            </Form.Item>
            <Form.Item label="执行模式" name="executionMode" rules={[{ required: true }]}>
              <Select size="small" options={[{ label: '远端执行', value: 'REMOTE_ONLY' }]} />
            </Form.Item>
            <Form.Item label="计划状态" name="planStatus" rules={[{ required: true }]}>
              <Select
                size="small"
                options={[
                  { label: '未规划', value: 'UNPLANNED' },
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

          <Form.Item label="SQL 模板" name="sqlTemplate">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input size="small" />
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
                          options={connections.map((c) => ({ label: c.connectionName, value: c.id }))}
                        />
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
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="状态" name={[field.name, 'status']}>
                        <Select
                          size="small"
                          options={[
                            { label: '已启用', value: 'ENABLED' },
                            { label: '已停用', value: 'DISABLED' },
                          ]}
                        />
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
                            { label: '整数', value: 'INTEGER' },
                            { label: '长整数', value: 'LONG' },
                            { label: '小数', value: 'DECIMAL' },
                            { label: '布尔', value: 'BOOLEAN' },
                            { label: '日期', value: 'DATE' },
                            { label: '时间戳', value: 'DATETIME' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="占位符" name={[field.name, 'sqlPlaceholder']} rules={[{ required: true }]}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="默认值" name={[field.name, 'defaultValue']}>
                        <Input size="small" />
                      </Form.Item>
                      <Form.Item label="排序" name={[field.name, 'sortOrder']}>
                        <InputNumber size="small" style={{ width: '100%' }} />
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
                            { label: '整数', value: 'INTEGER' },
                            { label: '长整数', value: 'LONG' },
                            { label: '小数', value: 'DECIMAL' },
                            { label: '布尔', value: 'BOOLEAN' },
                            { label: '日期', value: 'DATE' },
                            { label: '时间戳', value: 'DATETIME' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="排序" name={[field.name, 'sortOrder']}>
                        <InputNumber size="small" style={{ width: '100%' }} />
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
