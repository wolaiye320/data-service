import type { ItemType } from 'antd/es/menu/interface'
import type { NavigateFunction } from 'react-router-dom'
import { Icons } from './icons'

export function sidebarMenuItems(navigate: NavigateFunction): ItemType[] {
  return [
    {
      key: '/connections',
      icon: <Icons.database />,
      label: '数据源管理',
      onClick: () => navigate('/connections'),
    },
    {
      key: '/services',
      icon: <Icons.api />,
      label: '数据服务',
      onClick: () => navigate('/services'),
    },
    {
      key: '/audits',
      icon: <Icons.audit />,
      label: '审计日志',
      onClick: () => navigate('/audits'),
    },
  ]
}
