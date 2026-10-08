import { z } from 'zod'

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, { error: 'Informe o e-mail.' })
    .pipe(z.email({ error: 'Informe um e-mail válido.' })),
  password: z.string().min(1, { error: 'Informe a senha.' }),
})

export type LoginValues = z.infer<typeof loginSchema>
