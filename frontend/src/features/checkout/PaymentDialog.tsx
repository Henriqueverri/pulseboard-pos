import type { PaymentMethod } from '../../api/types'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { formatCents } from '../../lib/money'
import { PaymentMethodPicker } from './PaymentMethodPicker'

/** Simulated payment: the cashier only records how the customer paid. */
export function PaymentDialog({
  open,
  totalCents,
  paymentMethod,
  onPaymentMethodChange,
  issues,
  error,
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
  submitting: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Dialog open={open} title="Pagamento" onClose={() => !submitting && onClose()}>
      <div className="space-y-4">
        <ErrorAlert error={error} />
        {issues.length > 0 && (
          <Alert>
            {issues.map((issue) => (
              <p key={issue}>{issue}</p>
            ))}
          </Alert>
        )}
        <p className="text-sm">
          Total: <strong className="tabular-nums">{formatCents(totalCents)}</strong>
        </p>
        <PaymentMethodPicker value={paymentMethod} onChange={onPaymentMethodChange} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" disabled={submitting} onClick={onClose}>
            Voltar
          </Button>
          <Button disabled={submitting} onClick={onConfirm}>
            {submitting ? 'Processando…' : 'Confirmar pagamento'}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
