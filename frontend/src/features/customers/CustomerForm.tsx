import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { customersApi } from '../../api/endpoints'
import type { Customer } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { applyServerErrors } from '../../lib/formErrors'
import { customerSchema, type CustomerPayload } from './schema'

export function CustomerForm({
  customer,
  onSaved,
  onCancel,
}: {
  customer?: Customer
  onSaved: (customer: Customer) => void
  onCancel: () => void
}) {
  const queryClient = useQueryClient()
  const [generalError, setGeneralError] = useState<unknown>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      name: customer?.name ?? '',
      email: customer?.email ?? '',
      document: customer?.document ?? '',
    },
  })

  const save = useMutation({
    mutationFn: (payload: CustomerPayload) =>
      customer ? customersApi.update(customer.id, payload) : customersApi.create(payload),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['customers'] })
      onSaved(saved)
    },
    onError: (error) => {
      const mapped = applyServerErrors(error, setError, ['name', 'email', 'document'], {
        duplicate_email: 'email',
      })
      setGeneralError(mapped ? null : error)
    },
  })

  return (
    <form
      noValidate
      className="space-y-4"
      onSubmit={handleSubmit((payload) => {
        setGeneralError(null)
        save.mutate(payload)
      })}
    >
      <ErrorAlert error={generalError} />
      <TextField label="Nome" autoComplete="off" error={errors.name?.message} {...register('name')} />
      <TextField
        label="E-mail"
        type="email"
        autoComplete="off"
        error={errors.email?.message}
        {...register('email')}
      />
      <TextField
        label="Documento (opcional)"
        hint="CPF/CNPJ. Fica só no POS."
        error={errors.document?.message}
        {...register('document')}
      />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : 'Salvar cliente'}
        </Button>
      </div>
    </form>
  )
}
