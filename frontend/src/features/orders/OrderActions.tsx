import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ordersApi } from '../../api/endpoints'
import type { Order, PaymentMethod } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { formatMoney } from '../../lib/money'
import { useAuth } from '../auth/authContext'
import { PaymentMethodPicker } from '../checkout/PaymentMethodPicker'

type Pending = 'pay' | 'cancel' | 'refund' | null

/**
 * The transitions the current user may run on this order. The server enforces the same rules;
 * hiding the buttons just avoids offering what would be rejected.
 */
export function OrderActions({ order }: { order: Order }) {
  const { hasRole } = useAuth()
  const queryClient = useQueryClient()
  const [pending, setPending] = useState<Pending>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null)

  const transition = useMutation({
    mutationFn: (action: Exclude<Pending, null>) => {
      switch (action) {
        case 'pay':
          return ordersApi.pay(order.id, paymentMethod as PaymentMethod)
        case 'cancel':
          return ordersApi.cancel(order.id)
        case 'refund':
          return ordersApi.refund(order.id)
      }
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['orders', 'detail', order.id], updated)
      await queryClient.invalidateQueries({ queryKey: ['orders', 'list'] })
      close()
    },
    // A 409 means someone else changed the order: show the message and reload the current state.
    onError: () => queryClient.invalidateQueries({ queryKey: ['orders', 'detail', order.id] }),
  })

  function open(action: Exclude<Pending, null>) {
    transition.reset()
    setPaymentMethod(null)
    setPending(action)
  }

  function close() {
    setPending(null)
  }

  const canPay = order.status === 'PENDING'
  const canCancel = order.status === 'PENDING'
  const canRefund = order.status === 'PAID' && hasRole('ADMIN')

  if (!canPay && !canCancel && !canRefund) {
    return null
  }

  const total = formatMoney(order.total, order.currency)
  const titles = {
    pay: `Pagar pedido #${order.number}`,
    cancel: `Cancelar pedido #${order.number}`,
    refund: `Estornar pedido #${order.number}`,
  }

  return (
    <div className="flex flex-wrap gap-2">
      {canPay && <Button onClick={() => open('pay')}>Pagar</Button>}
      {canCancel && (
        <Button variant="secondary" onClick={() => open('cancel')}>
          Cancelar pedido
        </Button>
      )}
      {canRefund && (
        <Button variant="danger" onClick={() => open('refund')}>
          Estornar
        </Button>
      )}

      <Dialog open={pending !== null} title={pending ? titles[pending] : ''} onClose={close}>
        <div className="space-y-4">
          <ErrorAlert error={transition.error} />
          {pending === 'pay' && (
            <>
              <p className="text-sm">
                Total a receber: <strong>{total}</strong>
              </p>
              <PaymentMethodPicker value={paymentMethod} onChange={setPaymentMethod} />
            </>
          )}
          {pending === 'cancel' && (
            <p className="text-sm">O pedido pendente de {total} será cancelado. Esta ação não pode ser desfeita.</p>
          )}
          {pending === 'refund' && (
            <p className="text-sm">O pagamento de {total} será estornado. Esta ação não pode ser desfeita.</p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={close}>
              Voltar
            </Button>
            <Button
              variant={pending === 'pay' ? 'primary' : 'danger'}
              disabled={transition.isPending || (pending === 'pay' && !paymentMethod)}
              onClick={() => pending && transition.mutate(pending)}
            >
              {transition.isPending
                ? 'Enviando…'
                : pending === 'pay'
                  ? 'Confirmar pagamento'
                  : pending === 'cancel'
                    ? 'Confirmar cancelamento'
                    : 'Confirmar estorno'}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  )
}
