import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { PASSWORD, users } from '../test/db'
import { problem } from '../test/handlers'
import { renderApp } from '../test/renderApp'
import { server } from '../test/server'

describe('authentication', () => {
  it('sends anonymous users to the login', async () => {
    const { router } = renderApp('/orders')

    expect(await screen.findByRole('heading', { name: 'Entrar no caixa' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('logs in, keeps the token in sessionStorage (never localStorage) and returns to the page asked for', async () => {
    const { router, user } = renderApp('/orders')

    await user.type(await screen.findByLabelText('E-mail'), users.CASHIER.email)
    await user.type(screen.getByLabelText('Senha'), PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Pedidos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders')
    expect(JSON.parse(sessionStorage.getItem('pos.session')!)).toMatchObject({
      token: 'cashier-token',
      user: { email: users.CASHIER.email, role: 'CASHIER' },
    })
    expect(localStorage.length).toBe(0)
  })

  it('shows the ProblemDetail message for wrong credentials', async () => {
    const { user } = renderApp('/login')

    await user.type(await screen.findByLabelText('E-mail'), users.CASHIER.email)
    await user.type(screen.getByLabelText('Senha'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
    expect(sessionStorage.getItem('pos.session')).toBeNull()
  })

  it('validates the form before calling the API', async () => {
    const { user } = renderApp('/login')

    await user.click(await screen.findByRole('button', { name: 'Entrar' }))

    expect(screen.getByText('Informe o e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
  })

  it('goes back to the login with a notice when the API answers 401', async () => {
    server.use(http.get('/api/orders', () => problem(401, 'unauthorized', 'Token expirado.')))
    const { router } = renderApp('/orders', { as: 'ADMIN' })

    expect(await screen.findByText('Sua sessão expirou. Entre novamente.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(sessionStorage.getItem('pos.session')).toBeNull()
  })

  it('ignores an expired session stored in the tab', async () => {
    sessionStorage.setItem(
      'pos.session',
      JSON.stringify({ token: 'old', expiresAt: Date.now() - 1000, user: users.ADMIN }),
    )
    const { router } = renderApp('/')

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('logs out and clears the session', async () => {
    const { router, user } = renderApp('/', { as: 'CASHIER' })

    await user.click(await screen.findByRole('button', { name: 'Sair' }))

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(sessionStorage.getItem('pos.session')).toBeNull()
  })
})

describe('login while the API is hibernated', () => {
  it('wakes the API when the login opens and explains the wait', async () => {
    server.use(http.get('/api/health', () => problem(503, 'api_waking', 'O servidor está iniciando.')))
    renderApp('/login')

    expect(await screen.findByText('Acordando o servidor')).toBeInTheDocument()
  })

  it('shows no notice when the API is already up', async () => {
    let pinged = false
    server.use(
      http.get('/api/health', () => {
        pinged = true
        return HttpResponse.json({ status: 'UP' })
      }),
    )
    renderApp('/login')

    await waitFor(() => expect(pinged).toBe(true))
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(screen.queryByText('Acordando o servidor')).not.toBeInTheDocument()
  })

  it('retries the login by itself until the API answers', { timeout: 15_000 }, async () => {
    let attempts = 0
    server.use(
      http.post('/api/auth/login', () => {
        attempts += 1
        return problem(503, 'api_waking', 'O servidor está iniciando.')
      }, { once: true }),
    )
    const { router, user } = renderApp('/orders')

    await user.type(await screen.findByLabelText('E-mail'), users.CASHIER.email)
    await user.type(screen.getByLabelText('Senha'), PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Acordando o servidor')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Pedidos' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders')
    expect(attempts).toBe(1)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
