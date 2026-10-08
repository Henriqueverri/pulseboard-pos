import { describe, expect, it } from 'vitest'
import {
  cartReducer,
  cartTotals,
  emptyCart,
  MAX_ITEMS,
  MAX_QUANTITY,
  type CartAction,
  type CartState,
} from './cartReducer'

const fone = { id: 'p1', sku: 'PB-001-ESS', name: 'Fone', price: '79.90' }
const teclado = { id: 'p2', sku: 'PB-002-PRO', name: 'Teclado', price: '249.90' }

function run(...actions: CartAction[]): CartState {
  return actions.reduce(cartReducer, emptyCart)
}

describe('cartReducer', () => {
  it('adds a product with quantity 1 and the price in cents', () => {
    const state = run({ type: 'add', product: fone })

    expect(state.items).toEqual([
      { productId: 'p1', sku: 'PB-001-ESS', name: 'Fone', unitPriceCents: 7990, quantity: 1 },
    ])
  })

  it('adding the same product again increments its quantity instead of duplicating the line', () => {
    const state = run({ type: 'add', product: fone }, { type: 'add', product: fone })

    expect(state.items).toHaveLength(1)
    expect(state.items[0].quantity).toBe(2)
  })

  it('clamps quantities to the range the API accepts', () => {
    const add = { type: 'add', product: fone } as const

    expect(run(add, { type: 'setQuantity', productId: 'p1', quantity: 0 }).items[0].quantity).toBe(1)
    expect(run(add, { type: 'setQuantity', productId: 'p1', quantity: -5 }).items[0].quantity).toBe(1)
    expect(run(add, { type: 'setQuantity', productId: 'p1', quantity: Number.NaN }).items[0].quantity).toBe(1)
    expect(run(add, { type: 'setQuantity', productId: 'p1', quantity: 2.7 }).items[0].quantity).toBe(2)
    expect(
      run(add, { type: 'setQuantity', productId: 'p1', quantity: MAX_QUANTITY + 1 }).items[0].quantity,
    ).toBe(MAX_QUANTITY)
  })

  it('does not go over the maximum quantity when adding again', () => {
    const state = run(
      { type: 'add', product: fone },
      { type: 'setQuantity', productId: 'p1', quantity: MAX_QUANTITY },
      { type: 'add', product: fone },
    )

    expect(state.items[0].quantity).toBe(MAX_QUANTITY)
  })

  it('ignores new products beyond the item limit', () => {
    const actions: CartAction[] = Array.from({ length: MAX_ITEMS + 1 }, (_, index) => ({
      type: 'add',
      product: { id: `p${index}`, sku: `SKU-${index}`, name: `P${index}`, price: '1.00' },
    }))

    expect(run(...actions).items).toHaveLength(MAX_ITEMS)
  })

  it('removes a line, sets the customer and clears everything', () => {
    const customer = { id: 'c1', name: 'Maria', email: 'maria@example.com' }
    const filled = run(
      { type: 'add', product: fone },
      { type: 'add', product: teclado },
      { type: 'remove', productId: 'p1' },
      { type: 'setCustomer', customer },
    )

    expect(filled.items.map((item) => item.productId)).toEqual(['p2'])
    expect(filled.customer).toEqual(customer)
    expect(cartReducer(filled, { type: 'clear' })).toEqual(emptyCart)
  })
})

describe('cartTotals', () => {
  it('sums line totals in integer cents without floating-point drift', () => {
    const state = run(
      { type: 'add', product: { id: 'a', sku: 'A', name: 'A', price: '0.10' } },
      { type: 'setQuantity', productId: 'a', quantity: 3 },
      { type: 'add', product: teclado },
    )

    expect(cartTotals(state)).toEqual({ itemCount: 4, subtotalCents: 25020, totalCents: 25020 })
  })

  it('is zero for an empty cart', () => {
    expect(cartTotals(emptyCart)).toEqual({ itemCount: 0, subtotalCents: 0, totalCents: 0 })
  })
})
