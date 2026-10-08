import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { ordersApi, type OrderFilters } from '../../api/endpoints'
import type { OrderStatus } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { OrderStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Pagination, Table, Td, Th } from '../../components/ui/Table'
import { endOfLocalDay, formatDateTime, startOfLocalDay } from '../../lib/dates'
import { ORDER_STATUS_LABELS, PAYMENT_METHOD_LABELS } from '../../lib/labels'
import { formatMoney } from '../../lib/money'

const STATUSES = Object.keys(ORDER_STATUS_LABELS) as OrderStatus[]
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** Filters live in the URL so a filtered list survives reloads and can be shared. */
function filtersFrom(params: URLSearchParams): OrderFilters {
  const status = params.get('status')
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const number = Number(params.get('number'))
  const page = Number(params.get('page'))
  return {
    status: STATUSES.includes(status as OrderStatus) ? (status as OrderStatus) : undefined,
    from: DATE_PATTERN.test(from) ? startOfLocalDay(from) : undefined,
    to: DATE_PATTERN.test(to) ? endOfLocalDay(to) : undefined,
    number: Number.isSafeInteger(number) && number > 0 ? number : undefined,
    page: Number.isSafeInteger(page) && page > 0 ? page : 0,
    size: 20,
  }
}

export function OrdersPage() {
  const [params, setParams] = useSearchParams()
  const filters = filtersFrom(params)

  const orders = useQuery({
    queryKey: ['orders', 'list', filters],
    queryFn: () => ordersApi.list(filters),
    placeholderData: keepPreviousData,
  })

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) {
        next.set(key, value)
      } else {
        next.delete(key)
      }
    }
    if (!('page' in changes)) {
      next.delete('page')
    }
    setParams(next, { replace: true })
  }

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">Pedidos</h1>

      <div className="flex flex-wrap items-end gap-3 text-sm">
        <label className="space-y-1">
          <span className="block font-medium text-slate-700">Status</span>
          <select
            className="rounded-md border border-slate-300 px-3 py-2"
            value={params.get('status') ?? ''}
            onChange={(event) => update({ status: event.target.value })}
          >
            <option value="">Todos</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {ORDER_STATUS_LABELS[status]}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1">
          <span className="block font-medium text-slate-700">De</span>
          <input
            type="date"
            className="rounded-md border border-slate-300 px-3 py-2"
            value={params.get('from') ?? ''}
            onChange={(event) => update({ from: event.target.value })}
          />
        </label>
        <label className="space-y-1">
          <span className="block font-medium text-slate-700">Até</span>
          <input
            type="date"
            className="rounded-md border border-slate-300 px-3 py-2"
            value={params.get('to') ?? ''}
            onChange={(event) => update({ to: event.target.value })}
          />
        </label>
        <label className="space-y-1">
          <span className="block font-medium text-slate-700">Número</span>
          <input
            type="number"
            min={1}
            className="w-28 rounded-md border border-slate-300 px-3 py-2"
            value={params.get('number') ?? ''}
            onChange={(event) => update({ number: event.target.value })}
          />
        </label>
        <Button variant="ghost" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
          Limpar filtros
        </Button>
      </div>

      <ErrorAlert error={orders.error} />

      {orders.data && (
        <>
          <Table>
            <thead className="bg-slate-50">
              <tr>
                <Th>Número</Th>
                <Th>Data</Th>
                <Th>Cliente</Th>
                <Th>Status</Th>
                <Th>Pagamento</Th>
                <Th className="text-right">Total</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {orders.data.content.map((order) => (
                <tr key={order.id}>
                  <Td>
                    <Link className="font-medium text-indigo-700 hover:underline" to={`/orders/${order.id}`}>
                      #{order.number}
                    </Link>
                  </Td>
                  <Td>{formatDateTime(order.createdAt)}</Td>
                  <Td>{order.customer.name}</Td>
                  <Td>
                    <OrderStatusBadge status={order.status} />
                  </Td>
                  <Td>{order.paymentMethod ? PAYMENT_METHOD_LABELS[order.paymentMethod] : '—'}</Td>
                  <Td className="text-right tabular-nums">
                    {formatMoney(order.total, order.currency)}
                  </Td>
                </tr>
              ))}
              {orders.data.content.length === 0 && (
                <tr>
                  <Td colSpan={6} className="py-6 text-center text-slate-500">
                    Nenhum pedido encontrado.
                  </Td>
                </tr>
              )}
            </tbody>
          </Table>
          <Pagination
            page={orders.data.page}
            totalPages={orders.data.totalPages}
            onChange={(page) => update({ page: page > 0 ? String(page) : '' })}
          />
        </>
      )}
      {orders.isPending && <p className="text-sm text-slate-500">Carregando…</p>}
    </section>
  )
}
