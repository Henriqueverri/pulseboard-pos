import { screen, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { db, makeOrder } from '../../test/db'
import { problem } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'

describe('orders', () => {
  it('lists orders and filters by status through the URL', async () => {
    db.orders.push(
      makeOrder({ status: 'PAID', number: 1001 }),
      makeOrder({ status: 'PENDING', number: 1002 }),
    )
    const { router, user } = renderApp('/orders', { as: 'CASHIER' })

    expect(await screen.findByRole('link', { name: '#1001' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '#1002' })).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Status'), 'PENDING')

    expect(router.state.location.search).toBe('?status=PENDING')
    await expect.poll(() => screen.queryByRole('link', { name: '#1001' })).toBeNull()
    expect(screen.getByRole('link', { name: '#1002' })).toBeInTheDocument()
  })

  it('pays a pending order with the chosen method', async () => {
    const order = makeOrder({ status: 'PENDING' })
    db.orders.push(order)
    const { user } = renderApp(`/orders/${order.id}`, { as: 'CASHIER' })

    await user.click(await screen.findByRole('button', { name: 'Pagar' }))
    const dialog = screen.getByRole('dialog', { name: 'Pagar pedido #1001' })
    expect(within(dialog).getByRole('button', { name: 'Confirmar pagamento' })).toBeDisabled()
    await user.click(within(dialog).getByLabelText('Dinheiro'))
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pagamento' }))

    expect(await screen.findByText('Pago', { selector: 'span' })).toBeInTheDocument()
    expect(db.orders[0]).toMatchObject({ status: 'PAID', paymentMethod: 'CASH' })
    expect(screen.queryByRole('button', { name: 'Cancelar pedido' })).not.toBeInTheDocument()
  })

  it('cancels a pending order', async () => {
    const order = makeOrder({ status: 'PENDING' })
    db.orders.push(order)
    const { user } = renderApp(`/orders/${order.id}`, { as: 'CASHIER' })

    await user.click(await screen.findByRole('button', { name: 'Cancelar pedido' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))

    expect(await screen.findByText('Cancelado', { selector: 'span' })).toBeInTheDocument()
    expect(db.orders[0].status).toBe('CANCELED')
  })

  it('shows the conflict message when the order changed meanwhile', async () => {
    const order = makeOrder({ status: 'PENDING' })
    db.orders.push(order)
    server.use(
      http.post('/api/orders/:id/cancel', () =>
        problem(409, 'invalid_order_transition', 'Pedido PAID não pode ser cancelado.'),
      ),
    )
    const { user } = renderApp(`/orders/${order.id}`, { as: 'CASHIER' })

    await user.click(await screen.findByRole('button', { name: 'Cancelar pedido' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))

    const dialog = screen.getByRole('dialog')
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Pedido PAID não pode ser cancelado.')
  })

  it('shows "acesso negado" when the API answers 403', async () => {
    const order = makeOrder({ status: 'PAID' })
    db.orders.push(order)
    server.use(http.get('/api/orders/:id', () => problem(403, 'forbidden', 'Permissão insuficiente.')))
    renderApp(`/orders/${order.id}`, { as: 'CASHIER' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Acesso negado. Permissão insuficiente.')
  })
})
