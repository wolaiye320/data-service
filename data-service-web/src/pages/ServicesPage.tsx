import { Alert, App, Button, Popconfirm, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { formatDateTime } from '../api/format'
import {
  disableService,
  listServices,
  publishService,
  type ServiceDetail,
  type ServiceStatus,
} from '../api/services'
import { ApiRequestError } from '../api/request'
import { Icons } from '../components/icons'
import { TableActionButton } from '../components/TableActionButton'

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

export default function ServicesPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [services, setServices] = useState<ServiceDetail[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const [status, setStatus] = useState<string>()
  const [publishingId, setPublishingId] = useState<number>()
  const [disablingId, setDisablingId] = useState<number>()
  const [operationError, setOperationError] = useState<string>()

  const loadServices = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      const serviceData = await listServices(status)
      setServices(serviceData)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    void loadServices()
  }, [loadServices])

  const handlePublish = useCallback(async (record: ServiceDetail) => {
    setPublishingId(record.id)
    setOperationError(undefined)
    try {
      await publishService(record.id)
      message.success('服务已发布')
      await loadServices()
    } catch (err) {
      const detail = errorMessage(err)
      setOperationError(detail)
      message.error(detail)
    } finally {
      setPublishingId(undefined)
    }
  }, [loadServices, message])

  const handleDisable = useCallback(async (record: ServiceDetail) => {
    setDisablingId(record.id)
    setOperationError(undefined)
    try {
      await disableService(record.id)
      message.success('服务已停用')
      await loadServices()
    } catch (err) {
      const detail = errorMessage(err)
      setOperationError(detail)
      message.error(detail)
    } finally {
      setDisablingId(undefined)
    }
  }, [loadServices, message])

  const columns = useMemo<ColumnsType<ServiceDetail>>(
    () => [
      { title: '服务编码', dataIndex: 'serviceCode', width: 180 },
      { title: '服务名称', dataIndex: 'serviceName', width: 180 },
      {
        title: 'SQL 类型',
        dataIndex: 'sqlType',
        width: 130,
        render: (value: string) => (value === 'FEDERATED_SQL' ? '联邦 SQL' : '简单 SQL'),
      },
      { title: '默认连接', dataIndex: 'defaultConnectionCode', width: 150, render: (value?: string) => value || '-' },
      { title: '状态', dataIndex: 'status', width: 110, render: statusText },
      { title: '当前版本', dataIndex: 'currentVersion', width: 100, render: (value?: number) => value ?? '-' },
      { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: formatDateTime },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 170,
        render: (_, record) => (
          <Space size={4}>
            <TableActionButton
              label="编辑服务"
              icon={<Icons.edit />}
              onClick={() => navigate(`/services/${record.id}/edit`)}
              testId={`service-edit-${record.id}`}
            />
            <Popconfirm title="确定发布该服务吗？" okText="确定" cancelText="取消" onConfirm={() => void handlePublish(record)}>
              <span>
                <TableActionButton
                  label="发布服务"
                  icon={<Icons.play />}
                  loading={publishingId === record.id}
                  testId={`service-publish-${record.id}`}
                />
              </span>
            </Popconfirm>
            <Popconfirm title="确定停用该服务吗？" okText="确定" cancelText="取消" onConfirm={() => void handleDisable(record)}>
              <span>
                <TableActionButton
                  label="停用服务"
                  icon={<Icons.power />}
                  danger
                  disabled={record.status === 'DISABLED'}
                  loading={disablingId === record.id}
                  testId={`service-disable-${record.id}`}
                />
              </span>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [disablingId, handleDisable, handlePublish, navigate],
  )

  return (
    <div className="module-page" data-testid="services-page">
      <div className="module-hero">
        <Typography.Title level={5}>数据服务</Typography.Title>
        <Button type="primary" icon={<Icons.api />} onClick={() => navigate('/services/new')}>
          新增服务
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
                { value: 'DRAFT', label: '草稿' },
                { value: 'PUBLISHED', label: '已发布' },
                { value: 'DISABLED', label: '已停用' },
              ]}
              onChange={setStatus}
            />
            <Button icon={<Icons.reload />} onClick={() => void loadServices()}>
              刷新
            </Button>
          </Space>
        </div>

        {operationError ? <Alert className="inline-summary" type="error" showIcon message="发布或停用失败" description={operationError} closable onClose={() => setOperationError(undefined)} /> : null}
        {error ? <Alert type="error" showIcon message="数据服务加载失败" description={error} /> : null}

        <Table
          className="li-zebra-table"
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={services}
          scroll={{ x: 1180 }}
          locale={{ emptyText: error ? '请先处理接口错误后重试' : '暂无数据服务，请新增服务' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
        />
      </div>
    </div>
  )
}
