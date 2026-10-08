import type {
  Customer,
  IntegrationEvent,
  IntegrationHealth,
  Order,
  OrderStatus,
  Product,
  Role,
  User,
} from '../api/types'
import { centsToMoneyString, moneyToCents } from '../lib/money'

/** In-memory backend state used by the MSW handlers; reset before every test. */

const NOW = '2026-10-07T12:00:00Z'

export const users: Record<Role, User> = {
  ADMIN: {
    id: '0b6f7c1e-1d2a-4c3b-8e4f-5a6b7c8d9e01',
    name: 'Ana Admin',
    email: 'admin@test.pos',
    role: 'ADMIN',
  },
  CASHIER: {
    id: '0b6f7c1e-1d2a-4c3b-8e4f-5a6b7c8d9e02',
    name: 'Caio Caixa',
    email: 'caixa@test.pos',
    role: 'CASHIER',
  },
}

export const PASSWORD = 'secret-password'

function product(id: string, sku: string, name: string, price: string, active = true): Product {
  return { id, sku, name, price, active, createdAt: NOW, updatedAt: NOW }
}

function initialProducts(): Product[] {
  return [
    product('8f1d2c3b-4a5e-4f60-9a1b-2c3d4e5f6a01', 'PB-001-ESS', 'Fone Bluetooth Essential', '79.90'),
    product('8f1d2c3b-4a5e-4f60-9a1b-2c3d4e5f6a02', 'PB-002-PRO', 'Teclado Mecânico Pro', '249.90'),
    product('8f1d2c3b-4a5e-4f60-9a1b-2c3d4e5f6a03', 'PB-003-OLD', 'Mouse Antigo', '19.90', false),
  ]
}

function initialCustomers(): Customer[] {
  return [
    {
      id: '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e01',
      name: 'Maria Souza',
      email: 'maria@example.com',
      document: null,
      createdAt: NOW,
      updatedAt: NOW,
    },
  ]
}

export function makeOrder(overrides: Partial<Order> & { status: OrderStatus }): Order {
  const customer = initialCustomers()[0]
  const item = initialProducts()[0]
  return {
    id: crypto.randomUUID(),
    number: 1001,
    currency: 'BRL',
    paymentMethod: overrides.status === 'PENDING' || overrides.status === 'CANCELED' ? null : 'PIX',
    customer: { id: customer.id, name: customer.name, email: customer.email },
    total: '159.80',
    createdAt: NOW,
    updatedAt: NOW,
    paidAt: overrides.status === 'PAID' || overrides.status === 'REFUNDED' ? NOW : null,
    canceledAt: overrides.status === 'CANCELED' ? NOW : null,
    refundedAt: overrides.status === 'REFUNDED' ? NOW : null,
    items: [
      {
        id: crypto.randomUUID(),
        productId: item.id,
        sku: item.sku,
        productName: item.name,
        quantity: 2,
        unitPrice: item.price,
        lineTotal: '159.80',
      },
    ],
    integrationEvents: [],
    ...overrides,
  }
}

export function makeEvent(
  overrides: Partial<IntegrationEvent> & Pick<IntegrationEvent, 'sequence' | 'orderId'>,
): IntegrationEvent {
  const id = overrides.id ?? crypto.randomUUID()
  return {
    id,
    orderNumber: 1001,
    externalId: `pos:${overrides.orderId}`,
    eventType: 'ORDER_PAID',
    status: 'PENDING',
    failureKind: null,
    attempts: 0,
    attemptsAtRetry: 0,
    nextAttemptAt: NOW,
    lastAttemptAt: null,
    lastHttpStatus: null,
    lastErrorCode: null,
    lastError: null,
    requestId: `pos-${id}`,
    lastRequestId: null,
    remoteId: null,
    processedAt: null,
    createdAt: NOW,
    updatedAt: NOW,
    blockedBy: null,
    payload: { external_id: `pos:${overrides.orderId}`, status: 'paid' },
    ...overrides,
  }
}

function initialIntegration(): Omit<IntegrationHealth, 'pending' | 'failed'> {
  return {
    enabled: true,
    pausedUntil: null,
    targetUrl: 'http://localhost:8000/api/v1',
    keyPrefix: 'abcdefghijkl',
    lastSentAt: null,
  }
}

export const db = {
  products: initialProducts(),
  customers: initialCustomers(),
  orders: [] as Order[],
  nextNumber: 1001,
  events: [] as IntegrationEvent[],
  nextSequence: 1,
  integration: initialIntegration(),
}

export function resetDb(): void {
  db.products = initialProducts()
  db.customers = initialCustomers()
  db.orders = []
  db.nextNumber = 1001
  db.events = []
  db.nextSequence = 1
  db.integration = initialIntegration()
}

/** What the outbox does on pay/refund: a PENDING event, behind any unsent one of the order. */
export function enqueueEvent(order: Order, eventType: IntegrationEvent['eventType']): void {
  const blocker = db.events.find((event) => event.orderId === order.id && event.status !== 'SENT')
  db.events.push(
    makeEvent({
      sequence: db.nextSequence++,
      orderId: order.id,
      orderNumber: order.number,
      eventType,
      blockedBy: blocker?.sequence ?? null,
    }),
  )
}

export function createOrderInDb(
  customerId: string,
  items: { productId: string; quantity: number }[],
): Order {
  const customer = db.customers.find((c) => c.id === customerId)!
  const lines = items.map(({ productId, quantity }) => {
    const found = db.products.find((p) => p.id === productId)!
    const lineCents = moneyToCents(found.price) * quantity
    return {
      id: crypto.randomUUID(),
      productId,
      sku: found.sku,
      productName: found.name,
      quantity,
      unitPrice: found.price,
      lineTotal: centsToMoneyString(lineCents),
    }
  })
  const totalCents = lines.reduce((sum, line) => sum + moneyToCents(line.lineTotal), 0)
  const order: Order = {
    id: crypto.randomUUID(),
    number: db.nextNumber++,
    status: 'PENDING',
    currency: 'BRL',
    paymentMethod: null,
    customer: { id: customer.id, name: customer.name, email: customer.email },
    total: centsToMoneyString(totalCents),
    createdAt: NOW,
    updatedAt: NOW,
    paidAt: null,
    canceledAt: null,
    refundedAt: null,
    items: lines,
    integrationEvents: [],
  }
  db.orders.push(order)
  return order
}

export function updateOrder(id: string, changes: Partial<Order>): Order {
  const index = db.orders.findIndex((order) => order.id === id)
  db.orders[index] = { ...db.orders[index], ...changes }
  return db.orders[index]
}