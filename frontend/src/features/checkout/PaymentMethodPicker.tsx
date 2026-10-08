import type { PaymentMethod } from '../../api/types'
import { PAYMENT_METHOD_LABELS } from '../../lib/labels'

const METHODS = Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]

export function PaymentMethodPicker({
  value,
  onChange,
}: {
  value: PaymentMethod | null
  onChange: (method: PaymentMethod) => void
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium text-slate-700">Forma de pagamento</legend>
      <div className="grid grid-cols-3 gap-2">
        {METHODS.map((method) => (
          <label
            key={method}
            className={`flex cursor-pointer items-center justify-center gap-2 rounded-md border px-3 py-3 text-sm font-medium ${
              value === method
                ? 'border-indigo-600 bg-indigo-50 text-indigo-800'
                : 'border-slate-300 hover:bg-slate-50'
            }`}
          >
            <input
              type="radio"
              name="paymentMethod"
              className="sr-only"
              value={method}
              checked={value === method}
              onChange={() => onChange(method)}
            />
            {PAYMENT_METHOD_LABELS[method]}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
