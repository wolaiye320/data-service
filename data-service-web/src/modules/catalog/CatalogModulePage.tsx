import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Descriptions, Drawer, Form, Input, Select, Space, Spin, Table, Tag, Typography } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import type { ColumnsType } from 'antd/es/table'
import { getJson, putJson, resolveErrorMessage } from '../../services/http'
import type { CatalogItem, ConnectionDetail, ConnectionItem, ConnectionUpsertPayload } from '../../types/admin'

function renderStatus(status?: string) {
  if ((status ?? 'ENABLED') === 'ENABLED') {
    return <Tag color="success">已启用</Tag>
  }
  return <Tag color="default">已停用</Tag>
}

type CatalogFormValue = {
  catalogs: CatalogItem[]
}

export function CatalogModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [connections, setConnections] = useState<ConnectionItem[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<ConnectionDetail | null>(null)
  const [form] = Form.useForm<CatalogFormValue>()

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

  const catalogRows = detail?.catalogs ?? []

  const columns = useMemo<ColumnsType<ConnectionItem>>(
    () => [
      { title: '连接编码', dataIndex: 'connectionCode' },
      { title: '连接名称', dataIndex: 'connectionName' },
      { title: '数据库类型', dataIndex: 'dbType' },
      {
        title: '目标库数',
        key: 'catalogCount',
        render: (_, record) => {
          if (record.id !== detail?.connection.id) {
            return '-'
          }
          return catalogRows.length
        },
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => renderStatus(value),
      },
    ],
    [catalogRows.length, detail?.connection.id],
  )

  function openEdit() {
    if (!detail) {
      return
    }
    form.setFieldsValue({
      catalogs: detail.catalogs.length === 0
        ? [
            {
              catalogCode: '',
              catalogName: '',
              catalogType: 'SCHEMA',
              catalogValue: '',
              status: 'ENABLED',
              remark: '',
            },
          ]
        : detail.catalogs.map((catalog) => ({
            catalogCode: catalog.catalogCode,
            catalogName: catalog.catalogName,
            catalogType: catalog.catalogType,
            catalogValue: catalog.catalogValue,
            status: catalog.status ?? 'ENABLED',
            remark: catalog.remark ?? '',
          })),
    })
    setDrawerOpen(true)
  }

  async function handleSubmit(values: CatalogFormValue) {
    if (!detail?.connection.id) {
      return
    }
    setSaving(true)
    try {
      const payload: ConnectionUpsertPayload = {
        connectionCode: detail.connection.connectionCode,
        connectionName: detail.connection.connectionName,
        dbType: detail.connection.dbType,
        host: detail.connection.host,
        port: detail.connection.port,
        username: detail.connection.username,
        passwordCiphertext: '',
        status: detail.connection.status,
        remark: detail.connection.remark ?? '',
        connectionConfigJson: detail.connection.connectionConfigJson ?? '',
        catalogs: values.catalogs,
      }
      await putJson<ConnectionDetail, ConnectionUpsertPayload>(`/api/admin/connections/${detail.connection.id}`, payload)
      message.success('目标库配置已更新')
      await loadConnections(detail.connection.id)
      await loadDetail(detail.connection.id)
      setDrawerOpen(false)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={2}>目标库管理</Typography.Title>
          <Typography.Paragraph>
            独立查看一个连接下的目标库清单，统一维护 catalog 编码、类型、值和启停状态。
          </Typography.Paragraph>
        </div>
        <Button type="primary" size="large" disabled={!detail} onClick={openEdit}>
          维护目标库
        </Button>
      </div>

      <div className="module-grid">
        <Card title="连接选择" className="li-page-main-card">
          {loading ? (
            <Spin />
          ) : (
            <Table
              rowKey={(record) => String(record.id)}
              size="small"
              pagination={false}
              dataSource={connections}
              columns={columns}
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

        <Card title="目标库详情" className="li-page-main-card">
          {!detail ? (
            <Alert type="info" showIcon message="请选择一个连接查看目标库配置" />
          ) : (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions size="small" column={2} bordered>
                <Descriptions.Item label="连接编码">{detail.connection.connectionCode}</Descriptions.Item>
                <Descriptions.Item label="连接状态">{renderStatus(detail.connection.status)}</Descriptions.Item>
                <Descriptions.Item label="连接名称">{detail.connection.connectionName}</Descriptions.Item>
                <Descriptions.Item label="数据库类型">{detail.connection.dbType}</Descriptions.Item>
                <Descriptions.Item label="地址">
                  {detail.connection.host}:{detail.connection.port}
                </Descriptions.Item>
                <Descriptions.Item label="用户名">{detail.connection.username}</Descriptions.Item>
              </Descriptions>

              <Table<CatalogItem>
                rowKey={(record) => `${record.catalogCode}-${record.catalogValue}`}
                size="small"
                pagination={false}
                dataSource={catalogRows}
                locale={{ emptyText: '当前连接未配置目标库' }}
                columns={[
                  { title: '编码', dataIndex: 'catalogCode' },
                  { title: '名称', dataIndex: 'catalogName' },
                  { title: '类型', dataIndex: 'catalogType' },
                  { title: '目标值', dataIndex: 'catalogValue' },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    render: (value: string | undefined) => renderStatus(value),
                  },
                  { title: '备注', dataIndex: 'remark', render: (value: string | null | undefined) => value || '-' },
                ]}
              />
            </Space>
          )}
        </Card>
      </div>

      <Drawer
        title={detail ? `维护目标库 · ${detail.connection.connectionCode}` : '维护目标库'}
        width={820}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void form.submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<CatalogFormValue> form={form} layout="vertical" onFinish={(values) => void handleSubmit(values)}>
          <Form.List name="catalogs">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <div className="section-header">
                  <Typography.Title level={5}>目标库清单</Typography.Title>
                  <Button
                    onClick={() =>
                      add({
                        catalogCode: '',
                        catalogName: '',
                        catalogType: 'SCHEMA',
                        catalogValue: '',
                        status: 'ENABLED',
                        remark: '',
                      })
                    }
                  >
                    新增目标库
                  </Button>
                </div>
                {fields.length === 0 ? <Alert type="info" showIcon message="当前没有目标库，请至少新增一项。" /> : null}
                {fields.map((field) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`目标库 #${field.name + 1}`}
                    extra={
                      <ActionIconButton
                        icon={<DeleteOutlined />}
                        label={`删除目标库 ${field.name + 1}`}
                        danger
                        onClick={() => remove(field.name)}
                      />
                    }
                  >
                    <div className="form-grid form-grid-three">
                      <Form.Item label="编码" name={[field.name, 'catalogCode']} rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>
                      <Form.Item label="名称" name={[field.name, 'catalogName']} rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>
                      <Form.Item label="类型" name={[field.name, 'catalogType']} rules={[{ required: true }]}>
                        <Select
                          options={[
                            { label: 'Schema', value: 'SCHEMA' },
                            { label: 'Database', value: 'DATABASE' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="目标值" name={[field.name, 'catalogValue']} rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>
                      <Form.Item label="状态" name={[field.name, 'status']}>
                        <Select
                          options={[
                            { label: '已启用', value: 'ENABLED' },
                            { label: '已停用', value: 'DISABLED' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="备注" name={[field.name, 'remark']}>
                        <Input />
                      </Form.Item>
                    </div>
                  </Card>
                ))}
              </Space>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </div>
  )
}
