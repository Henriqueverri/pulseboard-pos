import { PlusIcon, ReceiptIcon, XIcon } from '@phosphor-icons/react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { ordersApi, type OrderFilters } from '../../api/endpoints'
import type { OrderStatus } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { ErrorAlert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { OrderStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { FilterField, Select } from '../../components/ui/Field'
import { EmptyState, SkeletonRows } from '../../components/ui/States'
import { buttonClasses, controlClasses } from '../../components/ui/styles'
import { Pagination, Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
import { cn } from '../../lib/cn'
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

  const hasFilters = ['status', 'from', 'to', 'number'].some((key) => params.get(key))

  return (
    <>
      <PageHeader
        title="Pedidos"
        description="Vendas do caixa, do mais recente para o mais antigo."
        actions={
          <Link to="/" className={buttonClasses()}>
            <PlusIcon size={16} weight="bold" aria-hidden="true" />
            Nova venda
          </Link>
        }
      />

      <Card padding="none">
        <div className="flex flex-wrap items-end gap-3 border-b border-ink/[0.07] p-4">
          <FilterField label="Status">
            <Select
              className="w-40"
              value={params.get('status') ?? ''}
              onChange={(event) => update({ status: event.target.value })}
            >
              <option value="">Todos</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {ORDER_STATUS_LABELS[status]}
                </option>
              ))}
            </Select>
          </FilterField>
          <FilterField label="De">
            <input
              type="date"
              className={controlClasses()}
              value={params.get('from') ?? ''}
              onChange={(event) => update({ from: event.target.value })}
            />
          </FilterField>
          <FilterField label="Até">
            <input
              type="date"
              className={controlClasses()}
              value={params.get('to') ?? ''}
              onChange={(event) => update({ to: event.target.value })}
            />
          </FilterField>
          <FilterField label="Número">
            <input
              type="number"
              min={1}
              placeholder="1001"
              className={cn(controlClasses(), 'w-28')}
              value={params.get('number') ?? ''}
              onChange={(event) => update({ number: event.target.value })}
            />
          </FilterField>
          {hasFilters && (
            <Button
              variant="ghost"
              leading={<XIcon size={14} aria-hidden="true" />}
              onClick={() => setParams(new URLSearchParams(), { replace: true })}
            >
              Limpar filtros
            </Button>
          )}
        </div>

        {orders.error && (
          <div className="p-4">
            <ErrorAlert error={orders.error} />
          </div>
        )}
        {orders.isPending && <SkeletonRows />}
        {orders.data?.content.length === 0 && (
          <EmptyState
            icon={ReceiptIcon}
            title="Nenhum pedido encontrado."
            description={hasFilters ? 'Ajuste ou limpe os filtros.' : 'As vendas feitas no caixa aparecem aqui.'}
          />
        )}
        {orders.data && orders.data.content.length > 0 && (
          <>
            <Table caption="Pedidos">
              <THead>
                <Th>Número</Th>
                <Th>Data</Th>
                <Th>Cliente</Th>
                <Th>Status</Th>
                <Th>Pagamento</Th>
                <Th className="text-right">Total</Th>
              </THead>
              <TBody>
                {orders.data.content.map((order) => (
                  <Tr key={order.id}>
                    <Td>
                      <Link className="font-medium text-brand-600 hover:underline" to={`/orders/${order.id}`}>
                        #{order.number}
                      </Link>
                    </Td>
                    <Td className="whitespace-nowrap text-ink/65 tabular">{formatDateTime(order.createdAt)}</Td>
                    <Td>
                      <span className="flex items-center gap-2.5">
                        <Avatar name={order.customer.name} size="sm" />
                        {order.customer.name}
                      </span>
                    </Td>
                    <Td>
                      <OrderStatusBadge status={order.status} />
                    </Td>
                    <Td className="text-ink/65">
                      {order.paymentMethod ? PAYMENT_METHOD_LABELS[order.paymentMethod] : '—'}
                    </Td>
                    <Td className="text-right font-medium tabular">{formatMoney(order.total, order.currency)}</Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
            <Pagination
              page={orders.data.page}
              totalPages={orders.data.totalPages}
              totalElements={orders.data.totalElements}
              onChange={(page) => update({ page: page > 0 ? String(page) : '' })}
            />
          </>
        )}
      </Card>
    </>
  )
}
