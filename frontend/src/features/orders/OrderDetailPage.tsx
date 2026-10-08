import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ordersApi } from '../../api/endpoints'
import { ErrorAlert } from '../../components/ui/Alert'
import { OrderStatusBadge } from '../../components/ui/Badge'
import { Table, Td, Th } from '../../components/ui/Table'
import { formatDateTime } from '../../lib/dates'
import { PAYMENT_METHOD_LABELS } from '../../lib/labels'
import { formatMoney } from '../../lib/money'
import { OrderActions } from './OrderActions'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  const order = useQuery({
    queryKey: ['orders', 'detail', id],
    queryFn: () => ordersApi.get(id),
  })

  return (
    <section className="space-y-4">
      <Link to="/orders" className="text-sm text-indigo-700 hover:underline">
        ← Voltar para pedidos
      </Link>

      <ErrorAlert error={order.error} />
      {order.isPending && <p className="text-sm text-slate-500">Carregando…</p>}

      {order.data && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-semibold">Pedido #{order.data.number}</h1>
              <OrderStatusBadge status={order.data.status} />
            </div>
            <OrderActions order={order.data} />
          </div>

          <dl className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-slate-500">Cliente</dt>
              <dd className="font-medium">{order.data.customer.name}</dd>
              <dd className="text-slate-500">{order.data.customer.email}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Pagamento</dt>
              <dd className="font-medium">
                {order.data.paymentMethod ? PAYMENT_METHOD_LABELS[order.data.paymentMethod] : '—'}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Total</dt>
              <dd className="text-lg font-semibold tabular-nums">
                {formatMoney(order.data.total, order.data.currency)}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Criado em</dt>
              <dd>{formatDateTime(order.data.createdAt)}</dd>
            </div>
            {order.data.paidAt && (
              <div>
                <dt className="text-slate-500">Pago em</dt>
                <dd>{formatDateTime(order.data.paidAt)}</dd>
              </div>
            )}
            {order.data.canceledAt && (
              <div>
                <dt className="text-slate-500">Cancelado em</dt>
                <dd>{formatDateTime(order.data.canceledAt)}</dd>
              </div>
            )}
            {order.data.refundedAt && (
              <div>
                <dt className="text-slate-500">Estornado em</dt>
                <dd>{formatDateTime(order.data.refundedAt)}</dd>
              </div>
            )}
          </dl>

          <Table>
            <thead className="bg-slate-50">
              <tr>
                <Th>SKU</Th>
                <Th>Produto</Th>
                <Th className="text-right">Qtd.</Th>
                <Th className="text-right">Preço unit.</Th>
                <Th className="text-right">Total</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {order.data.items.map((item) => (
                <tr key={item.id}>
                  <Td className="font-mono text-xs">{item.sku}</Td>
                  <Td>{item.productName}</Td>
                  <Td className="text-right tabular-nums">{item.quantity}</Td>
                  <Td className="text-right tabular-nums">
                    {formatMoney(item.unitPrice, order.data.currency)}
                  </Td>
                  <Td className="text-right tabular-nums">
                    {formatMoney(item.lineTotal, order.data.currency)}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </>
      )}
    </section>
  )
}
