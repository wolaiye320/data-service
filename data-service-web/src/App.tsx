import { Layout, Menu, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import { Icons } from './components/icons'
import { sidebarMenuItems } from './components/sidebarMenu'
import { AppRoutes } from './router'

const { Sider, Content } = Layout

export default function App() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <Layout className="app-layout">
      <Sider width={220} className="app-sidebar">
        <div className="brand-panel">
          <div className="brand-logo">DS</div>
          <div>
            <Typography.Text className="brand-title">data-service</Typography.Text>
            <Typography.Text className="brand-subtitle">SQL-first 管理台</Typography.Text>
          </div>
        </div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[
            location.pathname.startsWith('/services')
              ? '/services'
              : location.pathname.startsWith('/connections')
                ? '/connections'
                : location.pathname.startsWith('/audits')
                  ? '/audits'
                  : location.pathname,
          ]}
          className="sidebar-menu"
          items={sidebarMenuItems(navigate)}
        />
        <div className="sidebar-bottom">
          <div className="li-sidebar-bottom-item">
            <Icons.user />
            <span>admin / 管理员</span>
          </div>
        </div>
      </Sider>
      <Layout className="content-layout">
        <Content className="page-shell">
          <AppRoutes />
        </Content>
      </Layout>
    </Layout>
  )
}
