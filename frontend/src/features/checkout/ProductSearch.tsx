import { PackageIcon, PlusIcon } from '@phosphor-icons/react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { productsApi } from '../../api/endpoints'
import type { Product } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { SearchInput } from '../../components/ui/Field'
import { EmptyState, SkeletonRows } from '../../components/ui/States'
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
    <Card padding="none">
      <div className="space-y-3 border-b border-ink/[0.07] p-4">
        <SearchInput
          size="lg"
          aria-label="Buscar produto"
          placeholder="Buscar produto por SKU ou nome"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <ErrorAlert error={products.error} />
      </div>
      {products.isPending && <SkeletonRows rows={6} />}
      {products.data?.content.length === 0 && (
        <EmptyState
          compact
          icon={PackageIcon}
          title="Nenhum produto ativo encontrado."
          description="Confira o SKU ou busque pelo nome."
        />
      )}
      {products.data && products.data.content.length > 0 && (
        <ul className="divide-y divide-ink/[0.05]">
          {products.data.content.map((product) => (
            <li key={product.id} className="flex items-center gap-4 px-4 py-3 sm:px-5">
              <span
                className="hidden size-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 sm:flex"
                aria-hidden="true"
              >
                <PackageIcon size={20} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-ink">{product.name}</p>
                <p className="font-mono text-xs text-ink/55">{product.sku}</p>
              </div>
              <span className="text-sm font-semibold tabular">{formatMoney(product.price)}</span>
              <Button
                variant="outline"
                size="sm"
                aria-label={`Adicionar ${product.name}`}
                leading={<PlusIcon size={14} weight="bold" aria-hidden="true" />}
                onClick={() => onAdd(product)}
              >
                Adicionar
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
