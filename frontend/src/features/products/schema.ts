import { z } from 'zod'
import { centsToMoneyString, parseMoneyToCents } from '../../lib/money'

const MAX_CENTS = 999_999_999_999
export const MAX_STOCK = 1_000_000

/** Form values are what the user typed; the output is the API body (price as "79.90"). */
export const productSchema = z.object({
  sku: z
    .string()
    .trim()
    .min(1, { error: 'Informe o SKU.' })
    .max(64, { error: 'O SKU tem no máximo 64 caracteres.' }),
  name: z
    .string()
    .trim()
    .min(1, { error: 'Informe o nome.' })
    .max(120, { error: 'O nome tem no máximo 120 caracteres.' }),
  price: z.string().transform((value, ctx) => {
    const cents = parseMoneyToCents(value)
    if (cents === null || cents <= 0 || cents > MAX_CENTS) {
      ctx.addIssue({
        code: 'custom',
        message: 'Informe um preço maior que zero, com até duas casas decimais (ex.: 79,90).',
      })
      return z.NEVER
    }
    return centsToMoneyString(cents)
  }),
  active: z.boolean(),
  trackStock: z.boolean(),
  stockMin: z.string().transform((value, ctx) => {
    const text = value.trim()
    const min = text === '' ? 0 : Number(text)
    if (!/^\d*$/.test(text) || min > MAX_STOCK) {
      ctx.addIssue({ code: 'custom', message: 'Informe um número inteiro de unidades, zero ou mais.' })
      return z.NEVER
    }
    return min
  }),
})

export type ProductFormValues = z.input<typeof productSchema>
export type ProductPayload = z.output<typeof productSchema>

/** Receipt: units received. Adjustment: signed difference, always with a reason. */
export const stockMovementSchema = z
  .object({
    kind: z.enum(['RECEIPT', 'ADJUSTMENT']),
    quantity: z.string(),
    reason: z.string().trim().max(200, { error: 'O motivo tem no máximo 200 caracteres.' }),
  })
  .transform((value, ctx) => {
    const text = value.quantity.trim()
    const quantity = Number(text)
    const valid =
      /^-?\d+$/.test(text) &&
      Math.abs(quantity) <= MAX_STOCK &&
      (value.kind === 'RECEIPT' ? quantity > 0 : quantity !== 0)
    if (!valid) {
      ctx.addIssue({
        code: 'custom',
        path: ['quantity'],
        message:
          value.kind === 'RECEIPT'
            ? 'Informe quantas unidades chegaram (maior que zero).'
            : 'Informe a diferença com sinal, por exemplo -3 ou 5.',
      })
    }
    const missingReason = value.kind === 'ADJUSTMENT' && !value.reason
    if (missingReason) {
      ctx.addIssue({ code: 'custom', path: ['reason'], message: 'Informe o motivo do ajuste.' })
    }
    if (!valid || missingReason) {
      return z.NEVER
    }
    return { kind: value.kind, quantity, ...(value.reason ? { reason: value.reason } : {}) }
  })

export type StockMovementFormValues = z.input<typeof stockMovementSchema>
export type StockMovementPayload = z.output<typeof stockMovementSchema>
