import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useReducer, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { ordersApi } from '../../api/endpoints'
import type { Order, PaymentMethod } from '../../api/types'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { PAYMENT_METHOD_LABELS } from '../../lib/labels'
import { formatCents, formatMoney } from '../../lib/money'
import { Cart } from './Cart'
import { cartReducer, cartTotals, emptyCart, type CartState } from './cartReducer'
import { CustomerPicker } from './CustomerPicker'
import { PaymentDialog } from './PaymentDialog'
import { ProductSearch } from './ProductSearch'
import { checkoutSchema, type CheckoutInput } from './schema'

interface CheckoutResult {
  order: Order
  /** Set when the order was created but the payment failed: it stays PENDING. */
  payError: unknown
}

function toCheckoutInput(cart: CartState, paymentMethod: PaymentMethod | null) {
  return {
    customerId: cart.customer?.id ?? '',
    items: cart.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
    paymentMethod,
  }
}

function issuesOf(result: { success: false; error: { issues: { message: string }[] } }) {
  return [...new Set(result.error.issues.map((issue) => issue.message))]
}

const cartSchema = checkoutSchema.omit({ paymentMethod: true })

export function CheckoutPage() {
  const queryClient = useQueryClient()
  const [cart, dispatch] = useReducer(cartReducer, emptyCart)
  const [paying, setPaying] = useState(false)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null)
  const [issues, setIssues] = useState<string[]>([])
  const [result, setResult] = useState<CheckoutResult | null>(null)
  const totals = cartTotals(cart)

  const checkout = useMutation({
    mutationFn: async (input: CheckoutInput): Promise<CheckoutResult> => {
      const order = await ordersApi.create({ customerId: input.customerId, items: input.items })
      try {
        return { order: await ordersApi.pay(order.id, input.paymentMethod), payError: null }
      } catch (payError) {
        if (payError instanceof ApiError && payError.status === 401) {
          throw payError
        }
        return { order, payError }
      }
    },
    onSuccess: async (outcome) => {
      dispatch({ type: 'clear' })
      setPaying(false)
      setResult(outcome)
      await queryClient.invalidateQueries({ queryKey: ['orders', 'list'] })
    },
  })

  function startPayment() {
    const parsed = cartSchema.safeParse(toCheckoutInput(cart, null))
    if (!parsed.success) {
      setIssues(issuesOf(parsed))
      return
    }
    setIssues([])
    setPaymentMethod(null)
    checkout.reset()
    setPaying(true)
  }

  function confirmPayment() {
    const parsed = checkoutSchema.safeParse(toCheckoutInput(cart, paymentMethod))
    if (!parsed.success) {
      setIssues(issuesOf(parsed))
      return
    }
    setIssues([])
    checkout.mutate(parsed.data)
  }

  if (result) {
    const { order, payError } = result
    return (
      <section className="mx-auto max-w-lg space-y-4 rounded-lg border border-slate-200 bg-white p-6 text-center">
        {payError ? (
          <>
            <h1 className="text-xl font-semibold">Pedido #{order.number} criado, pagamento não concluído</h1>
            <ErrorAlert error={payError} />
            <p className="text-sm text-slate-600">
              O pedido está pendente. Tente o pagamento novamente pelo detalhe do pedido.
            </p>
          </>
        ) : (
          <>
            <h1 className="text-xl font-semibold text-emerald-700">Venda concluída</h1>
            <p className="text-sm text-slate-600">
              Pedido <strong>#{order.number}</strong> · {order.customer.name}
            </p>
            <p className="text-3xl font-semibold tabular-nums">
              {formatMoney(order.total, order.currency)}
            </p>
            {order.paymentMethod && (
              <p className="text-sm text-slate-600">
                Pago com {PAYMENT_METHOD_LABELS[order.paymentMethod]}
              </p>
            )}
          </>
        )}
        <div className="flex justify-center gap-2">
          <Link
            to={`/orders/${order.id}`}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium hover:bg-slate-100"
          >
            Ver pedido
          </Link>
          <Button onClick={() => setResult(null)}>Nova venda</Button>
        </div>
      </section>
    )
  }

  return (
    <section className="grid gap-6 lg:grid-cols-[1fr_24rem]">
      <div className="space-y-4">
        <h1 className="text-2xl font-semibold">Caixa</h1>
        <ProductSearch onAdd={(product) => dispatch({ type: 'add', product })} />
      </div>

      <aside className="space-y-4">
        <h2 className="text-lg font-semibold">Cliente</h2>
        <CustomerPicker
          customer={cart.customer}
          onChange={(customer) => dispatch({ type: 'setCustomer', customer })}
        />

        <h2 className="text-lg font-semibold">Carrinho</h2>
        <Cart items={cart.items} dispatch={dispatch} />

        <dl className="space-y-1 rounded-md border border-slate-200 bg-white p-3 text-sm">
          <div className="flex justify-between">
            <dt>Itens</dt>
            <dd className="tabular-nums">{totals.itemCount}</dd>
          </div>
          <div className="flex justify-between">
            <dt>Subtotal</dt>
            <dd className="tabular-nums">{formatCents(totals.subtotalCents)}</dd>
          </div>
          <div className="flex justify-between border-t border-slate-100 pt-2 text-base font-semibold">
            <dt>Total</dt>
            <dd className="tabular-nums" data-testid="cart-total">
              {formatCents(totals.totalCents)}
            </dd>
          </div>
        </dl>

        {issues.length > 0 && !paying && (
          <Alert>
            {issues.map((issue) => (
              <p key={issue}>{issue}</p>
            ))}
          </Alert>
        )}

        <div className="flex gap-2">
          <Button className="flex-1" onClick={startPayment}>
            Finalizar venda
          </Button>
          <Button
            variant="secondary"
            disabled={cart.items.length === 0 && !cart.customer}
            onClick={() => {
              dispatch({ type: 'clear' })
              setIssues([])
            }}
          >
            Limpar
          </Button>
        </div>
      </aside>

      <PaymentDialog
        open={paying}
        totalCents={totals.totalCents}
        paymentMethod={paymentMethod}
        onPaymentMethodChange={setPaymentMethod}
        issues={issues}
        error={checkout.error}
        submitting={checkout.isPending}
        onConfirm={confirmPayment}
        onClose={() => setPaying(false)}
      />
    </section>
  )
}
