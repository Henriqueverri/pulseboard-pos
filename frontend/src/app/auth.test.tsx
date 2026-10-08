import { screen } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { PASSWORD, users } from '../test/db'
import { problem } from '../test/handlers'
import { renderApp } from '../test/renderApp'
import { server } from '../test/server'

describe('authentication', () => {
  it('sends anonymous users to the login', async () => {
    const { router } = renderApp('/orders')

    expect(await screen.findByRole('heading', { name: 'PulseBoard POS' })).toBeInTheDocument()
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
