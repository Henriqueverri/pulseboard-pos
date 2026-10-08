import { screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { db } from '../../test/db'
import { problem } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'

async function addProduct(user: ReturnType<typeof renderApp>['user'], name: string) {
  await user.click(await screen.findByRole('button', { name: `Adicionar ${name}` }))
}

async function pickCustomer(user: ReturnType<typeof renderApp>['user']) {
  await user.type(screen.getByRole('searchbox', { name: 'Buscar cliente' }), 'maria')
  await user.click(await screen.findByRole('button', { name: /Maria Souza/ }))
}

describe('checkout', () => {
  it('sells: cart with quantities, customer, payment and success screen', async () => {
    const requests: { path: string; body: unknown }[] = []
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST') {
        requests.push({ path: new URL(request.url).pathname, body: await request.clone().json() })
      }
    })
    const { user } = renderApp('/', { as: 'CASHIER' })

    await addProduct(user, 'Fone Bluetooth Essential')
    await addProduct(user, 'Fone Bluetooth Essential')
    await addProduct(user, 'Teclado Mecânico Pro')
    expect(screen.getByRole('spinbutton', { name: 'Quantidade de Fone Bluetooth Essential' })).toHaveValue(2)
    expect(screen.getByTestId('cart-total')).toHaveTextContent('R$ 409,70')

    await user.click(screen.getByRole('button', { name: 'Aumentar Teclado Mecânico Pro' }))
    expect(screen.getByTestId('cart-total')).toHaveTextContent('R$ 659,60')

    await pickCustomer(user)
    await user.click(screen.getByRole('button', { name: 'Finalizar venda' }))

    const dialog = await screen.findByRole('dialog', { name: 'Pagamento' })
    await user.click(within(dialog).getByLabelText('Pix'))
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pagamento' }))

    expect(await screen.findByRole('heading', { name: 'Venda concluída' })).toBeInTheDocument()
    expect(screen.getByText('#1001')).toBeInTheDocument()
    expect(screen.getByText('R$ 659,60')).toBeInTheDocument()
    expect(screen.getByText('Pago com Pix')).toBeInTheDocument()

    const [created] = db.orders
    expect(requests).toEqual([
      {
        path: '/api/orders',
        body: {
          customerId: db.customers[0].id,
          items: [
            { productId: db.products[0].id, quantity: 2 },
            { productId: db.products[1].id, quantity: 2 },
          ],
        },
      },
      { path: `/api/orders/${created.id}/pay`, body: { paymentMethod: 'PIX' } },
    ])
    expect(created.status).toBe('PAID')

    await user.click(screen.getByRole('button', { name: 'Nova venda' }))
    expect(await screen.findByText(/Carrinho vazio/)).toBeInTheDocument()
  })

  it('only offers active products', async () => {
    renderApp('/', { as: 'CASHIER' })

    expect(await screen.findByText('Fone Bluetooth Essential')).toBeInTheDocument()
    expect(screen.queryByText('Mouse Antigo')).not.toBeInTheDocument()
  })

  it('validates the cart before opening the payment', async () => {
    const { user } = renderApp('/', { as: 'CASHIER' })

    await user.click(await screen.findByRole('button', { name: 'Finalizar venda' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Selecione o cliente.')
    expect(screen.getByRole('alert')).toHaveTextContent('Adicione ao menos um produto.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('requires a payment method', async () => {
    const { user } = renderApp('/', { as: 'CASHIER' })
    await addProduct(user, 'Fone Bluetooth Essential')
    await pickCustomer(user)
    await user.click(screen.getByRole('button', { name: 'Finalizar venda' }))

    const dialog = await screen.findByRole('dialog', { name: 'Pagamento' })
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pagamento' }))

    expect(within(dialog).getByRole('alert')).toHaveTextContent('Escolha a forma de pagamento.')
    expect(db.orders).toHaveLength(0)
  })

  it('shows the API message and keeps the cart when the order is rejected', async () => {
    server.use(
      http.post('/api/orders', () => problem(422, 'product_inactive', 'O produto PB-001-ESS está inativo.')),
    )
    const { user } = renderApp('/', { as: 'CASHIER' })
    await addProduct(user, 'Fone Bluetooth Essential')
    await pickCustomer(user)
    await user.click(screen.getByRole('button', { name: 'Finalizar venda' }))
    const dialog = await screen.findByRole('dialog', { name: 'Pagamento' })
    await user.click(within(dialog).getByLabelText('Dinheiro'))
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pagamento' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('O produto PB-001-ESS está inativo.')
    await user.click(within(dialog).getByRole('button', { name: 'Voltar' }))
    expect(screen.getByRole('list', { name: 'Carrinho' })).toHaveTextContent('Fone Bluetooth Essential')
  })

  it('reports an order created but not paid, with a link to it', async () => {
    server.use(
      http.post('/api/orders/:id/pay', () =>
        problem(409, 'concurrent_modification', 'O pedido foi alterado por outra operação.'),
      ),
    )
    const { user } = renderApp('/', { as: 'CASHIER' })
    await addProduct(user, 'Fone Bluetooth Essential')
    await pickCustomer(user)
    await user.click(screen.getByRole('button', { name: 'Finalizar venda' }))
    const dialog = await screen.findByRole('dialog', { name: 'Pagamento' })
    await user.click(within(dialog).getByLabelText('Cartão'))
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pagamento' }))

    expect(
      await screen.findByRole('heading', { name: 'Pedido #1001 criado, pagamento não concluído' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('O pedido foi alterado por outra operação.')
    expect(screen.getByRole('link', { name: 'Ver pedido' })).toHaveAttribute(
      'href',
      `/orders/${db.orders[0].id}`,
    )
  })

  it('creates a customer on the spot and uses it in the sale', async () => {
    const { user } = renderApp('/', { as: 'CASHIER' })

    await user.click(await screen.findByRole('button', { name: 'Novo cliente' }))
    const dialog = screen.getByRole('dialog', { name: 'Novo cliente' })
    await user.type(within(dialog).getByLabelText('Nome'), 'João Lima')
    await user.type(within(dialog).getByLabelText('E-mail'), 'joao@example.com')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar cliente' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('João Lima')).toBeInTheDocument()
    expect(screen.getByText('joao@example.com')).toBeInTheDocument()
  })
})