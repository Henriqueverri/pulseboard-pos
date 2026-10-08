import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadSession } from '../features/auth/session'
import { problem } from '../test/handlers'
import { signIn } from '../test/renderApp'
import { server } from '../test/server'
import { api, ApiError, setUnauthorizedHandler } from './client'

afterEach(() => setUnauthorizedHandler(() => {}))

describe('api client', () => {
  it('sends the session token as a bearer header', async () => {
    signIn('CASHIER')
    let authorization: string | null = null
    server.use(
      http.get('/api/ping', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    await expect(api.get('/ping')).resolves.toEqual({ ok: true })
    expect(authorization).toBe('Bearer cashier-token')
  })

  it('sends no Authorization header without a session', async () => {
    let authorization: string | null = 'unset'
    server.use(
      http.get('/api/ping', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({})
      }),
    )

    await api.get('/ping')
    expect(authorization).toBeNull()
  })

  it('builds the error from the ProblemDetail body', async () => {
    signIn('ADMIN')
    server.use(
      http.post('/api/things', () =>
        problem(400, 'validation_failed', 'Dados inválidos.', { name: ['não pode ficar em branco'] }),
      ),
    )

    const error = await api.post('/things', {}).catch((failure: unknown) => failure)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      code: 'validation_failed',
      message: 'Dados inválidos.',
      errors: { name: ['não pode ficar em branco'] },
    })
  })

  it('falls back to a generic message when the body is not a ProblemDetail', async () => {
    server.use(http.get('/api/ping', () => new HttpResponse('<html>bad gateway</html>', { status: 502 })))

    await expect(api.get('/ping')).rejects.toMatchObject({
      status: 502,
      code: 'http_502',
      message: 'Erro no servidor. Tente novamente.',
    })
  })

  it('clears the session and notifies the app on 401', async () => {
    signIn('CASHIER')
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    server.use(http.get('/api/ping', () => problem(401, 'unauthorized', 'Autenticação necessária.')))

    await expect(api.get('/ping')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(loadSession()).toBeNull()
  })

  it('treats a 401 from the login itself as wrong credentials, not as an expired session', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)

    await expect(api.post('/auth/login', { email: 'x@y.z', password: 'bad' })).rejects.toMatchObject({
      status: 401,
      code: 'invalid_credentials',
    })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('reports network failures as status 0', async () => {
    server.use(http.get('/api/ping', () => HttpResponse.error()))

    const error = await api.get('/ping').catch((failure: unknown) => failure)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).isNetworkError).toBe(true)
  })
})
