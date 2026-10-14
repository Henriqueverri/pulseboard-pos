import type { PaymentMethod } from '../../api/types'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { formatCents } from '../../lib/money'
import { PaymentMethodPicker } from './PaymentMethodPicker'

export interface StockShortage {
  sku: string
  name: string
  message: string
}

/** Simulated payment: the cashier only records how the customer paid. */
export function PaymentDialog({
  open,
  totalCents,
  paymentMethod,
  onPaymentMethodChange,
  issues,
  error,
  shortages,
  submitting,
  onConfirm,
  onClose,
}: {
  open: boolean
  totalCents: number
  paymentMethod: PaymentMethod | null
  onPaymentMethodChange: (method: PaymentMethod) => void
  issues: string[]
  error: unknown
  /** Products short of stock in the last attempt; the sale was not made. */
  shortages: StockShortage[]
  submitting: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Dialog
      open={open}
      title="Pagamento"
      onClose={() => !submitting && onClose()}
      footer={
        <>
          <Button variant="outline" disabled={submitting} onClick={onClose}>
            Voltar
          </Button>
          <Button loading={submitting} onClick={onConfirm}>
            Confirmar pagamento
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        {shortages.length > 0 ? (
          <Alert title="Sem saldo para concluir a venda">
            <ul className="space-y-0.5">
              {shortages.map((shortage) => (
                <li key={shortage.sku}>
                  <strong className="font-medium">{shortage.name}</strong>: {shortage.message}
                </li>
              ))}
            </ul>
            <p className="mt-1.5">Volte, ajuste as quantidades no carrinho e confirme de novo.</p>
          </Alert>
        ) : (
          <ErrorAlert error={error} />
        )}
        {issues.length > 0 && (
          <Alert>
            {issues.map((issue) => (
              <p key={issue}>{issue}</p>
            ))}
          </Alert>
        )}
        <div className="rounded-xl bg-ink/[0.03] px-4 py-3">
          <p className="text-xs text-ink/65">Total a receber</p>
          <p className="mt-0.5 text-2xl font-semibold tracking-tight tabular">{formatCents(totalCents)}</p>
        </div>
        <PaymentMethodPicker value={paymentMethod} onChange={onPaymentMethodChange} />
      </div>
    </Dialog>
  )
}
