import { z } from 'zod'
import { MAX_ITEMS, MAX_QUANTITY } from './cartReducer'

export const paymentMethodSchema = z.enum(['CASH', 'CARD', 'PIX'], {
  error: 'Escolha a forma de pagamento.',
})

/** What the checkout sends: ids and quantities only; prices and totals come from the server. */
export const checkoutSchema = z.object({
  customerId: z.uuid({ error: 'Selecione o cliente.' }),
  items: z
    .array(
      z.object({
        productId: z.uuid(),
        quantity: z.number().int().min(1).max(MAX_QUANTITY),
      }),
    )
    .min(1, { error: 'Adicione ao menos um produto.' })
    .max(MAX_ITEMS, { error: `No máximo ${MAX_ITEMS} produtos por venda.` }),
  paymentMethod: paymentMethodSchema,
})

export type CheckoutInput = z.infer<typeof checkoutSchema>
