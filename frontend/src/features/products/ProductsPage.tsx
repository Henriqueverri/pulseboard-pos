import { PackageIcon, PencilSimpleIcon, PlusIcon } from '@phosphor-icons/react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { productsApi } from '../../api/endpoints'
import type { Product } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { ErrorAlert } from '../../components/ui/Alert'
import { Tag } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Dialog } from '../../components/ui/Dialog'
import { SearchInput, Select } from '../../components/ui/Field'
import { EmptyState, SkeletonRows } from '../../components/ui/States'
import { Pagination, Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
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
    <>
      <PageHeader
        title="Produtos"
        description="O SKU precisa existir também no PulseBoard para a venda ser aceita."
        actions={
          <Button
            leading={<PlusIcon size={16} weight="bold" aria-hidden="true" />}
            onClick={() => setEditing({ mode: 'create' })}
          >
            Novo produto
          </Button>
        }
      />

      <Card padding="none">
        <div className="flex flex-wrap gap-3 border-b border-ink/[0.07] p-4">
          <SearchInput
            className="w-full max-w-md"
            aria-label="Buscar produtos"
            placeholder="Buscar por SKU ou nome"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
          <Select
            className="w-36"
            aria-label="Situação"
            value={activeFilter}
            onChange={(event) => {
              setActiveFilter(event.target.value as ActiveFilter)
              setPage(0)
            }}
          >
            <option value="all">Todos</option>
            <option value="active">Ativos</option>
            <option value="inactive">Inativos</option>
          </Select>
        </div>

        {products.error && (
          <div className="p-4">
            <ErrorAlert error={products.error} />
          </div>
        )}
        {products.isPending && <SkeletonRows />}
        {products.data?.content.length === 0 && (
          <EmptyState icon={PackageIcon} title="Nenhum produto encontrado." />
        )}
        {products.data && products.data.content.length > 0 && (
          <>
            <Table caption="Produtos">
              <THead>
                <Th>SKU</Th>
                <Th>Nome</Th>
                <Th className="text-right">Preço</Th>
                <Th>Situação</Th>
                <Th>
                  <span className="sr-only">Ações</span>
                </Th>
              </THead>
              <TBody>
                {products.data.content.map((product) => (
                  <Tr key={product.id}>
                    <Td className="font-mono text-xs text-ink/65">{product.sku}</Td>
                    <Td className="font-medium">{product.name}</Td>
                    <Td className="text-right tabular">{formatMoney(product.price)}</Td>
                    <Td>
                      {product.active ? (
                        <Tag tone="success" dot>
                          Ativo
                        </Tag>
                      ) : (
                        <Tag dot>Inativo</Tag>
                      )}
                    </Td>
                    <Td className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        leading={<PencilSimpleIcon size={14} aria-hidden="true" />}
                        onClick={() => setEditing({ mode: 'edit', product })}
                      >
                        Editar
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
            <Pagination
              page={products.data.page}
              totalPages={products.data.totalPages}
              totalElements={products.data.totalElements}
              onChange={setPage}
            />
          </>
        )}
      </Card>

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
    </>
  )
}
