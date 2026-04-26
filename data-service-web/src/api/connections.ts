import { apiRequest } from './request'

export type ConnectionStatus = 'ENABLED' | 'DISABLED'

export type ConnectionDetail = {
  id: number
  connectionCode: string
  connectionName: string
  dbType: string
  host: string
  port: number
  username: string
  status: ConnectionStatus
  remark?: string
  connectionConfigJson?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
}

export type ConnectionPayload = {
  connectionCode?: string
  connectionName: string
  dbType: string
  host: string
  port: number
  username: string
  password: string
  databaseName: string
  remark?: string
}

export type ConnectionTestResult = {
  success: boolean
  message: string
}

export function listConnections(status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<ConnectionDetail[]>(`/api/admin/connections${query}`)
}

export function createConnection(payload: ConnectionPayload) {
  return apiRequest<ConnectionDetail>('/api/admin/connections', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateConnection(id: number, payload: ConnectionPayload) {
  return apiRequest<ConnectionDetail>(`/api/admin/connections/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateConnectionStatus(id: number, status: ConnectionStatus) {
  return apiRequest<ConnectionDetail>(`/api/admin/connections/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}

export function testConnection(id: number) {
  return apiRequest<ConnectionTestResult>(`/api/admin/connections/${id}/test`, {
    method: 'POST',
  })
}
