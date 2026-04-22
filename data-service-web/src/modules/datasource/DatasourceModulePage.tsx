import { useEffect, useMemo, useState } from 'react'
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
  EyeOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import { deleteJson, getJson, postJson, putJson, resolveErrorMessage } from '../../services/http'
import type { CatalogItem, ConnectionDetail, ConnectionItem, ConnectionUpsertPayload } from '../../types/admin'

const DEFAULT_CONNECTION: ConnectionUpsertPayload = {
  connectionCode: '',
  connectionName: '',
  dbType: 'POSTGRESQL',
  host: '127.0.0.1',
  port: 5432,
  username: '',
  passwordCiphertext: '',
  status: 'ENABLED',
  remark: '',
  connectionConfigJson: '{\n  "database": ""\n}',
  catalogs: [],
}

const DB_TYPE_PORT: Record<string, number> = {
  POSTGRESQL: 5432,
  MYSQL: 3306,
  ORACLE: 1521,
}

const CATALOG_RULES: Record<string, { defaultType: string; valueLabel: string; placeholder: string; emptyText: string }> = {
  POSTGRESQL: {
    defaultType: 'SCHEMA',
    valueLabel: 'Schema 名称',
    placeholder: '例如 public / analytics',
    emptyText: 'PostgreSQL 默认按 Schema 管理目标库，可新增后填写 Schema 名称。',
  },
  MYSQL: {
    defaultType: 'DATABASE',
    valueLabel: 'Database 名称',
    placeholder: '例如 crm / order_center',
    emptyText: 'MySQL 默认按 Database 管理目标库，可新增后填写 Database 名称。',
  },
  ORACLE: {
    defaultType: 'DATABASE',
    valueLabel: '目标库值',
    placeholder: '例如 serviceName 对应值',
    emptyText: 'Oracle 暂保留兼容配置，可按需填写目标库值。',
  },
}

function getCatalogRule(dbType?: string) {
  return CATALOG_RULES[dbType ?? 'POSTGRESQL'] ?? CATALOG_RULES.POSTGRESQL
}

function getCatalogTypeOptions(dbType?: string) {
  const normalizedDbType = (dbType ?? 'POSTGRESQL').trim().toUpperCase()
  if (normalizedDbType === 'MYSQL') {
    return [{ label: 'DATABASE', value: 'DATABASE' }]
  }
  if (normalizedDbType === 'ORACLE') {
    return [
      { label: 'DATABASE', value: 'DATABASE' },
      { label: 'SCHEMA', value: 'SCHEMA' },
    ]
  }
  return [{ label: 'SCHEMA', value: 'SCHEMA' }]
}

function normalizeCatalogTypeForDb(dbType?: string, catalogType?: string) {
  const normalizedType = catalogType?.trim().toUpperCase()
  const allowedTypes = getCatalogTypeOptions(dbType).map((option) => option.value)
  if (normalizedType && allowedTypes.includes(normalizedType)) {
    return normalizedType
  }
  return allowedTypes[0] ?? normalizedType ?? ''
}

function buildCatalogDraft(dbType?: string): CatalogItem {
  return {
    catalogType: normalizeCatalogTypeForDb(dbType),
    catalogValue: '',
  }
}

function normalizeCatalogPayload(values: ConnectionUpsertPayload): ConnectionUpsertPayload {
  return {
    ...values,
    catalogs: (values.catalogs ?? []).map((catalog) => {
      const catalogValue = catalog.catalogValue?.trim() ?? ''
      const catalogType = normalizeCatalogTypeForDb(values.dbType, catalog.catalogType)
      return {
        ...catalog,
        catalogType,
        catalogValue,
      }
    }),
  }
}

function renderStatusText(status: string) {
  if (status === 'ENABLED') {
    return <span className="status-text-success">已启用</span>
  }
  if (status === 'DISABLED') {
    return <span className="status-text">已停用</span>
  }
  return <span className="status-text">{status}</span>
}

export function DatasourceModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testingForm, setTestingForm] = useState(false)
  const [submittingAction, setSubmittingAction] = useState<number | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false)
  const [connections, setConnections] = useState<ConnectionItem[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<ConnectionDetail | null>(null)
  const [editingConnection, setEditingConnection] = useState<ConnectionItem | null>(null)
  const [form] = Form.useForm<ConnectionUpsertPayload>()
  const currentDbType = Form.useWatch('dbType', form) ?? DEFAULT_CONNECTION.dbType
  const currentCatalogs = Form.useWatch('catalogs', form) ?? []

  async function loadConnections(nextSelectedId?: number | null) {
    setLoading(true)
    try {
      const response = await getJson<ConnectionItem[]>('/api/admin/connections')
      const nextConnections = response.data
      setConnections(nextConnections)
      const effectiveId =
        nextSelectedId ?? (selectedId && nextConnections.some((item) => item.id === selectedId) ? selectedId : nextConnections[0]?.id)
      setSelectedId(effectiveId ?? null)
    } finally {
      setLoading(false)
    }
  }

  async function loadDetail(id: number) {
    const response = await getJson<ConnectionDetail>(`/api/admin/connections/${id}`)
    setDetail(response.data)
  }

  useEffect(() => {
    void loadConnections()
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setDetail(null)
      return
    }
    void loadDetail(selectedId)
  }, [selectedId])

  const columns = useMemo<ColumnsType<ConnectionItem>>(
    () => [
      {
        title: '连接编码',
        dataIndex: 'connectionCode',
        key: 'connectionCode',
      },
      {
        title: '名称',
        dataIndex: 'connectionName',
        key: 'connectionName',
      },
      {
        title: '类型',
        dataIndex: 'dbType',
        key: 'dbType',
      },
      {
        title: '地址',
        key: 'host',
        render: (_, record) => `${record.host}:${record.port}`,
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        render: (value: string) => renderStatusText(value),
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space size={4}>
            <ActionIconButton
              icon={<EyeOutlined />}
              label="查看连接详情"
              onClick={() => void openDetail(record.id ?? null)}
            />
            <ActionIconButton
              icon={<EditOutlined />}
              label="编辑连接"
              onClick={() => openEdit(record.id ?? null)}
            />
            <ActionIconButton
              icon={<PlayCircleOutlined />}
              label="测试连接"
              loading={submittingAction === record.id}
              onClick={() => void handleTest(record.id)}
            />
            <ActionIconButton
              icon={<DeleteOutlined />}
              label="删除连接"
              danger
              confirmTitle="确认删除该连接？"
              onClick={() => void handleDelete(record.id)}
            />
          </Space>
        ),
      },
    ],
    [submittingAction],
  )

  async function openDetail(id: number | null) {
    if (!id) {
      return
    }
    setSelectedId(id)
    await loadDetail(id)
    setDetailDrawerOpen(true)
  }

  async function openEdit(id: number | null) {
    if (!id) {
      setEditingConnection(null)
      form.setFieldsValue(DEFAULT_CONNECTION)
      setDrawerOpen(true)
      return
    }
    const response = await getJson<ConnectionDetail>(`/api/admin/connections/${id}`)
    const nextDetail = response.data
    setEditingConnection(nextDetail.connection)
    setDetail(nextDetail)
    form.setFieldsValue({
      connectionCode: nextDetail.connection.connectionCode,
      connectionName: nextDetail.connection.connectionName,
      dbType: nextDetail.connection.dbType,
      host: nextDetail.connection.host,
      port: nextDetail.connection.port,
      username: nextDetail.connection.username,
      passwordCiphertext: '',
      status: nextDetail.connection.status,
      remark: nextDetail.connection.remark ?? '',
      connectionConfigJson: nextDetail.connection.connectionConfigJson ?? '',
      catalogs: nextDetail.catalogs.map((catalog) => ({
        id: catalog.id,
        catalogType: catalog.catalogType,
        catalogValue: catalog.catalogValue,
      })),
    })
    setDrawerOpen(true)
  }

  async function handleSubmit(values: ConnectionUpsertPayload) {
    setSaving(true)
    try {
      const payload = normalizeCatalogPayload(values)
      if (editingConnection?.id) {
        await putJson<ConnectionDetail, ConnectionUpsertPayload>(
          `/api/admin/connections/${editingConnection.id}`,
          payload,
        )
        message.success('连接已更新')
        await loadConnections(editingConnection.id)
        await loadDetail(editingConnection.id)
      } else {
        const response = await postJson<ConnectionDetail, ConnectionUpsertPayload>('/api/admin/connections', payload)
        message.success('连接已创建')
        await loadConnections(response.data.connection.id ?? null)
      }
      setDrawerOpen(false)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  async function handleTest(id?: number) {
    if (!id) {
      return
    }
    setSubmittingAction(id)
    try {
      await postJson<null, undefined>(`/api/admin/connections/${id}/test`)
      message.success('连接测试成功')
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmittingAction(null)
    }
  }

  async function handleTestForm() {
    setTestingForm(true)
    try {
      const values = await form.validateFields()
      const payload = normalizeCatalogPayload(values)
      const url = editingConnection?.id
        ? `/api/admin/connections/${editingConnection.id}/test-config`
        : '/api/admin/connections/test'
      await postJson<null, ConnectionUpsertPayload>(url, payload)
      message.success('连接校验通过，目标库均真实存在')
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setTestingForm(false)
    }
  }

  async function handleDelete(id: number | undefined) {
    if (!id) {
      return
    }
    setSubmittingAction(id)
    try {
      await deleteJson<null>(`/api/admin/connections/${id}`)
      if (selectedId === id) {
        setDetailDrawerOpen(false)
        setDetail(null)
      }
      message.success('连接已删除')
      await loadConnections(null)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSubmittingAction(null)
    }
  }

  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={5}>数据源连接</Typography.Title>
        </div>
        <Button type="primary" onClick={() => void openEdit(null)}>
          新建连接
        </Button>
      </div>

      <div className="datasource-workspace">
        <Card className="li-page-main-card datasource-list-card" data-testid="datasource-list-card" styles={{ body: { padding: 0 } }}>
          {loading ? (
            <div style={{ padding: 24 }}>
              <Spin />
            </div>
          ) : (
            <div className="datasource-list-table-wrapper">
              <Table
                rowKey={(record) => String(record.id)}
                columns={columns}
                dataSource={connections}
                pagination={false}
                size="small"
                scroll={{ x: 760 }}
                rowClassName={(record) => (record.id === selectedId ? 'li-table-row-selected' : '')}
              />
            </div>
          )}
        </Card>
      </div>

      <Drawer
        title="连接详情"
        width={640}
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
        destroyOnHidden
      >
        {!detail ? (
          <Alert type="info" showIcon message="请选择一个连接查看详情" />
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="连接编码">{detail.connection.connectionCode}</Descriptions.Item>
              <Descriptions.Item label="状态">{renderStatusText(detail.connection.status)}</Descriptions.Item>
              <Descriptions.Item label="数据库类型">{detail.connection.dbType}</Descriptions.Item>
              <Descriptions.Item label="主机端口">
                {detail.connection.host}:{detail.connection.port}
              </Descriptions.Item>
              <Descriptions.Item label="用户名">{detail.connection.username}</Descriptions.Item>
              <Descriptions.Item label="扩展配置">
                <Typography.Text code>{detail.connection.connectionConfigJson || '未配置'}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.connection.remark || '无'}
              </Descriptions.Item>
            </Descriptions>

            <div>
              <Typography.Title level={5}>目标库列表</Typography.Title>
              <Table<CatalogItem>
                rowKey={(record) => String(record.id ?? `${record.catalogType}-${record.catalogValue}`)}
                size="small"
                pagination={false}
                dataSource={detail.catalogs}
                locale={{ emptyText: '当前连接未配置目标库' }}
                columns={[
                  { title: '目标库名称', dataIndex: 'catalogValue' },
                  { title: '类型', dataIndex: 'catalogType' },
                ]}
              />
            </div>
          </Space>
        )}
      </Drawer>

      <Drawer
        title={editingConnection ? '编辑连接' : '新建连接'}
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button loading={testingForm} onClick={() => void handleTestForm()}>
              测试连接
            </Button>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void form.submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<ConnectionUpsertPayload>
          form={form}
          layout="vertical"
          className="compact-form"
          onFinish={(values) => void handleSubmit(values)}
        >
          <div className="form-grid form-grid-two">
            <Form.Item label="连接编码" name="connectionCode" rules={[{ required: true }]}>
              <Input size="small" />
            </Form.Item>
            <Form.Item label="连接名称" name="connectionName" rules={[{ required: true }]}>
              <Input size="small" />
            </Form.Item>
            <Form.Item label="数据库类型" name="dbType" rules={[{ required: true }]}>
              <Select
                size="small"
                options={[
                  { label: 'PostgreSQL', value: 'POSTGRESQL' },
                  { label: 'MySQL', value: 'MYSQL' },
                  { label: 'Oracle', value: 'ORACLE' },
                ]}
                onChange={(value) => {
                  form.setFieldValue('port', DB_TYPE_PORT[value])
                  const catalogs = form.getFieldValue('catalogs') ?? []
                  form.setFieldValue(
                    'catalogs',
                    catalogs.map((catalog: CatalogItem) => ({
                      ...catalog,
                      catalogType: normalizeCatalogTypeForDb(value, catalog.catalogType),
                    })),
                  )
                }}
              />
            </Form.Item>
            <Form.Item label="状态" name="status">
              <Select
                size="small"
                options={[
                  { label: '已启用', value: 'ENABLED' },
                  { label: '已停用', value: 'DISABLED' },
                ]}
              />
            </Form.Item>
            <Form.Item label="主机" name="host" rules={[{ required: true }]}>
              <Input size="small" />
            </Form.Item>
            <Form.Item label="端口" name="port" rules={[{ required: true }]}>
              <InputNumber size="small" style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item label="用户名" name="username" rules={[{ required: true }]}>
              <Input size="small" />
            </Form.Item>
            <Form.Item
              label={editingConnection ? '密码（留空则沿用）' : '密码'}
              name="passwordCiphertext"
              rules={editingConnection ? undefined : [{ required: true }]}
            >
              <Input.Password size="small" />
            </Form.Item>
          </div>

          <Form.Item label="扩展配置 JSON" name="connectionConfigJson">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input size="small" />
          </Form.Item>

          <Form.List name="catalogs">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <div className="section-header">
                  <Typography.Title level={5}>目标库</Typography.Title>
                  <Button size="small" onClick={() => add(buildCatalogDraft(currentDbType))}>
                    新增目标库
                  </Button>
                </div>
                <Table<{ key: number | string; name: number }>
                  className="catalog-edit-table"
                  size="small"
                  pagination={false}
                  dataSource={fields.map((field) => ({
                    key: currentCatalogs[field.name]?.id ?? field.key,
                    name: field.name,
                  }))}
                  rowKey="key"
                  locale={{ emptyText: getCatalogRule(currentDbType).emptyText }}
                  columns={[
                    {
                      title: '目标库名称',
                      key: 'catalogValue',
                      width: '60%',
                      render: (_, row) => (
                        <Form.Item
                          name={[row.name, 'catalogValue']}
                          rules={[
                            { required: true, message: '请输入目标库名称' },
                            {
                              validator: async (_, value) => {
                                const trimmedValue = value?.trim()
                                if (!trimmedValue) {
                                  return
                                }
                                const currentType = normalizeCatalogTypeForDb(
                                  currentDbType,
                                  form.getFieldValue(['catalogs', row.name, 'catalogType']),
                                )
                                const duplicated = currentCatalogs.some((catalog, index) => {
                                  if (index === row.name) {
                                    return false
                                  }
                                  return (
                                    catalog?.catalogValue?.trim().toLowerCase() === trimmedValue.toLowerCase()
                                    && normalizeCatalogTypeForDb(currentDbType, catalog?.catalogType) === currentType
                                  )
                                })
                                if (duplicated) {
                                  throw new Error('同一连接下目标库名称不可重复')
                                }
                              },
                            },
                          ]}
                        >
                          <Input size="small" placeholder={getCatalogRule(currentDbType).placeholder} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '库类型',
                      key: 'catalogType',
                      width: 180,
                      render: (_, row) => (
                        <Form.Item
                          name={[row.name, 'catalogType']}
                          initialValue={normalizeCatalogTypeForDb(currentDbType)}
                          rules={[{ required: true, message: '请选择库类型' }]}
                        >
                          <Select
                            size="small"
                            options={getCatalogTypeOptions(currentDbType)}
                            onChange={() => void form.validateFields([['catalogs', row.name, 'catalogValue']])}
                          />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '操作',
                      key: 'actions',
                      width: 88,
                      align: 'center',
                      render: (_, row) => (
                        <ActionIconButton
                          icon={<DeleteOutlined />}
                          label={`删除目标库第 ${row.name + 1} 行`}
                          danger
                          onClick={() => remove(row.name)}
                        />
                      ),
                    },
                  ]}
                />
              </Space>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </div>
  )
}
