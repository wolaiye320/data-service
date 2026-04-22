import { useEffect, useMemo, useState } from 'react'
import { Alert, Card, Descriptions, Space, Spin, Table, Tag, Typography } from 'antd'
import { RocketOutlined, StopOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { ActionIconButton } from '../../components/ActionIconButton'
import { useAppFeedback } from '../../components/useAppFeedback'
import { getJson, postJson, putJson, resolveErrorMessage } from '../../services/http'
import type { ServiceDefinition, ServiceVersionItem } from '../../types/admin'

function renderServiceStatus(status: string) {
  if (status === 'PUBLISHED') {
    return <Tag color="success">已发布</Tag>
  }
  if (status === 'DISABLED') {
    return <Tag color="default">已停用</Tag>
  }
  return <Tag color="processing">草稿</Tag>
}

export function PublishModulePage() {
  const { message } = useAppFeedback()
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [definitions, setDefinitions] = useState<ServiceDefinition[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [versions, setVersions] = useState<ServiceVersionItem[]>([])

  async function loadDefinitions(nextSelectedId?: number | null) {
    setLoading(true)
    try {
      const response = await getJson<ServiceDefinition[]>('/api/admin/service-definitions')
      const nextDefinitions = response.data
      setDefinitions(nextDefinitions)
      const effectiveId =
        nextSelectedId ?? (selectedId && nextDefinitions.some((item) => item.id === selectedId) ? selectedId : nextDefinitions[0]?.id)
      setSelectedId(effectiveId ?? null)
    } finally {
      setLoading(false)
    }
  }

  async function loadVersions(id: number) {
    const response = await getJson<ServiceVersionItem[]>(`/api/admin/service-definitions/${id}/versions`)
    setVersions(response.data)
  }

  useEffect(() => {
    void loadDefinitions()
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setVersions([])
      return
    }
    void loadVersions(selectedId)
  }, [selectedId])

  async function handlePublish(id?: number) {
    if (!id) {
      return
    }
    setActionLoading(id)
    try {
      await postJson(`/api/admin/service-definitions/${id}/publish`)
      message.success('服务已发布')
      await loadDefinitions(id)
      await loadVersions(id)
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
      await putJson(`/api/admin/service-definitions/${id}/status`, { status: 'DISABLED' })
      message.success('服务已停用')
      await loadDefinitions(id)
      await loadVersions(id)
    } catch (error) {
      message.error(resolveErrorMessage(error))
    } finally {
      setActionLoading(null)
    }
  }

  const columns = useMemo<ColumnsType<ServiceDefinition>>(
    () => [
      { title: '服务编码', dataIndex: 'serviceCode' },
      { title: '服务名称', dataIndex: 'serviceName' },
      {
        title: '当前状态',
        dataIndex: 'status',
        render: (value: string) => renderServiceStatus(value),
      },
      {
        title: '当前版本',
        dataIndex: 'version',
        render: (value: number | null | undefined) => value ?? 0,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space size={4}>
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

  const selectedDefinition = definitions.find((item) => item.id === selectedId) ?? null

  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={5}>发布管理</Typography.Title>
        </div>
      </div>

      <div className="module-grid">
        <Card title="待发布与已发布服务" className="li-page-main-card">
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

        <Card title="发布概览与版本快照" className="li-page-main-card">
          {!selectedDefinition ? (
            <Alert type="info" showIcon message="请选择一个服务查看发布概览" />
          ) : (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions size="small" column={2} bordered>
                <Descriptions.Item label="服务编码">{selectedDefinition.serviceCode}</Descriptions.Item>
                <Descriptions.Item label="状态">{renderServiceStatus(selectedDefinition.status)}</Descriptions.Item>
                <Descriptions.Item label="当前版本">{selectedDefinition.version ?? 0}</Descriptions.Item>
                <Descriptions.Item label="当前 SQL 版本">{selectedDefinition.currentSqlVersion ?? 0}</Descriptions.Item>
                <Descriptions.Item label="发布人">{selectedDefinition.publishedBy ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="发布时间">{selectedDefinition.publishedAt ?? '-'}</Descriptions.Item>
              </Descriptions>

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
                    render: (value: string) => renderServiceStatus(value),
                  },
                  { title: '创建人', dataIndex: 'createdBy' },
                  { title: '创建时间', dataIndex: 'createdAt' },
                ]}
              />
            </Space>
          )}
        </Card>
      </div>
    </div>
  )
}
