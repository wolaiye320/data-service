import { lazy } from 'react'

export const appRoutes = {
  '/': lazy(async () => {
    const module = await import('../pages/HomePage')
    return { default: module.HomePage }
  }),
  '/datasource': lazy(async () => {
    const module = await import('../modules/datasource/DatasourceModulePage')
    return { default: module.DatasourceModulePage }
  }),
  '/service': lazy(async () => {
    const module = await import('../modules/service/ServiceModulePage')
    return { default: module.default }
  }),
  '/query-debug': lazy(async () => {
    const module = await import('../modules/query-debug/QueryDebugModulePage')
    return { default: module.QueryDebugModulePage }
  }),
  '/cache': lazy(async () => {
    const module = await import('../modules/cache/CacheModulePage')
    return { default: module.CacheModulePage }
  }),
  '/publish': lazy(async () => {
    const module = await import('../modules/publish/PublishModulePage')
    return { default: module.PublishModulePage }
  }),
  '/audit': lazy(async () => {
    const module = await import('../modules/audit/AuditModulePage')
    return { default: module.AuditModulePage }
  }),
}

export type AppRoutePath = keyof typeof appRoutes
