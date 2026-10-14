import type {
  FailureKind,
  IntegrationEventStatus,
  IntegrationEventType,
  OrderStatus,
  PaymentMethod,
  Role,
  StockMovementKind,
} from '../api/types'

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

export const INTEGRATION_STATUS_LABELS: Record<IntegrationEventStatus, string> = {
  PENDING: 'Pendente',
  PROCESSING: 'Enviando',
  SENT: 'Enviado',
  FAILED: 'Falhou',
}

export const FAILURE_KIND_LABELS: Record<FailureKind, string> = {
  PERMANENT: 'rejeitado',
  EXHAUSTED: 'tentativas esgotadas',
  CONFIGURATION: 'configuração',
}

export const STOCK_MOVEMENT_LABELS: Record<StockMovementKind, string> = {
  SALE: 'Venda',
  REFUND: 'Estorno',
  RECEIPT: 'Entrada',
  ADJUSTMENT: 'Ajuste',
}

export const EVENT_TYPE_LABELS: Record<IntegrationEventType, string> = {
  ORDER_PAID: 'Venda',
  ORDER_REFUNDED: 'Estorno',
  STOCK_CHANGED: 'Estoque',
}
