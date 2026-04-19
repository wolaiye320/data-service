export type ApiResponse<T> = {
  success: boolean
  code: string
  message: string
  data: T
  meta: Record<string, unknown>
}

export class HttpRequestError extends Error {
  readonly status: number
  readonly code?: string
  readonly meta?: Record<string, unknown>

  constructor(status: number, message: string, code?: string, meta?: Record<string, unknown>) {
    super(message)
    this.name = 'HttpRequestError'
    this.status = status
    this.code = code
    this.meta = meta
  }
}

const JSON_HEADERS = {
  'Content-Type': 'application/json',
  'X-Operator': resolveOperator(),
  'X-Operator-Role': resolveOperatorRole(),
}

export async function getJson<T>(url: string): Promise<ApiResponse<T>> {
  return requestJson<T>(url)
}

export async function postJson<TResponse, TBody>(url: string, body?: TBody): Promise<ApiResponse<TResponse>> {
  return requestJson<TResponse>(url, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function putJson<TResponse, TBody>(url: string, body: TBody): Promise<ApiResponse<TResponse>> {
  return requestJson<TResponse>(url, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export async function deleteJson<TResponse>(url: string): Promise<ApiResponse<TResponse>> {
  return requestJson<TResponse>(url, {
    method: 'DELETE',
  })
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(url, {
    headers: JSON_HEADERS,
    ...init,
  })

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
  if (!response.ok) {
    throw new HttpRequestError(
      response.status,
      payload?.message || `请求失败: ${response.status}`,
      payload?.code,
      payload?.meta,
    )
  }
  if (!payload) {
    throw new HttpRequestError(response.status, '接口未返回有效 JSON')
  }
  return payload
}

export function resolveErrorMessage(error: unknown) {
  if (error instanceof HttpRequestError) {
    return error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '请求失败，请稍后重试'
}

function resolveOperator() {
  if (typeof window === 'undefined') {
    return 'web-admin'
  }
  return window.localStorage.getItem('data-service.operator') || 'web-admin'
}

function resolveOperatorRole() {
  if (typeof window === 'undefined') {
    return 'ADMIN'
  }
  return window.localStorage.getItem('data-service.operatorRole') || 'ADMIN'
}
