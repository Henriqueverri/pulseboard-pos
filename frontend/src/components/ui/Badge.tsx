import type { ReactNode } from 'react'
import type { FailureKind, IntegrationEventStatus, OrderStatus } from '../../api/types'
import {
  FAILURE_KIND_LABELS,
  INTEGRATION_STATUS_LABELS,
  ORDER_STATUS_LABELS,
} from '../../lib/labels'

type Tone = 'gray' | 'green' | 'amber' | 'red' | 'blue'

const tones: Record<Tone, string> = {
  gray: 'bg-slate-100 text-slate-700',
  green: 'bg-emerald-100 text-emerald-800',
  amber: 'bg-amber-100 text-amber-800',
  red: 'bg-red-100 text-red-800',
  blue: 'bg-sky-100 text-sky-800',
}

export function Badge({ tone = 'gray', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}

const statusTones: Record<OrderStatus, Tone> = {
  PENDING: 'amber',
  PAID: 'green',
  CANCELED: 'gray',
  REFUNDED: 'red',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={statusTones[status]}>{ORDER_STATUS_LABELS[status]}</Badge>
}

const integrationTones: Record<IntegrationEventStatus, Tone> = {
  PENDING: 'amber',
  PROCESSING: 'blue',
  SENT: 'green',
  FAILED: 'red',
}

export function IntegrationStatusBadge({
  status,
  failureKind,
}: {
  status: IntegrationEventStatus
  failureKind: FailureKind | null
}) {
  return (
    <Badge tone={integrationTones[status]}>
      {INTEGRATION_STATUS_LABELS[status]}
      {failureKind && ` (${FAILURE_KIND_LABELS[failureKind]})`}
    </Badge>
  )
}
