import { ArrowLeftIcon } from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ordersApi } from '../../api/endpoints'
import type { Order } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { OrderStatusBadge } from '../../components/ui/Badge'
import { Card } from '../../components/ui/Card'
import { Skeleton } from '../../components/ui/States'
import { Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
import { formatDateTime } from '../../lib/dates'
import { PAYMENT_METHOD_LABELS } from '../../lib/labels'
import { formatMoney } from '../../lib/money'
import { useAuth } from '../auth/authContext'
import { isOpen, POLL_INTERVAL_MS } from '../integration/events'
import { OrderIntegrationStatus } from '../integration/OrderIntegrationStatus'
import { OrderActions } from './OrderActions'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  const { hasRole } = useAuth()
  const order = useQuery({
    queryKey: ['orders', 'detail', id],
    queryFn: () => ordersApi.get(id),
    // Follows a delivery in progress; stops once nothing is waiting.
    refetchInterval: (query) =>
      query.state.data?.integrationEvents.some(isOpen) ? POLL_INTERVAL_MS : false,
  })

  return (
    <div className="space-y-6">
      <Link
        to="/orders"
        className="inline-flex items-center gap-1.5 text-sm text-ink/65 transition-colors hover:text-ink"
      >
        <ArrowLeftIcon size={14} aria-hidden="true" />
        Voltar para pedidos
      </Link>

      <ErrorAlert error={order.error} />
      {order.isPending && (
        <div className="space-y-4" aria-busy="true">
          <span className="sr-only">Carregando…</span>
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-40 w-full" />
        </div>
      )}

      {order.data && <OrderDetail order={order.data} showIntegrationLink={hasRole('ADMIN')} />}
    </div>
  )
}

function OrderDetail({ order, showIntegrationLink }: { order: Order; showIntegrationLink: boolean }) {
  const timeline = [
    { label: 'Criado em', at: order.createdAt },
    { label: 'Pago em', at: order.paidAt },
    { label: 'Cancelado em', at: order.canceledAt },
    { label: 'Estornado em', at: order.refundedAt },
  ].filter((entry) => entry.at)

  return (
    <>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">Pedido #{order.number}</h1>
            <OrderStatusBadge status={order.status} />
          </div>
          <p className="mt-1 text-sm text-ink/65">Criado em {formatDateTime(order.createdAt)}</p>
        </div>
        <OrderActions order={order} />
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Card title="Itens" description={`${order.items.length} ${order.items.length === 1 ? 'produto' : 'produtos'}`} padding="none">
          <Table caption="Itens do pedido">
            <THead>
              <Th>SKU</Th>
              <Th>Produto</Th>
              <Th className="text-right">Qtd.</Th>
              <Th className="text-right">Preço unit.</Th>
              <Th className="text-right">Total</Th>
            </THead>
            <TBody>
              {order.items.map((item) => (
                <Tr key={item.id}>
                  <Td className="font-mono text-xs text-ink/65">{item.sku}</Td>
                  <Td className="font-medium">{item.productName}</Td>
                  <Td className="text-right tabular">{item.quantity}</Td>
                  <Td className="text-right text-ink/65 tabular">{formatMoney(item.unitPrice, order.currency)}</Td>
                  <Td className="text-right font-medium tabular">{formatMoney(item.lineTotal, order.currency)}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
          <div className="flex items-baseline justify-end gap-4 border-t border-ink/[0.07] px-5 py-4">
            <span className="text-sm text-ink/65">Total</span>
            <span className="text-xl font-semibold tracking-tight tabular">
              {formatMoney(order.total, order.currency)}
            </span>
          </div>
        </Card>

        <Card title="Resumo">
          <dl className="space-y-4 text-sm">
            <div>
              <dt className="text-xs text-ink/65">Cliente</dt>
              <dd className="mt-1.5 flex items-center gap-3">
                <Avatar name={order.customer.name} />
                <span className="min-w-0">
                  <span className="block truncate font-medium">{order.customer.name}</span>
                  <span className="block truncate text-xs text-ink/65">{order.customer.email}</span>
                </span>
              </dd>
            </div>
            <div>
              <dt className="text-xs text-ink/65">Pagamento</dt>
              <dd className="mt-0.5 font-medium">
                {order.paymentMethod ? PAYMENT_METHOD_LABELS[order.paymentMethod] : '—'}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-ink/65">Histórico</dt>
              <dd className="mt-1.5">
                <ol className="space-y-2 border-l border-ink/10 pl-4">
                  {timeline.map((entry) => (
                    <li key={entry.label} className="relative">
                      <span
                        className="absolute top-1.5 -left-[21px] size-2 rounded-full bg-brand-500 ring-4 ring-surface"
                        aria-hidden="true"
                      />
                      <span className="block text-xs text-ink/65">{entry.label}</span>
                      <span className="block tabular">{formatDateTime(entry.at)}</span>
                    </li>
                  ))}
                </ol>
              </dd>
            </div>
          </dl>
        </Card>
      </div>

      <OrderIntegrationStatus events={order.integrationEvents} showIntegrationLink={showIntegrationLink} />
    </>
  )
}
