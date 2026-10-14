import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { productsApi } from '../../api/endpoints'
import type { Product } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SwitchField, TextField } from '../../components/ui/Field'
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
    control,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(productSchema),
    defaultValues: {
      sku: product?.sku ?? '',
      name: product?.name ?? '',
      price: product?.price.replace('.', ',') ?? '',
      active: product?.active ?? true,
      trackStock: product?.trackStock ?? false,
      stockMin: String(product?.stockMin ?? 0),
    },
  })
  const trackStock = useWatch({ control, name: 'trackStock' })

  const save = useMutation({
    mutationFn: (payload: ProductPayload) =>
      product ? productsApi.update(product.id, payload) : productsApi.create(payload),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      onSaved(saved)
    },
    onError: (error) => {
      const mapped = applyServerErrors(
        error,
        setError,
        ['sku', 'name', 'price', 'active', 'trackStock', 'stockMin'],
        { duplicate_sku: 'sku' },
      )
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
      <SwitchField
        label="Ativo"
        description="Só produtos ativos aparecem no caixa."
        {...register('active')}
      />
      <SwitchField
        label="Controlar estoque"
        description="A venda baixa o saldo e não é concluída sem unidades disponíveis."
        {...register('trackStock')}
      />
      {trackStock && (
        <TextField
          label="Estoque mínimo"
          inputMode="numeric"
          hint="Com o saldo neste valor ou abaixo, o produto aparece como estoque baixo."
          error={errors.stockMin?.message}
          {...register('stockMin')}
        />
      )}
      <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" loading={save.isPending}>
          Salvar produto
        </Button>
      </div>
    </form>
  )
}
