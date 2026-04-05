import { Card, Space, Tag, Typography } from 'antd'

type ModuleIntroCardProps = {
  title: string
  description: string
  tags: string[]
}

export function ModuleIntroCard({ title, description, tags }: ModuleIntroCardProps) {
  return (
    <Card>
      <Typography.Title level={3}>{title}</Typography.Title>
      <Typography.Paragraph>{description}</Typography.Paragraph>
      <Space wrap>
        {tags.map((tag) => (
          <Tag key={tag}>{tag}</Tag>
        ))}
      </Space>
    </Card>
  )
}
