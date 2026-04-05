import { ModuleIntroCard } from '../../components/ModuleIntroCard'

export function CacheModulePage() {
  return (
    <ModuleIntroCard
      title="缓存策略"
      description="预留服务级缓存开关、TTL、缓存键策略与命中观测的页面入口。"
      tags={['本地缓存', 'TTL', '命中观测']}
    />
  )
}
