import { Card, Col, Row, Statistic, Timeline, Typography } from 'antd'

export function HomePage() {
  return (
    <div>
      <Typography.Title level={2}>工程与运行底座已就绪</Typography.Title>
      <Typography.Paragraph>
        当前版本完成了后端多模块骨架、统一响应与异常处理、基础日志审计接入、资源保护基线，以及第一阶段管理端与第二阶段联邦平台入口。
      </Typography.Paragraph>
      <Row gutter={[16, 16]}>
        <Col span={8}>
          <Card>
            <Statistic title="后端端口" value={8081} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="前端端口" value={3001} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="系统级批量上限" value={100} />
          </Card>
        </Col>
      </Row>
      <Card title="阶段落地清单" style={{ marginTop: 16 }}>
        <Timeline
          items={[
            { children: '后端多模块工程骨架与启动工程' },
            { children: '前端管理端骨架、路由与基础布局' },
            { children: '环境配置、部署参数模板与 profile 区分' },
            { children: '统一响应、错误码、全局异常处理' },
            { children: 'traceId 日志过滤器与审计入口' },
            { children: '查询超时、批量上限、结果上限默认保护' },
            { children: '联邦 SQL 编辑、校验日志、计划诊断、自助发布入口' },
          ]}
        />
      </Card>
    </div>
  )
}
