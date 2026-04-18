import { Typography } from 'antd'

type ModuleIntroCardProps = {
  title: string
}

export function ModuleIntroCard({ title }: ModuleIntroCardProps) {
  return (
    <div className="module-page">
      <div className="module-hero">
        <div>
          <Typography.Title level={5}>{title}</Typography.Title>
        </div>
      </div>
    </div>
  )
}
