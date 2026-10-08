import type { Customer, Product } from '../../api/types'
import { moneyToCents } from '../../lib/money'

/**
 * All checkout state lives here: items, quantities and the selected customer. Prices are integer
 * cents taken from the catalog; the total shown is a preview, the server computes the real one.
 */

export const MAX_ITEMS = 100
export const MAX_QUANTITY = 10_000

export interface CartItem {
  productId: string
  sku: string
  name: string
  unitPriceCents: number
  quantity: number
}

export interface CartCustomer {
  id: string
  name: string
  email: string
}

export interface CartState {
  items: CartItem[]
  customer: CartCustomer | null
}

export type CartAction =
  | { type: 'add'; product: Pick<Product, 'id' | 'sku' | 'name' | 'price'> }
  | { type: 'setQuantity'; productId: string; quantity: number }
  | { type: 'remove'; productId: string }
  | { type: 'setCustomer'; customer: Pick<Customer, 'id' | 'name' | 'email'> | null }
  | { type: 'clear' }

export const emptyCart: CartState = { items: [], customer: null }

function clampQuantity(quantity: number): number {
  if (!Number.isFinite(quantity)) {
    return 1
  }
  return Math.min(MAX_QUANTITY, Math.max(1, Math.trunc(quantity)))
}

export function cartReducer(state: CartState, action: CartAction): CartState {
  switch (action.type) {
    case 'add': {
      const existing = state.items.find((item) => item.productId === action.product.id)
      if (existing) {
        return {
          ...state,
          items: state.items.map((item) =>
            item === existing ? { ...item, quantity: clampQuantity(item.quantity + 1) } : item,
          ),
        }
      }
      if (state.items.length >= MAX_ITEMS) {
        return state
      }
      const item: CartItem = {
        productId: action.product.id,
        sku: action.product.sku,
        name: action.product.name,
        unitPriceCents: moneyToCents(action.product.price),
        quantity: 1,
      }
      return { ...state, items: [...state.items, item] }
    }
    case 'setQuantity':
      return {
        ...state,
        items: state.items.map((item) =>
          item.productId === action.productId
            ? { ...item, quantity: clampQuantity(action.quantity) }
            : item,
        ),
      }
    case 'remove':
      return { ...state, items: state.items.filter((item) => item.productId !== action.productId) }
    case 'setCustomer':
      return {
        ...state,
        customer: action.customer
          ? { id: action.customer.id, name: action.customer.name, email: action.customer.email }
          : null,
      }
    case 'clear':
      return emptyCart
  }
}

export function lineTotalCents(item: CartItem): number {
  return item.unitPriceCents * item.quantity
}

export interface CartTotals {
  itemCount: number
  subtotalCents: number
  totalCents: number
}

/** No discount or shipping in the MVP, so the total equals the subtotal. */
export function cartTotals(state: CartState): CartTotals {
  const subtotalCents = state.items.reduce((sum, item) => sum + lineTotalCents(item), 0)
  return {
    itemCount: state.items.reduce((sum, item) => sum + item.quantity, 0),
    subtotalCents,
    totalCents: subtotalCents,
  }
}
