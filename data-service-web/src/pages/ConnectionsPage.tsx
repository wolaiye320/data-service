import { Alert, App, Button, Drawer, Form, Input, InputNumber, Popconfirm, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createConnection,
  listConnections,
  testConnection,
  updateConnection,
  updateConnectionStatus,
  type ConnectionDetail,
  type ConnectionPayload,
  type ConnectionStatus,
} from '../api/connections'
import { ApiRequestError } from '../api/request'
import { formatDateTime } from '../api/format'
import { Icons } from '../components/icons'
import { TableActionButton } from '../components/TableActionButton'

type ConnectionFormValue = ConnectionPayload

const dbTypeOptions = [
  { value: 'POSTGRESQL', label: 'PostgreSQL' },
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'ORACLE', label: 'Oracle' },
]

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : '操作失败'
}

function databaseNameOf(record?: ConnectionDetail) {
  if (!record?.connectionConfigJson) {
    return { value: '' }
  }
  try {
    const config = JSON.parse(record.connectionConfigJson) as { databaseName?: string }
    return { value: config.databaseName ?? '' }
  } catch {
    return { value: '', error: '连接扩展配置解析失败，请检查后端返回的 connectionConfigJson。' }
  }
}

export default function ConnectionsPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm<ConnectionFormValue>()
  const [connections, setConnections] = useState<ConnectionDetail[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const [status, setStatus] = useState<string>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<ConnectionDetail>()
  const [saving, setSaving] = useState(false)
  const [testingId, setTestingId] = useState<number>()
  const [switchingId, setSwitchingId] = useState<number>()

  const loadConnections = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      setConnections(await listConnections(status))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    void loadConnections()
  }, [loadConnections])

  const openCreate = () => {
    setEditing(undefined)
    form.resetFields()
    form.setFieldsValue({ dbType: 'POSTGRESQL', port: 5432 })
    setDrawerOpen(true)
  }

  const openEdit = useCallback((record: ConnectionDetail) => {
    const databaseName = databaseNameOf(record)
    if (databaseName.error) {
      message.error(databaseName.error)
    }
    setEditing(record)
    form.setFieldsValue({
      connectionCode: record.connectionCode,
      connectionName: record.connectionName,
      dbType: record.dbType,
      host: '',
      port: record.port,
      username: '',
      password: '',
      databaseName: databaseName.value,
      remark: record.remark,
    })
    setDrawerOpen(true)
  }, [form, message])

  const submitForm = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await updateConnection(editing.id, values)
        message.success('连接已更新')
      } else {
        await createConnection(values)
        message.success('连接已新增')
      }
      setDrawerOpen(false)
      await loadConnections()
    } catch (err) {
      message.error(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleTest = useCallback(async (record: ConnectionDetail) => {
    setTestingId(record.id)
    try {
      const result = await testConnection(record.id)
      if (result.success) {
        message.success(result.message)
      } else {
        message.error(result.message)
      }
    } catch (err) {
      message.error(errorMessage(err))
    } finally {
      setTestingId(undefined)
    }
  }, [message])

  const handleSwitch = useCallback(async (record: ConnectionDetail) => {
    const nextStatus: ConnectionStatus = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    setSwitchingId(record.id)
    try {
      await updateConnectionStatus(record.id, nextStatus)
      message.success(nextStatus === 'ENABLED' ? '连接已启用' : '连接已停用')
      await loadConnections()
    } catch (err) {
      message.error(errorMessage(err))
    } finally {
      setSwitchingId(undefined)
    }
  }, [loadConnections, message])

  const columns = useMemo<ColumnsType<ConnectionDetail>>(
    () => [
      { title: '连接编码', dataIndex: 'connectionCode', width: 180 },
      { title: '连接名称', dataIndex: 'connectionName', width: 180 },
      { title: '数据库类型', dataIndex: 'dbType', width: 130 },
      { title: '主机', dataIndex: 'host', width: 180 },
      { title: '端口', dataIndex: 'port', width: 90 },
      { title: '用户名', dataIndex: 'username', width: 130 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: ConnectionStatus) => (value === 'ENABLED' ? '启用' : '停用'),
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: 170,
        render: formatDateTime,
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 150,
        render: (_, record) => (
          <Space size={4}>
            <TableActionButton
              label="编辑连接"
              icon={<Icons.edit />}
              onClick={() => openEdit(record)}
              testId={`connection-edit-${record.id}`}
            />
            <TableActionButton
              label="测试连接"
              icon={<Icons.play />}
              loading={testingId === record.id}
              onClick={() => void handleTest(record)}
              testId={`connection-test-${record.id}`}
            />
            <Popconfirm
              title={record.status === 'ENABLED' ? '确定停用该连接吗？' : '确定启用该连接吗？'}
              okText="确定"
              cancelText="取消"
              onConfirm={() => void handleSwitch(record)}
            >
              <span>
                <TableActionButton
                  label={record.status === 'ENABLED' ? '停用连接' : '启用连接'}
                  icon={<Icons.power />}
                  danger={record.status === 'ENABLED'}
                  loading={switchingId === record.id}
                  testId={`connection-status-${record.id}`}
                />
              </span>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [handleSwitch, handleTest, openEdit, switchingId, testingId],
  )

  return (
    <div className="module-page" data-testid="connections-page">
      <div className="module-hero">
        <Typography.Title level={5}>数据源连接</Typography.Title>
        <Button type="primary" icon={<Icons.database />} onClick={openCreate}>
          新增连接
        </Button>
      </div>

      <div className="li-page-main-card">
        <div className="section-header">
          <Space>
            <Select
              allowClear
              placeholder="全部状态"
              value={status}
              style={{ width: 140 }}
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
              onChange={setStatus}
            />
            <Button icon={<Icons.reload />} onClick={() => void loadConnections()}>
              刷新
            </Button>
          </Space>
        </div>

        {error ? <Alert type="error" showIcon message="数据源连接加载失败" description={error} /> : null}

        <Table
          className="li-zebra-table"
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={connections}
          scroll={{ x: 1260 }}
          locale={{ emptyText: error ? '请先处理接口错误后重试' : '暂无数据源连接，请新增连接' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
        />
      </div>

      <Drawer
        title={editing ? '编辑数据源连接' : '新增数据源连接'}
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitForm()}>
              保存
            </Button>
          </Space>
        }
      >
        {editing ? (
          <Alert
            className="inline-summary"
            type="info"
            showIcon
            message="敏感字段已脱敏"
            description="编辑连接时需重新输入主机、用户名和密码，列表与详情不会回显敏感明文。"
          />
        ) : null}
        <Form form={form} layout="vertical" className="form-grid form-grid-two" requiredMark>
          {!editing ? (
            <Form.Item
              name="connectionCode"
              label="连接编码"
              rules={[{ required: true, message: '请输入连接编码' }, { max: 64, message: '最多 64 个字符' }]}
            >
              <Input size="small" placeholder="如 pg_trade" />
            </Form.Item>
          ) : null}
          <Form.Item
            name="connectionName"
            label="连接名称"
            rules={[{ required: true, message: '请输入连接名称' }, { max: 128, message: '最多 128 个字符' }]}
          >
            <Input size="small" placeholder="如 PostgreSQL 交易库" />
          </Form.Item>
          <Form.Item name="dbType" label="数据库类型" rules={[{ required: true, message: '请选择数据库类型' }]}>
            <Select size="small" options={dbTypeOptions} />
          </Form.Item>
          <Form.Item name="host" label="主机" rules={[{ required: true, message: '请输入主机' }]}>
            <Input size="small" placeholder="127.0.0.1" />
          </Form.Item>
          <Form.Item name="port" label="端口" rules={[{ required: true, message: '请输入端口' }]}>
            <InputNumber size="small" min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input size="small" placeholder="数据库用户" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password size="small" placeholder="不会明文回显" />
          </Form.Item>
          <Form.Item name="databaseName" label="数据库/服务名" rules={[{ required: true, message: '请输入数据库或服务名' }]}>
            <Input size="small" placeholder="如 crm / ORCLPDB1" />
          </Form.Item>
          <Form.Item className="form-grid-full" name="remark" label="备注" rules={[{ max: 512, message: '最多 512 个字符' }]}>
            <Input size="small" placeholder="可选说明" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
