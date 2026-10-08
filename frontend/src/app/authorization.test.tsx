import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { db, makeOrder } from '../test/db'
import { renderApp } from '../test/renderApp'

describe('role-based access', () => {
  it('hides the products menu from the cashier and denies the page', async () => {
    renderApp('/products', { as: 'CASHIER' })

    expect(await screen.findByRole('heading', { name: 'Acesso negado' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).queryByRole('link', { name: 'Produtos' })).not.toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Caixa' })).toBeInTheDocument()
  })

  it('gives the admin the products menu and page', async () => {
    renderApp('/products', { as: 'ADMIN' })

    expect(await screen.findByRole('heading', { name: 'Produtos' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Produtos' })).toBeInTheDocument()
    expect(await screen.findByText('Mouse Antigo')).toBeInTheDocument()
  })

  it('does not offer refunds to the cashier', async () => {
    const order = makeOrder({ status: 'PAID' })
    db.orders.push(order)
    renderApp(`/orders/${order.id}`, { as: 'CASHIER' })

    expect(await screen.findByRole('heading', { name: 'Pedido #1001' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Estornar' })).not.toBeInTheDocument()
  })

  it('lets the admin refund a paid order', async () => {
    const order = makeOrder({ status: 'PAID' })
    db.orders.push(order)
    const { user } = renderApp(`/orders/${order.id}`, { as: 'ADMIN' })

    await user.click(await screen.findByRole('button', { name: 'Estornar' }))
    const dialog = screen.getByRole('dialog', { name: 'Estornar pedido #1001' })
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar estorno' }))

    expect(await screen.findByText('Estornado', { selector: 'span' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Estornar' })).not.toBeInTheDocument()
    expect(db.orders[0].status).toBe('REFUNDED')
  })
})
