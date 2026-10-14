import { CheckIcon, WarningIcon } from '@phosphor-icons/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useReducer, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { ordersApi } from '../../api/endpoints'
import type { Order, PaymentMethod } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { buttonClasses } from '../../components/ui/styles'
import { PAYMENT_METHOD_LABELS } from '../../lib/labels'
import { formatCents, formatMoney } from '../../lib/money'
import { Cart } from './Cart'
import { cartReducer, cartTotals, emptyCart, type CartState } from './cartReducer'
import { CustomerPicker } from './CustomerPicker'
import { PaymentDialog, type StockShortage } from './PaymentDialog'
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

/** The cart lines named in an {@code insufficient_stock} error (keyed by SKU), with what is left. */
function stockShortages(error: unknown, cart: CartState): StockShortage[] {
  if (!(error instanceof ApiError) || error.code !== 'insufficient_stock') {
    return []
  }
  return Object.entries(error.errors).map(([sku, messages]) => ({
    sku,
    name: cart.items.find((item) => item.sku === sku)?.name ?? sku,
    message: messages.join(' '),
  }))
}

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
        if (payError instanceof ApiError && payError.code === 'insufficient_stock') {
          // Nothing was sold: the cashier fixes the cart and tries again with a new order.
          await ordersApi.cancel(order.id).catch(() => undefined)
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
      await queryClient.invalidateQueries({ queryKey: ['products'] })
    },
    onError: () => queryClient.invalidateQueries({ queryKey: ['products'] }),
  })
  const shortages = stockShortages(checkout.error, cart)

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
      <Card className="mx-auto mt-4 max-w-md" padding="none">
        <div className="flex flex-col items-center gap-4 px-6 py-10 text-center">
          {payError ? (
            <>
              <span className="flex size-14 items-center justify-center rounded-full bg-warning-soft text-warning-strong">
                <WarningIcon size={28} weight="fill" aria-hidden="true" />
              </span>
              <h1 className="text-xl font-semibold tracking-tight">
                Pedido #{order.number} criado, pagamento não concluído
              </h1>
              <div className="w-full text-left">
                <ErrorAlert error={payError} />
              </div>
              <p className="text-sm text-ink/65">
                O pedido está pendente. Tente o pagamento novamente pelo detalhe do pedido.
              </p>
            </>
          ) : (
            <>
              <span className="flex size-14 items-center justify-center rounded-full bg-success-soft text-success-strong">
                <CheckIcon size={28} weight="bold" aria-hidden="true" />
              </span>
              <div>
                <h1 className="text-xl font-semibold tracking-tight">Venda concluída</h1>
                <p className="mt-1 text-sm text-ink/65">
                  Pedido <strong className="font-semibold text-ink">#{order.number}</strong> ·{' '}
                  {order.customer.name}
                </p>
              </div>
              <p className="text-4xl font-semibold tracking-tight tabular">
                {formatMoney(order.total, order.currency)}
              </p>
              {order.paymentMethod && (
                <p className="text-sm text-ink/65">Pago com {PAYMENT_METHOD_LABELS[order.paymentMethod]}</p>
              )}
              <p className="text-xs text-ink/55">O envio ao PulseBoard acontece em segundos.</p>
            </>
          )}
        </div>
        <div className="flex justify-center gap-2 border-t border-ink/[0.07] px-6 py-4">
          <Link to={`/orders/${order.id}`} className={buttonClasses({ variant: 'outline' })}>
            Ver pedido
          </Link>
          <Button onClick={() => setResult(null)}>Nova venda</Button>
        </div>
      </Card>
    )
  }

  return (
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_400px]">
      <div className="min-w-0">
        <PageHeader title="Caixa" description="Busque os produtos, escolha o cliente e registre o pagamento." />
        <ProductSearch onAdd={(product) => dispatch({ type: 'add', product })} />
      </div>

      <Card
        title="Venda atual"
        aria-label="Venda atual"
        className="xl:sticky xl:top-8"
        actions={
          <Button
            variant="ghost"
            size="sm"
            disabled={cart.items.length === 0 && !cart.customer}
            onClick={() => {
              dispatch({ type: 'clear' })
              setIssues([])
            }}
          >
            Limpar
          </Button>
        }
      >
        <div className="space-y-5">
          <div className="space-y-2">
            <h3 className="text-xs font-medium text-ink/65">Cliente</h3>
            <CustomerPicker
              customer={cart.customer}
              onChange={(customer) => dispatch({ type: 'setCustomer', customer })}
            />
          </div>

          <div className="space-y-2">
            <h3 className="text-xs font-medium text-ink/65">Carrinho</h3>
            <Cart items={cart.items} dispatch={dispatch} />
          </div>

          <dl className="space-y-1.5 border-t border-ink/[0.07] pt-4 text-sm">
            <div className="flex justify-between text-ink/65">
              <dt>Itens</dt>
              <dd className="tabular">{totals.itemCount}</dd>
            </div>
            <div className="flex justify-between text-ink/65">
              <dt>Subtotal</dt>
              <dd className="tabular">{formatCents(totals.subtotalCents)}</dd>
            </div>
            <div className="flex items-baseline justify-between pt-1">
              <dt className="font-medium">Total</dt>
              <dd className="text-2xl font-semibold tracking-tight tabular" data-testid="cart-total">
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

          <Button size="lg" block onClick={startPayment}>
            Finalizar venda
          </Button>
        </div>
      </Card>

      <PaymentDialog
        open={paying}
        totalCents={totals.totalCents}
        paymentMethod={paymentMethod}
        onPaymentMethodChange={setPaymentMethod}
        issues={issues}
        error={checkout.error}
        shortages={shortages}
        submitting={checkout.isPending}
        onConfirm={confirmPayment}
        onClose={() => setPaying(false)}
      />
    </div>
  )
}
