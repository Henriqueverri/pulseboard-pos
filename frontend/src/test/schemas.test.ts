import { describe, expect, it } from 'vitest'
import { loginSchema } from '../features/auth/schema'
import { checkoutSchema } from '../features/checkout/schema'
import { customerSchema } from '../features/customers/schema'
import { productSchema } from '../features/products/schema'

const productId = '8f1d2c3b-4a5e-4f60-9a1b-2c3d4e5f6a01'
const customerId = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e01'

function messages(result: { success: boolean; error?: { issues: { message: string }[] } }) {
  return result.error?.issues.map((issue) => issue.message) ?? []
}

describe('checkoutSchema', () => {
  const valid = { customerId, items: [{ productId, quantity: 2 }], paymentMethod: 'PIX' }

  it('accepts a complete checkout', () => {
    expect(checkoutSchema.parse(valid)).toEqual(valid)
  })

  it('requires a customer, at least one item and a payment method', () => {
    const result = checkoutSchema.safeParse({ customerId: '', items: [], paymentMethod: null })

    expect(messages(result)).toEqual([
      'Selecione o cliente.',
      'Adicione ao menos um produto.',
      'Escolha a forma de pagamento.',
    ])
  })

  it('rejects quantities outside 1..10000 and unknown payment methods', () => {
    expect(checkoutSchema.safeParse({ ...valid, items: [{ productId, quantity: 0 }] }).success).toBe(false)
    expect(checkoutSchema.safeParse({ ...valid, items: [{ productId, quantity: 10_001 }] }).success).toBe(false)
    expect(checkoutSchema.safeParse({ ...valid, paymentMethod: 'BOLETO' }).success).toBe(false)
  })
})

describe('productSchema', () => {
  it('trims text and converts the typed price to the API format', () => {
    expect(productSchema.parse({ sku: ' PB-9 ', name: ' Cabo ', price: '19,9', active: true })).toEqual({
      sku: 'PB-9',
      name: 'Cabo',
      price: '19.90',
      active: true,
    })
  })

  it.each(['0', '0,00', '-1', '1,999', 'abc', ''])('rejects the price %j', (price) => {
    const result = productSchema.safeParse({ sku: 'X', name: 'Y', price, active: true })

    expect(result.success).toBe(false)
  })

  it('limits the SKU to 64 characters', () => {
    const result = productSchema.safeParse({ sku: 'x'.repeat(65), name: 'Y', price: '1', active: true })

    expect(messages(result)).toEqual(['O SKU tem no máximo 64 caracteres.'])
  })
})

describe('customerSchema', () => {
  it('requires a valid e-mail and turns an empty document into null', () => {
    expect(customerSchema.parse({ name: 'Maria', email: ' maria@example.com ', document: '' })).toEqual({
      name: 'Maria',
      email: 'maria@example.com',
      document: null,
    })
    expect(messages(customerSchema.safeParse({ name: 'Maria', email: '', document: '' }))).toEqual([
      'Informe o e-mail.',
    ])
    expect(messages(customerSchema.safeParse({ name: 'Maria', email: 'maria', document: '' }))).toEqual([
      'Informe um e-mail válido.',
    ])
  })
})

describe('loginSchema', () => {
  it('requires e-mail and password', () => {
    expect(messages(loginSchema.safeParse({ email: '', password: '' }))).toEqual([
      'Informe o e-mail.',
      'Informe a senha.',
    ])
  })
})
