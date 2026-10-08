import type { OrderStatus, PaymentMethod, Role } from '../api/types'

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pendente',
  PAID: 'Pago',
  CANCELED: 'Cancelado',
  REFUNDED: 'Estornado',
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Dinheiro',
  CARD: 'Cartão',
  PIX: 'Pix',
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrador',
  CASHIER: 'Caixa',
}
