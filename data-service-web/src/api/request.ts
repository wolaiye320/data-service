type RequestOptions = RequestInit

const ADMIN_OPERATOR_HEADER = 'X-Operator'
const ADMIN_ROLE_HEADER = 'X-Operator-Role'
const DEFAULT_ADMIN_OPERATOR = 'web-admin'
const DEFAULT_ADMIN_ROLE = 'ADMIN'

export type ApiError = {
  errorCode: string
  message: string
  traceId?: string
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly traceId?: string

  constructor(status: number, error: ApiError) {
    super(error.message || '请求处理失败，请稍后重试')
    this.status = status
    this.errorCode = error.errorCode
    this.traceId = error.traceId
  }
}

async function parseJsonResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  if (!text) {
    return undefined as T
  }
  return JSON.parse(text) as T
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (path.startsWith('/api/admin/')) {
    if (!headers.has(ADMIN_OPERATOR_HEADER)) {
      headers.set(ADMIN_OPERATOR_HEADER, DEFAULT_ADMIN_OPERATOR)
    }
    if (!headers.has(ADMIN_ROLE_HEADER)) {
      headers.set(ADMIN_ROLE_HEADER, DEFAULT_ADMIN_ROLE)
    }
  }
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...options, headers })
  if (!response.ok) {
    let error: ApiError
    try {
      error = await parseJsonResponse<ApiError>(response)
    } catch {
      error = { errorCode: 'HTTP_ERROR', message: `接口请求失败：${response.status}` }
    }
    throw new ApiRequestError(response.status, error)
  }

  return parseJsonResponse<T>(response)
}
