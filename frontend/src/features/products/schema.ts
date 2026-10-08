import { z } from 'zod'
import { centsToMoneyString, parseMoneyToCents } from '../../lib/money'

const MAX_CENTS = 999_999_999_999

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
})

export type ProductFormValues = z.input<typeof productSchema>
export type ProductPayload = z.output<typeof productSchema>
