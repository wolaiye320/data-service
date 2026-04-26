import { apiRequest } from './request'

export type AuditLogSummary = {
  id: number
  serviceId?: number
  serviceCode?: string
  connectionId?: number
  eventType: string
  targetType: string
  targetId: string
  operator: string
  operatorRole: string
  operationResult: string
  traceId?: string
  changeSummary?: string
  createdAt?: string
  createdBy?: string
}

export type AuditLogListResponse = {
  page: number
  size: number
  total: number
  items: AuditLogSummary[]
}

export type AuditLogDetail = {
  id: number
  serviceId?: number
  serviceCode?: string
  connectionId?: number
  eventType: string
  targetType: string
  targetId: string
  operator: string
  operatorRole: string
  operationResult: string
  traceId?: string
  requestIp?: string
  changeSummary?: string
  detailJson?: string
  contextSummaryJson?: string
  createdAt?: string
  createdBy?: string
}

export type AuditLogQuery = {
  serviceCode?: string
  operator?: string
  eventType?: string
  operationResult?: string
  traceId?: string
  startAt?: string
  endAt?: string
  page?: number
  size?: number
}

function toQueryString(query: AuditLogQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value == null || value === '') {
      return
    }
    params.set(key, String(value))
  })
  const text = params.toString()
  return text ? `?${text}` : ''
}

export function listAudits(query: AuditLogQuery) {
  return apiRequest<AuditLogListResponse>(`/api/admin/audits${toQueryString(query)}`)
}

export function getAuditDetail(id: number) {
  return apiRequest<AuditLogDetail>(`/api/admin/audits/${id}`)
}
