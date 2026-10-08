import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { productsApi } from '../../api/endpoints'
import type { Product } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { Pagination, Table, Td, Th } from '../../components/ui/Table'
import { formatMoney } from '../../lib/money'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { ProductForm } from './ProductForm'

type Editing = { mode: 'create' } | { mode: 'edit'; product: Product } | null
type ActiveFilter = 'all' | 'active' | 'inactive'

export function ProductsPage() {
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('all')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Editing>(null)
  const q = useDebouncedValue(search.trim(), 300)
  const active = activeFilter === 'all' ? undefined : activeFilter === 'active'

  const products = useQuery({
    queryKey: ['products', 'list', { q, active, page }],
    queryFn: () => productsApi.list({ q, active, page, size: 20 }),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Produtos</h1>
        <Button onClick={() => setEditing({ mode: 'create' })}>Novo produto</Button>
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          type="search"
          aria-label="Buscar produtos"
          placeholder="Buscar por SKU ou nome"
          className="w-full max-w-md rounded-md border border-slate-300 px-3 py-2 text-sm"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPage(0)
          }}
        />
        <select
          aria-label="Situação"
          className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          value={activeFilter}
          onChange={(event) => {
            setActiveFilter(event.target.value as ActiveFilter)
            setPage(0)
          }}
        >
          <option value="all">Todos</option>
          <option value="active">Ativos</option>
          <option value="inactive">Inativos</option>
        </select>
      </div>

      <ErrorAlert error={products.error} />

      {products.data && (
        <>
          <Table>
            <thead className="bg-slate-50">
              <tr>
                <Th>SKU</Th>
                <Th>Nome</Th>
                <Th className="text-right">Preço</Th>
                <Th>Situação</Th>
                <Th>
                  <span className="sr-only">Ações</span>
                </Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {products.data.content.map((product) => (
                <tr key={product.id}>
                  <Td className="font-mono text-xs">{product.sku}</Td>
                  <Td className="font-medium">{product.name}</Td>
                  <Td className="text-right tabular-nums">{formatMoney(product.price)}</Td>
                  <Td>
                    {product.active ? (
                      <Badge tone="green">Ativo</Badge>
                    ) : (
                      <Badge>Inativo</Badge>
                    )}
                  </Td>
                  <Td className="text-right">
                    <Button variant="ghost" onClick={() => setEditing({ mode: 'edit', product })}>
                      Editar
                    </Button>
                  </Td>
                </tr>
              ))}
              {products.data.content.length === 0 && (
                <tr>
                  <Td colSpan={5} className="py-6 text-center text-slate-500">
                    Nenhum produto encontrado.
                  </Td>
                </tr>
              )}
            </tbody>
          </Table>
          <Pagination
            page={products.data.page}
            totalPages={products.data.totalPages}
            onChange={setPage}
          />
        </>
      )}
      {products.isPending && <p className="text-sm text-slate-500">Carregando…</p>}

      <Dialog
        open={editing !== null}
        title={editing?.mode === 'edit' ? 'Editar produto' : 'Novo produto'}
        onClose={() => setEditing(null)}
      >
        {editing && (
          <ProductForm
            product={editing.mode === 'edit' ? editing.product : undefined}
            onSaved={() => setEditing(null)}
            onCancel={() => setEditing(null)}
          />
        )}
      </Dialog>
    </section>
  )
}
