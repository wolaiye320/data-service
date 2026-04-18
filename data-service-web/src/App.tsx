import { Suspense } from 'react'
import { Layout, Menu, Badge, Avatar, Dropdown, Space, Typography, Spin } from 'antd'
import {
  DatabaseOutlined,
  TableOutlined,
  AppstoreOutlined,
  ClusterOutlined,
  ExperimentOutlined,
  CloudServerOutlined,
  SafetyCertificateOutlined,
  ProfileOutlined,
  BellOutlined,
  UserOutlined,
  SettingOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { appRoutes } from './router'
import type { AppRoutePath } from './router'

const { Sider, Content } = Layout

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

const userMenuItems = [
  { key: 'profile', icon: <UserOutlined />, label: '个人中心' },
  { key: 'settings', icon: <SettingOutlined />, label: '系统设置' },
  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
]

function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const routePath = location.pathname as AppRoutePath
  const CurrentPage = appRoutes[routePath] ?? appRoutes['/']

  return (
    <Layout className="app-layout">
      <Sider width={220} className="app-sider">
        <div className="brand-panel">
          <div className="brand-logo">
            <div className="brand-icon" />
            <Typography.Title level={4} className="brand-title">
              Data Service
            </Typography.Title>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          className="app-menu"
        />
        <div className="sider-bottom">
          <div className="sider-bottom-item">
            <Badge dot offset={[-2, 2]}>
              <BellOutlined />
            </Badge>
            <span>消息</span>
          </div>
          <Dropdown
            menu={{ items: userMenuItems }}
            placement="topLeft"
            arrow
          >
            <div className="sider-bottom-item user-item">
              <Avatar size="small" icon={<UserOutlined />} />
              <Space direction="vertical" size={0}>
                <span className="user-name">admin</span>
                <span className="user-role">管理员</span>
              </Space>
            </div>
          </Dropdown>
        </div>
      </Sider>
      <Content className="app-content">
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
  )
}

export default App
