import type { ReactNode } from 'react'
import type { FailureKind, IntegrationEventStatus, OrderStatus } from '../../api/types'
import { cn } from '../../lib/cn'
import {
  FAILURE_KIND_LABELS,
  INTEGRATION_STATUS_LABELS,
  ORDER_STATUS_LABELS,
} from '../../lib/labels'

export type TagTone = 'neutral' | 'success' | 'warning' | 'info' | 'danger' | 'brand'

const tones: Record<TagTone, { body: string; dot: string }> = {
  neutral: { body: 'bg-ink/[0.05] text-ink/70', dot: 'bg-ink/40' },
  success: { body: 'bg-success-soft text-success-strong', dot: 'bg-success' },
  warning: { body: 'bg-warning-soft text-warning-strong', dot: 'bg-warning' },
  info: { body: 'bg-info-soft text-info-strong', dot: 'bg-info' },
  danger: { body: 'bg-danger-soft text-danger-strong', dot: 'bg-danger' },
  brand: { body: 'bg-brand-50 text-brand-700', dot: 'bg-brand-500' },
}

export function Tag({
  tone = 'neutral',
  dot = false,
  size = 'md',
  children,
}: {
  tone?: TagTone
  dot?: boolean
  size?: 'sm' | 'md'
  children: ReactNode
}) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1.5 rounded-md font-medium whitespace-nowrap',
        tones[tone].body,
        size === 'sm' ? 'h-5 px-1.5 text-[11px]' : 'h-6 px-2 text-xs',
      )}
    >
      {dot && <span className={cn('size-1.5 rounded-full', tones[tone].dot)} aria-hidden="true" />}
      {children}
    </span>
  )
}

const orderTones: Record<OrderStatus, TagTone> = {
  PENDING: 'warning',
  PAID: 'success',
  CANCELED: 'danger',
  REFUNDED: 'info',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <Tag tone={orderTones[status]} dot>
      {ORDER_STATUS_LABELS[status]}
    </Tag>
  )
}

const integrationTones: Record<IntegrationEventStatus, TagTone> = {
  PENDING: 'warning',
  PROCESSING: 'info',
  SENT: 'success',
  FAILED: 'danger',
}

export function IntegrationStatusBadge({
  status,
  failureKind,
}: {
  status: IntegrationEventStatus
  failureKind: FailureKind | null
}) {
  return (
    <Tag tone={integrationTones[status]} dot>
      {INTEGRATION_STATUS_LABELS[status]}
      {failureKind && ` (${FAILURE_KIND_LABELS[failureKind]})`}
    </Tag>
  )
}
