import { ModuleIntroCard } from '../../components/ModuleIntroCard'

export function AuditModulePage() {
  return (
    <ModuleIntroCard
      title="审计日志"
      description="预留关键操作审计列表、详情与筛选维度页面入口，后续接 DS_AUDIT_LOG。"
      tags={['审计列表', '详情查看', 'traceId 检索']}
    />
  )
}
