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

  it('does not submit an invalid price', async () => {
    const { user } = renderApp('/products', { as: 'ADMIN' })
    const dialog = await openNewProduct(user)

    await user.type(within(dialog).getByLabelText('SKU'), 'PB-101')
    await user.type(within(dialog).getByLabelText('Nome'), 'Nome')
    await user.type(within(dialog).getByLabelText('Preço (R$)'), '0')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar produto' }))

    expect(await within(dialog).findByText(/Informe um preço maior que zero/)).toBeInTheDocument()
    expect(db.products).toHaveLength(3)
  })
})
