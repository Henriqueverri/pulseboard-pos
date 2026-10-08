/** Shapes of the POS API. Money is always a decimal string ("79.90"). */

export type Role = 'ADMIN' | 'CASHIER'

export interface User {
  id: string
  name: string
  email: string
  role: Role
}

export interface LoginResponse {
  access_token: string
  token_type: 'Bearer'
  expires_in: number
  user: User
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Product {
  id: string
  sku: string
  name: string
  price: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductInput {
  sku: string
  name: string
  price: string
  active: boolean
}

export interface Customer {
  id: string
  name: string
  email: string
  document: string | null
  createdAt: string
  updatedAt: string
}

export interface CustomerInput {
  name: string
  email: string
  document?: string | null
}

export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELED' | 'REFUNDED'

export type PaymentMethod = 'CASH' | 'CARD' | 'PIX'

export interface OrderCustomer {
  id: string
  name: string
  email: string
}

export interface OrderItem {
  id: string
  productId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: string
  lineTotal: string
}

export interface OrderSummary {
  id: string
  number: number
  status: OrderStatus
  currency: string
  paymentMethod: PaymentMethod | null
  customer: OrderCustomer
  total: string
  createdAt: string
  paidAt: string | null
  canceledAt: string | null
  refundedAt: string | null
}

export interface Order extends OrderSummary {
  items: OrderItem[]
  updatedAt: string
  /** Deliveries of this order to PulseBoard, oldest first. */
  integrationEvents: IntegrationEvent[]
}

export type IntegrationEventType = 'ORDER_PAID' | 'ORDER_REFUNDED'

export type IntegrationEventStatus = 'PENDING' | 'PROCESSING' | 'SENT' | 'FAILED'

/** Why an event is FAILED; none is retried automatically. */
export type FailureKind = 'PERMANENT' | 'EXHAUSTED' | 'CONFIGURATION'

/** One outbox event. `payload` (the exact body sent to PulseBoard) only comes in the detail. */
export interface IntegrationEvent {
  id: string
  sequence: number
  orderId: string
  orderNumber: number | null
  externalId: string
  eventType: IntegrationEventType
  status: IntegrationEventStatus
  failureKind: FailureKind | null
  attempts: number
  attemptsAtRetry: number
  nextAttemptAt: string | null
  lastAttemptAt: string | null
  lastHttpStatus: number | null
  lastErrorCode: string | null
  lastError: string | null
  /** X-Request-Id sent on every attempt of this event. */
  requestId: string
  /** X-Request-Id of PulseBoard's last response. */
  lastRequestId: string | null
  remoteId: string | null
  processedAt: string | null
  createdAt: string
  updatedAt: string
  /** Sequence of the earlier unsent event of the same order holding this one back. */
  blockedBy: number | null
  payload: unknown
}

export interface IntegrationHealth {
  enabled: boolean
  pausedUntil: string | null
  targetUrl: string
  keyPrefix: string | null
  pending: number
  failed: number
  lastSentAt: string | null
}

export interface CreateOrderInput {
  customerId: string
  items: { productId: string; quantity: number }[]
}

/** RFC 9457 body returned by every API error. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  errors?: Record<string, string[]>
}
