import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { productsApi } from '../../api/endpoints'
import type { Product } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { formatMoney } from '../../lib/money'
import { useDebouncedValue } from '../../lib/useDebouncedValue'

/** Only active products can be sold, so inactive ones are never offered. */
export function ProductSearch({ onAdd }: { onAdd: (product: Product) => void }) {
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 250)

  const products = useQuery({
    queryKey: ['products', 'list', { q, active: true, page: 0, size: 8 }],
    queryFn: () => productsApi.list({ q, active: true, page: 0, size: 8 }),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-3">
      <input
        type="search"
        aria-label="Buscar produto"
        placeholder="Buscar produto por SKU ou nome"
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
      />
      <ErrorAlert error={products.error} />
      <ul className="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white">
        {products.data?.content.map((product) => (
          <li key={product.id} className="flex items-center justify-between gap-3 px-3 py-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{product.name}</p>
              <p className="font-mono text-xs text-slate-500">{product.sku}</p>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-sm tabular-nums">{formatMoney(product.price)}</span>
              <Button
                variant="secondary"
                aria-label={`Adicionar ${product.name}`}
                onClick={() => onAdd(product)}
              >
                Adicionar
              </Button>
            </div>
          </li>
        ))}
        {products.data?.content.length === 0 && (
          <li className="px-3 py-4 text-center text-sm text-slate-500">Nenhum produto ativo encontrado.</li>
        )}
        {products.isPending && <li className="px-3 py-4 text-sm text-slate-500">Carregando…</li>}
      </ul>
    </div>
  )
}
