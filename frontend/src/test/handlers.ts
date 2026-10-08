import { http, HttpResponse } from 'msw'
import type {
  IntegrationEvent,
  IntegrationHealth,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PaymentMethod,
  ProblemDetail,
  Role,
} from '../api/types'
import { createOrderInDb, db, enqueueEvent, PASSWORD, updateOrder, users } from './db'

export const TOKENS: Record<Role, string> = { ADMIN: 'admin-token', CASHIER: 'cashier-token' }

export function problem(status: number, code: string, detail: string, errors?: ProblemDetail['errors']) {
  return HttpResponse.json<ProblemDetail>(
    { type: 'about:blank', title: 'Error', status, detail, code, ...(errors ? { errors } : {}) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function roleOf(request: Request): Role | null {
  const header = request.headers.get('Authorization')
  const entry = Object.entries(TOKENS).find(([, token]) => header === `Bearer ${token}`)
  return entry ? (entry[0] as Role) : null
}

function page<T>(items: T[], url: URL): Page<T> {
  const number = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  return {
    content: items.slice(number * size, number * size + size),
    page: number,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  }
}

const unauthorized = () => problem(401, 'unauthorized', 'Autenticação necessária.')
const forbidden = () => problem(403, 'forbidden', 'Você não tem permissão para esta operação.')

/** Events as the API lists them: no payload outside the detail. */
function summaryOf(event: IntegrationEvent): IntegrationEvent {
  return { ...event, payload: null }
}

function withEvents(order: Order): Order {
  return {
    ...order,
    integrationEvents: db.events
      .filter((event) => event.orderId === order.id)
      .sort((a, b) => a.sequence - b.sequence)
      .map(summaryOf),
  }
}

function transition(
  id: string,
  from: OrderStatus,
  changes: Partial<Order>,
  event?: IntegrationEvent['eventType'],
) {
  const order = db.orders.find((candidate) => candidate.id === id)
  if (!order) {
    return problem(404, 'not_found', 'Pedido não encontrado.')
  }
  if (order.status !== from) {
    return problem(409, 'invalid_order_transition', `Pedido ${order.status} não pode mudar para ${changes.status}.`)
  }
  const updated = updateOrder(id, changes)
  if (event) {
    enqueueEvent(updated, event)
  }
  return HttpResponse.json(withEvents(updated))
}

function health(): IntegrationHealth {
  return {
    ...db.integration,
    pending: db.events.filter((event) => event.status === 'PENDING' || event.status === 'PROCESSING')
      .length,
    failed: db.events.filter((event) => event.status === 'FAILED').length,
  }
}

function admin(request: Request) {
  const role = roleOf(request)
  return !role ? unauthorized() : role !== 'ADMIN' ? forbidden() : null
}

function requeue(event: IntegrationEvent): void {
  Object.assign(event, {
    status: 'PENDING',
    failureKind: null,
    attemptsAtRetry: event.attempts,
    nextAttemptAt: '2026-10-07T12:05:00Z',
  })
}

/** A small fake of the POS API with the same auth rules as the backend. */
export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const { email, password } = (await request.json()) as { email: string; password: string }
    const user = Object.values(users).find((candidate) => candidate.email === email)
    if (!user || password !== PASSWORD) {
      return problem(401, 'invalid_credentials', 'E-mail ou senha inválidos.')
    }
    return HttpResponse.json({
      access_token: TOKENS[user.role],
      token_type: 'Bearer',
      expires_in: 28800,
      user,
    })
  }),

  http.get('/api/products', ({ request }) => {
    if (!roleOf(request)) return unauthorized()
    const url = new URL(request.url)
    const q = (url.searchParams.get('q') ?? '').toLowerCase()
    const active = url.searchParams.get('active')
    const found = db.products.filter(
      (product) =>
        (active === null || String(product.active) === active) &&
        (product.sku.toLowerCase().includes(q) || product.name.toLowerCase().includes(q)),
    )
    return HttpResponse.json(page(found, url))
  }),

  http.post('/api/products', async ({ request }) => {
    const role = roleOf(request)
    if (!role) return unauthorized()
    if (role !== 'ADMIN') return forbidden()
    const body = (await request.json()) as { sku: string; name: string; price: string; active: boolean }
    if (db.products.some((product) => product.sku === body.sku)) {
      return problem(409, 'duplicate_sku', `Já existe um produto com o SKU ${body.sku}.`)
    }
    const created = {
      ...body,
      id: crypto.randomUUID(),
      createdAt: '2026-10-07T12:00:00Z',
      updatedAt: '2026-10-07T12:00:00Z',
    }
    db.products.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/customers', ({ request }) => {
    if (!roleOf(request)) return unauthorized()
    const url = new URL(request.url)
    const q = (url.searchParams.get('q') ?? '').toLowerCase()
    const found = db.customers.filter(
      (customer) => customer.name.toLowerCase().includes(q) || customer.email.includes(q),
    )
    return HttpResponse.json(page(found, url))
  }),

  http.post('/api/customers', async ({ request }) => {
    if (!roleOf(request)) return unauthorized()
    const body = (await request.json()) as { name: string; email: string; document: string | null }
    if (db.customers.some((customer) => customer.email === body.email)) {
      return problem(409, 'duplicate_email', 'Já existe um cliente com este e-mail.')
    }
    const created = {
      ...body,
      document: body.document ?? null,
      id: crypto.randomUUID(),
      createdAt: '2026-10-07T12:00:00Z',
      updatedAt: '2026-10-07T12:00:00Z',
    }
    db.customers.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/orders', ({ request }) => {
    if (!roleOf(request)) return unauthorized()
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const found = db.orders
      .filter((order) => !status || order.status === status)
      .map((order): OrderSummary => {
        const summary: Partial<Order> = { ...order }
        delete summary.items
        delete summary.updatedAt
        delete summary.integrationEvents
        return summary as OrderSummary
      })
    return HttpResponse.json(page(found, url))
  }),

  http.get('/api/orders/:id', ({ request, params }) => {
    if (!roleOf(request)) return unauthorized()
    const order = db.orders.find((candidate) => candidate.id === params.id)
    return order
      ? HttpResponse.json(withEvents(order))
      : problem(404, 'not_found', 'Pedido não encontrado.')
  }),

  http.post('/api/orders', async ({ request }) => {
    if (!roleOf(request)) return unauthorized()
    const body = (await request.json()) as {
      customerId: string
      items: { productId: string; quantity: number }[]
    }
    const inactive = body.items.find(
      (item) => !db.products.find((product) => product.id === item.productId)?.active,
    )
    if (inactive) {
      return problem(422, 'product_inactive', 'Produto inativo não pode ser vendido.')
    }
    return HttpResponse.json(createOrderInDb(body.customerId, body.items), { status: 201 })
  }),

  http.post('/api/orders/:id/pay', async ({ request, params }) => {
    if (!roleOf(request)) return unauthorized()
    const { paymentMethod } = (await request.json()) as { paymentMethod: PaymentMethod }
    return transition(
      String(params.id),
      'PENDING',
      { status: 'PAID', paymentMethod, paidAt: '2026-10-07T12:01:00Z' },
      'ORDER_PAID',
    )
  }),

  http.post('/api/orders/:id/cancel', ({ request, params }) => {
    if (!roleOf(request)) return unauthorized()
    return transition(String(params.id), 'PENDING', {
      status: 'CANCELED',
      canceledAt: '2026-10-07T12:01:00Z',
    })
  }),

  http.post('/api/orders/:id/refund', ({ request, params }) => {
    const role = roleOf(request)
    if (!role) return unauthorized()
    if (role !== 'ADMIN') return forbidden()
    return transition(
      String(params.id),
      'PAID',
      { status: 'REFUNDED', refundedAt: '2026-10-07T12:02:00Z' },
      'ORDER_REFUNDED',
    )
  }),

  http.get('/api/integration/events', ({ request }) => {
    const denied = admin(request)
    if (denied) return denied
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const found = db.events
      .filter((event) => !status || event.status === status)
      .sort((a, b) => b.sequence - a.sequence)
      .map(summaryOf)
    return HttpResponse.json(page(found, url))
  }),

  http.get('/api/integration/events/:id', ({ request, params }) => {
    const denied = admin(request)
    if (denied) return denied
    const event = db.events.find((candidate) => candidate.id === params.id)
    return event ? HttpResponse.json(event) : problem(404, 'not_found', 'Evento não encontrado.')
  }),

  http.post('/api/integration/events/retry-configuration-failures', ({ request }) => {
    const denied = admin(request)
    if (denied) return denied
    const failures = db.events.filter(
      (event) => event.status === 'FAILED' && event.failureKind === 'CONFIGURATION',
    )
    failures.forEach(requeue)
    db.integration.pausedUntil = null
    return HttpResponse.json({ retried: failures.length })
  }),

  http.post('/api/integration/events/:id/retry', ({ request, params }) => {
    const denied = admin(request)
    if (denied) return denied
    const event = db.events.find((candidate) => candidate.id === params.id)
    if (!event) {
      return problem(404, 'not_found', 'Evento não encontrado.')
    }
    if (event.status !== 'FAILED') {
      return problem(409, 'integration_event_not_failed', 'Só eventos FAILED podem ser reprocessados.')
    }
    requeue(event)
    return HttpResponse.json(event)
  }),

  http.get('/api/integration/health', ({ request }) => admin(request) ?? HttpResponse.json(health())),
]
