import { z } from 'zod'

export const customerSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, { error: 'Informe o nome.' })
    .max(120, { error: 'O nome tem no máximo 120 caracteres.' }),
  email: z
    .string()
    .trim()
    .min(1, { error: 'Informe o e-mail.' })
    .max(254, { error: 'O e-mail tem no máximo 254 caracteres.' })
    .pipe(z.email({ error: 'Informe um e-mail válido.' })),
  document: z
    .string()
    .trim()
    .max(20, { error: 'O documento tem no máximo 20 caracteres.' })
    .transform((value) => (value === '' ? null : value)),
})

export type CustomerFormValues = z.input<typeof customerSchema>
export type CustomerPayload = z.output<typeof customerSchema>
