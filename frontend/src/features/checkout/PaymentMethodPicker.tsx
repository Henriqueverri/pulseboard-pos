import { CreditCardIcon, MoneyIcon, PixLogoIcon, type Icon } from '@phosphor-icons/react'
import type { PaymentMethod } from '../../api/types'
import { cn } from '../../lib/cn'
import { PAYMENT_METHOD_LABELS } from '../../lib/labels'

const METHODS = Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]

const ICONS: Record<PaymentMethod, Icon> = {
  CASH: MoneyIcon,
  CARD: CreditCardIcon,
  PIX: PixLogoIcon,
}

export function PaymentMethodPicker({
  value,
  onChange,
}: {
  value: PaymentMethod | null
  onChange: (method: PaymentMethod) => void
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium text-ink">Forma de pagamento</legend>
      <div className="grid grid-cols-3 gap-2">
        {METHODS.map((method) => {
          const MethodIcon = ICONS[method]
          const selected = value === method
          return (
            <label
              key={method}
              className={cn(
                'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border px-3 py-4 text-sm font-medium transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-brand-500/60',
                selected
                  ? 'border-brand-500 bg-brand-50 text-brand-700'
                  : 'border-ink/10 text-ink hover:bg-ink/[0.03]',
              )}
            >
              <input
                type="radio"
                name="paymentMethod"
                className="sr-only"
                value={method}
                checked={selected}
                onChange={() => onChange(method)}
              />
              <MethodIcon size={22} weight={selected ? 'fill' : 'regular'} aria-hidden="true" />
              {PAYMENT_METHOD_LABELS[method]}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
