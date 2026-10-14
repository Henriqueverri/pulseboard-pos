import { screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { db } from '../../test/db'
import { problem } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'

async function openNewProduct(user: ReturnType<typeof renderApp>['user']) {
  await user.click(await screen.findByRole('button', { name: 'Novo produto' }))
  return screen.getByRole('dialog', { name: 'Novo produto' })
}

describe('products (admin)', () => {
  it('creates a product sending the price as a decimal string', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    const dialog = await openNewProduct(user)

    await user.type(within(dialog).getByLabelText('SKU'), 'PB-099-NEW')
    await user.type(within(dialog).getByLabelText('Nome'), 'Cabo USB-C')
    await user.type(within(dialog).getByLabelText('Preço (R$)'), '19,9')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar produto' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(db.products.at(-1)).toMatchObject({
      sku: 'PB-099-NEW',
      name: 'Cabo USB-C',
      price: '19.90',
      active: true,
    })
    expect(await screen.findByText('Cabo USB-C')).toBeInTheDocument()
  })

  it('puts a duplicate SKU error on the SKU field', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    const dialog = await openNewProduct(user)

    await user.type(within(dialog).getByLabelText('SKU'), 'PB-001-ESS')
    await user.type(within(dialog).getByLabelText('Nome'), 'Outro')
    await user.type(within(dialog).getByLabelText('Preço (R$)'), '10')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar produto' }))

    expect(await within(dialog).findByText('Já existe um produto com o SKU PB-001-ESS.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('SKU')).toHaveAttribute('aria-invalid', 'true')
  })

  it('maps server validation errors to their fields', async () => {
    server.use(
      http.post('/api/products', () =>
        problem(400, 'validation_failed', 'Dados inválidos.', { name: ['tamanho deve ser entre 1 e 120'] }),
      ),
    )
    const { user } = renderApp('/products', { as: 'ADMIN' })
    const dialog = await openNewProduct(user)

    await user.type(within(dialog).getByLabelText('SKU'), 'PB-100')
    await user.type(within(dialog).getByLabelText('Nome'), 'Nome')
    await user.type(within(dialog).getByLabelText('Preço (R$)'), '10')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar produto' }))

    expect(await within(dialog).findByText('tamanho deve ser entre 1 e 120')).toBeInTheDocument()
  })

  it('creates a product with stock control and a minimum', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    const dialog = await openNewProduct(user)

    expect(within(dialog).queryByLabelText('Estoque mínimo')).not.toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('SKU'), 'BAR-010')
    await user.type(within(dialog).getByLabelText('Nome'), 'Refrigerante lata')
    await user.type(within(dialog).getByLabelText('Preço (R$)'), '6')
    await user.click(within(dialog).getByRole('switch', { name: /Controlar estoque/ }))
    await user.type(within(dialog).getByLabelText('Estoque mínimo'), '12')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar produto' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(db.products.at(-1)).toMatchObject({ sku: 'BAR-010', trackStock: true, stockMin: 12 })
  })

  it('does not submit an invalid price', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    const dialog = await openNewProduct(user)

    await user.type(within(dialog).getByLabelText('SKU'), 'PB-101')
    await user.type(within(dialog).getByLabelText('Nome'), 'Nome')
    await user.type(within(dialog).getByLabelText('Preço (R$)'), '0')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar produto' }))

    expect(await within(dialog).findByText(/Informe um preço maior que zero/)).toBeInTheDocument()
    expect(db.products).toHaveLength(5)
  })
})

function rowOf(name: string) {
  return screen.getByRole('row', { name: new RegExp(name) })
}

describe('stock (admin)', () => {
  it('shows the balance with a low or empty badge, and filters low stock', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })

    await screen.findByText('Cerveja Long Neck')
    expect(within(rowOf('Cerveja Long Neck')).getByText('Baixo')).toBeInTheDocument()
    expect(within(rowOf('Cerveja Long Neck')).getByText('3')).toBeInTheDocument()
    expect(within(rowOf('Água sem gás')).getByText('Zerado')).toBeInTheDocument()
    expect(within(rowOf('Fone Bluetooth Essential')).getByText('Não controla')).toBeInTheDocument()
    expect(
      within(rowOf('Fone Bluetooth Essential')).queryByRole('button', { name: /Estoque de/ }),
    ).not.toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Estoque' }), 'low')

    await waitFor(() => expect(screen.queryByText('Fone Bluetooth Essential')).not.toBeInTheDocument())
    expect(screen.getByText('Cerveja Long Neck')).toBeInTheDocument()
    expect(screen.getByText('Água sem gás')).toBeInTheDocument()
  })

  it('records a receipt and shows it in the history', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    await user.click(await screen.findByRole('button', { name: 'Estoque de Cerveja Long Neck' }))
    const dialog = screen.getByRole('dialog', { name: 'Estoque · Cerveja Long Neck' })
    expect(within(dialog).getByTestId('stock-balance')).toHaveTextContent('3')
    expect(await within(dialog).findByText('Nenhuma movimentação ainda.')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('Quantidade'), '24')
    await user.type(within(dialog).getByLabelText(/Motivo/), 'NF 1234')
    await user.click(within(dialog).getByRole('button', { name: 'Registrar entrada' }))

    await waitFor(() => expect(within(dialog).getByTestId('stock-balance')).toHaveTextContent('27'))
    expect(within(dialog).queryByText('Baixo')).not.toBeInTheDocument()
    const history = within(dialog).getByRole('table', { name: 'Movimentações de estoque' })
    expect(within(history).getByText('Entrada')).toBeInTheDocument()
    expect(within(history).getByText('+24')).toBeInTheDocument()
    expect(within(history).getByText('NF 1234')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Quantidade')).toHaveValue('')
    expect(db.products.find((product) => product.sku === 'BAR-001')?.stockQuantity).toBe(27)
  })

  it('requires a reason for an adjustment and shows a balance that would go negative', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    await user.click(await screen.findByRole('button', { name: 'Estoque de Cerveja Long Neck' }))
    const dialog = screen.getByRole('dialog', { name: 'Estoque · Cerveja Long Neck' })

    await user.selectOptions(within(dialog).getByLabelText('Movimentação'), 'ADJUSTMENT')
    await user.type(within(dialog).getByLabelText('Quantidade'), '-5')
    await user.click(within(dialog).getByRole('button', { name: 'Registrar ajuste' }))
    expect(await within(dialog).findByText('Informe o motivo do ajuste.')).toBeInTheDocument()
    expect(db.movements).toHaveLength(0)

    await user.type(within(dialog).getByLabelText(/Motivo/), 'Quebra')
    await user.click(within(dialog).getByRole('button', { name: 'Registrar ajuste' }))

    expect(await within(dialog).findByText('Saldo insuficiente para BAR-001.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Quantidade')).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog).getByTestId('stock-balance')).toHaveTextContent('3')
  })
})
