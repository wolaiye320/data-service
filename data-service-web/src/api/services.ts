import { apiRequest } from './request'

export type ServiceStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type SqlType = 'SIMPLE_SQL' | 'FEDERATED_SQL'

export type ServiceVersion = {
  id: number
  version: number
  status: string
  sqlType: string
  sqlText: string
  sourceSnapshotJson?: string
  paramSnapshotJson?: string
  fieldSnapshotJson?: string
  validationSnapshotJson?: string
  planSnapshotJson?: string
  createdAt?: string
  updatedAt?: string
}

export type ServiceDetail = {
  id: number
  serviceCode: string
  serviceName: string
  sqlType: SqlType
  defaultConnectionCode?: string
  status: ServiceStatus
  currentVersion?: number
  maxBatchSize?: number
  maxResultRows?: number
  queryTimeoutSeconds?: number
  federatedQueryTimeoutSeconds?: number
  remark?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
  draftVersion?: ServiceVersion
  versionHistory?: ServiceVersion[]
}

export type ServicePayload = {
  serviceCode?: string
  serviceName: string
  sqlType: SqlType
  defaultConnectionCode?: string
  sqlText: string
  paramDefinitions?: unknown[]
  maxBatchSize?: number
  maxResultRows?: number
  queryTimeoutSeconds?: number
  federatedQueryTimeoutSeconds?: number
  remark?: string
}

export type CachePolicy = {
  serviceId: number
  enabled: boolean
  ttlSeconds?: number
  cacheKeyTemplate?: string
  maxEntries?: number
  contextKeys?: string[]
  remark?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
}

export type CachePolicyPayload = {
  enabled: boolean
  ttlSeconds?: number
  cacheKeyTemplate?: string
  maxEntries?: number
  contextKeys?: string[]
  remark?: string
}

export type ServicePreviewRequest = {
  previewParams?: Record<string, unknown>
  requestContext?: {
    tenantId?: string
    callerId?: string
    traceId?: string
    contextKeys?: string[]
  }
}

export type ServicePreviewResponse = {
  serviceId: number
  draftVersion: number
  previewParams: Record<string, unknown>
  requestContext: Record<string, unknown>
  planSnapshotJson?: string
  planSnapshot?: unknown
  rows: Record<string, unknown>[]
  elapsedMs?: number
  diagnosticSummary?: Record<string, unknown>
}

export function listServices(status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<ServiceDetail[]>(`/api/admin/services${query}`)
}

export function createService(payload: ServicePayload) {
  return apiRequest<ServiceDetail>('/api/admin/services', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateService(id: number, payload: ServicePayload) {
  return apiRequest<ServiceDetail>(`/api/admin/services/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function disableService(id: number) {
  return apiRequest<ServiceDetail>(`/api/admin/services/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status: 'DISABLED' }),
  })
}

export function publishService(id: number) {
  return apiRequest<ServiceDetail>(`/api/admin/services/${id}/publish`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export function previewService(id: number, payload: ServicePreviewRequest) {
  return apiRequest<ServicePreviewResponse>(`/api/admin/services/${id}/preview`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getCachePolicy(id: number) {
  return apiRequest<CachePolicy>(`/api/admin/services/${id}/cache-policy`)
}

export function saveCachePolicy(id: number, payload: CachePolicyPayload) {
  return apiRequest<CachePolicy>(`/api/admin/services/${id}/cache-policy`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
