import { ClockCounterClockwiseIcon } from '@phosphor-icons/react'
import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { stockApi } from '../../api/endpoints'
import type { Product, StockMovement } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { StockBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { SelectField, TextField } from '../../components/ui/Field'
import { EmptyState, SkeletonRows } from '../../components/ui/States'
import { Pagination, Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
import { formatDateTime } from '../../lib/dates'
import { applyServerErrors } from '../../lib/formErrors'
import { STOCK_MOVEMENT_LABELS } from '../../lib/labels'
import { stockMovementSchema, type StockMovementPayload } from './schema'

const HISTORY_SIZE = 10

function signed(quantity: number): string {
  return quantity > 0 ? `+${quantity}` : String(quantity)
}

/** Balance, receipts and adjustments, and the history of one product with stock control. */
export function StockDialog({ product, onClose }: { product: Product | null; onClose: () => void }) {
  return (
    <Dialog
      open={product !== null}
      size="lg"
      title={product ? `Estoque · ${product.name}` : 'Estoque'}
      onClose={onClose}
    >
      {product && <StockPanel key={product.id} product={product} />}
    </Dialog>
  )
}

function StockPanel({ product }: { product: Product }) {
  const [balance, setBalance] = useState(product.stockQuantity)
  const [page, setPage] = useState(0)
  const lowStock = balance <= product.stockMin

  const history = useQuery({
    queryKey: ['products', 'stock-movements', product.id, page],
    queryFn: () => stockApi.movements(product.id, page, HISTORY_SIZE),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end gap-x-8 gap-y-3 rounded-xl bg-ink/[0.03] px-4 py-3">
        <div>
          <p className="text-xs text-ink/65">Saldo atual</p>
          <p className="mt-0.5 flex items-center gap-2 text-2xl font-semibold tracking-tight tabular">
            <span data-testid="stock-balance">{balance}</span>
            <span className="text-sm font-normal text-ink/65">un.</span>
            <StockBadge product={{ lowStock, stockQuantity: balance }} />
          </p>
        </div>
        <div>
          <p className="text-xs text-ink/65">Mínimo</p>
          <p className="mt-0.5 text-sm font-medium tabular">{product.stockMin} un.</p>
        </div>
        <p className="font-mono text-xs text-ink/55 sm:ml-auto">{product.sku}</p>
      </div>

      <MovementForm
        productId={product.id}
        onRecorded={(movement) => {
          setBalance(movement.balanceAfter)
          setPage(0)
        }}
      />

      <section aria-label="Histórico de movimentações" className="space-y-2">
        <h3 className="flex items-center gap-1.5 text-sm font-medium">
          <ClockCounterClockwiseIcon size={16} aria-hidden="true" />
          Histórico
        </h3>
        <div className="rounded-xl border border-ink/[0.07]">
          <ErrorAlert error={history.error} />
          {history.isPending && <SkeletonRows rows={3} />}
          {history.data?.content.length === 0 && (
            <EmptyState compact icon={ClockCounterClockwiseIcon} title="Nenhuma movimentação ainda." />
          )}
          {history.data && history.data.content.length > 0 && (
            <>
              <Table caption="Movimentações de estoque">
                <THead>
                  <Th>Quando</Th>
                  <Th>Tipo</Th>
                  <Th className="text-right">Qtd.</Th>
                  <Th className="text-right">Saldo</Th>
                  <Th>Detalhe</Th>
                </THead>
                <TBody>
                  {history.data.content.map((movement) => (
                    <Tr key={movement.id}>
                      <Td className="text-xs whitespace-nowrap text-ink/65">
                        {formatDateTime(movement.createdAt)}
                      </Td>
                      <Td>{STOCK_MOVEMENT_LABELS[movement.kind]}</Td>
                      <Td
                        className={
                          movement.quantity < 0
                            ? 'text-right font-medium text-danger-strong tabular'
                            : 'text-right font-medium text-success-strong tabular'
                        }
                      >
                        {signed(movement.quantity)}
                      </Td>
                      <Td className="text-right tabular">{movement.balanceAfter}</Td>
                      <Td className="text-xs text-ink/65">
                        <MovementDetail movement={movement} />
                      </Td>
                    </Tr>
                  ))}
                </TBody>
              </Table>
              <Pagination
                page={history.data.page}
                totalPages={history.data.totalPages}
                totalElements={history.data.totalElements}
                onChange={setPage}
              />
            </>
          )}
        </div>
      </section>
    </div>
  )
}

function MovementDetail({ movement }: { movement: StockMovement }) {
  return (
    <span className="flex flex-col">
      {movement.orderId && (
        <Link to={`/orders/${movement.orderId}`} className="text-brand-700 hover:underline">
          Pedido #{movement.orderNumber}
        </Link>
      )}
      {movement.reason && <span>{movement.reason}</span>}
      {movement.userName && <span className="text-ink/50">{movement.userName}</span>}
    </span>
  )
}

function MovementForm({
  productId,
  onRecorded,
}: {
  productId: string
  onRecorded: (movement: StockMovement) => void
}) {
  const queryClient = useQueryClient()
  const [generalError, setGeneralError] = useState<unknown>(null)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(stockMovementSchema),
    defaultValues: { kind: 'RECEIPT' as const, quantity: '', reason: '' },
  })
  const kind = useWatch({ control, name: 'kind' })

  const record = useMutation({
    mutationFn: (payload: StockMovementPayload) => stockApi.record(productId, payload),
    onSuccess: async (movement) => {
      reset({ kind, quantity: '', reason: '' })
      onRecorded(movement)
      await queryClient.invalidateQueries({ queryKey: ['products'] })
    },
    onError: (error) => {
      const mapped = applyServerErrors(error, setError, ['kind', 'quantity', 'reason'], {
        insufficient_stock: 'quantity',
      })
      setGeneralError(mapped ? null : error)
    },
  })

  return (
    <form
      noValidate
      aria-label="Movimentar estoque"
      className="space-y-3"
      onSubmit={handleSubmit((payload) => {
        setGeneralError(null)
        record.mutate(payload)
      })}
    >
      <ErrorAlert error={generalError} />
      <div className="grid gap-3 sm:grid-cols-[150px_140px_minmax(0,1fr)]">
        <SelectField label="Movimentação" {...register('kind')}>
          <option value="RECEIPT">Entrada</option>
          <option value="ADJUSTMENT">Ajuste</option>
        </SelectField>
        <TextField
          label="Quantidade"
          inputMode="numeric"
          autoComplete="off"
          placeholder={kind === 'RECEIPT' ? '24' : '-3'}
          error={errors.quantity?.message}
          {...register('quantity')}
        />
        <TextField
          label="Motivo"
          optional={kind === 'RECEIPT'}
          autoComplete="off"
          placeholder={kind === 'RECEIPT' ? 'Nota fiscal 1234' : 'Contagem, quebra, perda…'}
          error={errors.reason?.message}
          {...register('reason')}
        />
      </div>
      <div className="flex justify-end">
        <Button type="submit" loading={record.isPending}>
          Registrar {kind === 'RECEIPT' ? 'entrada' : 'ajuste'}
        </Button>
      </div>
    </form>
  )
}
