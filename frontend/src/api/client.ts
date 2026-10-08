import { clearSession, currentToken } from '../features/auth/session'
import type { ProblemDetail } from './types'

/**
 * The only place that talks HTTP. It knows the base URL, adds the bearer token, turns every
 * failure into an {@link ApiError} built from the ProblemDetail body, and reports an expired or
 * rejected session (401 on an authenticated request) to the app.
 */

const BASE_PATH = '/api'
const LOGIN_PATH = '/auth/login'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: Record<string, string[]>

  constructor(status: number, code: string, message: string, errors: Record<string, string[]> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errors = errors
  }

  get isNetworkError(): boolean {
    return this.status === 0
  }

  get isForbidden(): boolean {
    return this.status === 403
  }
}

let onUnauthorized: () => void = () => {}

/** Called once by the auth provider: what to do when the server rejects the session. */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

type Method = 'GET' | 'POST' | 'PUT'

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const token = currentToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(new URL(BASE_PATH + path, window.location.origin), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'network_error', 'Não foi possível conectar ao servidor. Tente novamente.')
  }

  if (!response.ok) {
    const error = await toApiError(response)
    // A 401 on login means wrong credentials; anywhere else the session is missing or expired.
    if (response.status === 401 && path !== LOGIN_PATH) {
      clearSession()
      onUnauthorized()
    }
    throw error
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {}
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // Not a ProblemDetail (e.g. a proxy error page): fall back to the status below.
  }
  const message =
    problem.detail ??
    (response.status === 403
      ? 'Acesso negado.'
      : response.status >= 500
        ? 'Erro no servidor. Tente novamente.'
        : 'Não foi possível concluir a operação.')
  return new ApiError(
    response.status,
    problem.code ?? `http_${response.status}`,
    message,
    problem.errors ?? {},
  )
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
}

/** Query string from an object, skipping empty values. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
