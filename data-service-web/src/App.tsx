import { Suspense } from 'react'
import { Layout, Menu, Space, Spin, Tag, Typography } from 'antd'
import {
  AppstoreOutlined,
  ClusterOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  TableOutlined,
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { appRoutes } from './router'
import type { AppRoutePath } from './router'

const { Header, Sider, Content } = Layout

const menuItems = [
  { key: '/datasource', icon: <DatabaseOutlined />, label: '数据源连接' },
  { key: '/catalog', icon: <TableOutlined />, label: '目标库管理' },
  { key: '/service', icon: <AppstoreOutlined />, label: '数据服务' },
  { key: '/federation', icon: <ClusterOutlined />, label: '联邦 SQL 平台' },
  { key: '/query-debug', icon: <ExperimentOutlined />, label: '统一查询调试' },
  { key: '/cache', icon: <CloudServerOutlined />, label: '缓存策略' },
  { key: '/publish', icon: <SafetyCertificateOutlined />, label: '发布管理' },
  { key: '/audit', icon: <ProfileOutlined />, label: '审计日志' },
]

function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const routePath = location.pathname as AppRoutePath
  const CurrentPage = appRoutes[routePath] ?? appRoutes['/']

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={248} theme="light" style={{ borderRight: '1px solid #d9e2ec' }}>
        <div className="brand-panel">
          <Typography.Text className="brand-kicker">P0 运行底座</Typography.Text>
          <Typography.Title level={3} style={{ margin: 0, color: '#072635' }}>
            data-service
          </Typography.Title>
          <Typography.Paragraph className="brand-copy">
            统一承载连接管理、服务配置、发布审计与联邦 SQL 平台扩展。
          </Typography.Paragraph>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderInlineEnd: 'none' }}
        />
      </Sider>
      <Layout>
        <Header className="top-header">
          <Space size={12}>
            <Tag color="processing">React 19 + Ant Design 5</Tag>
            <Tag color="success">端口 3001</Tag>
            <Tag color="default">统一响应已对齐后端</Tag>
          </Space>
        </Header>
        <Content className="page-shell">
          <Suspense
            fallback={
              <div className="page-loading">
                <Spin size="large" />
              </div>
            }
          >
            <CurrentPage />
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  )
}

export default App
