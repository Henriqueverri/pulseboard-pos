import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { productsApi } from '../../api/endpoints'
import type { Product } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { applyServerErrors } from '../../lib/formErrors'
import { productSchema, type ProductPayload } from './schema'

export function ProductForm({
  product,
  onSaved,
  onCancel,
}: {
  product?: Product
  onSaved: (product: Product) => void
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
    resolver: zodResolver(productSchema),
    defaultValues: {
      sku: product?.sku ?? '',
      name: product?.name ?? '',
      price: product?.price.replace('.', ',') ?? '',
      active: product?.active ?? true,
    },
  })

  const save = useMutation({
    mutationFn: (payload: ProductPayload) =>
      product ? productsApi.update(product.id, payload) : productsApi.create(payload),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      onSaved(saved)
    },
    onError: (error) => {
      const mapped = applyServerErrors(error, setError, ['sku', 'name', 'price', 'active'], {
        duplicate_sku: 'sku',
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
      <TextField label="SKU" autoComplete="off" error={errors.sku?.message} {...register('sku')} />
      <TextField label="Nome" autoComplete="off" error={errors.name?.message} {...register('name')} />
      <TextField
        label="Preço (R$)"
        inputMode="decimal"
        placeholder="79,90"
        error={errors.price?.message}
        {...register('price')}
      />
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="h-4 w-4" {...register('active')} />
        Ativo (pode ser vendido)
      </label>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : 'Salvar produto'}
        </Button>
      </div>
    </form>
  )
}
