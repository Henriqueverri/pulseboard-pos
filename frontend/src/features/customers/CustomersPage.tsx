import { PencilSimpleIcon, PlusIcon, UsersIcon } from '@phosphor-icons/react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { customersApi } from '../../api/endpoints'
import type { Customer } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { ErrorAlert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Dialog } from '../../components/ui/Dialog'
import { SearchInput } from '../../components/ui/Field'
import { EmptyState, SkeletonRows } from '../../components/ui/States'
import { Pagination, Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { CustomerForm } from './CustomerForm'

type Editing = { mode: 'create' } | { mode: 'edit'; customer: Customer } | null

export function CustomersPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Editing>(null)
  const q = useDebouncedValue(search.trim(), 300)

  const customers = useQuery({
    queryKey: ['customers', 'list', { q, page }],
    queryFn: () => customersApi.list({ q, page, size: 20 }),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader
        title="Clientes"
        description="Quem compra na loja. Nome e e-mail seguem para o PulseBoard junto com cada venda."
        actions={
          <Button
            leading={<PlusIcon size={16} weight="bold" aria-hidden="true" />}
            onClick={() => setEditing({ mode: 'create' })}
          >
            Novo cliente
          </Button>
        }
      />

      <Card padding="none">
        <div className="border-b border-ink/[0.07] p-4">
          <SearchInput
            className="max-w-md"
            aria-label="Buscar clientes"
            placeholder="Buscar por nome ou e-mail"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </div>

        {customers.error && (
          <div className="p-4">
            <ErrorAlert error={customers.error} />
          </div>
        )}
        {customers.isPending && <SkeletonRows />}
        {customers.data?.content.length === 0 && (
          <EmptyState icon={UsersIcon} title="Nenhum cliente encontrado." />
        )}
        {customers.data && customers.data.content.length > 0 && (
          <>
            <Table caption="Clientes">
              <THead>
                <Th>Nome</Th>
                <Th>E-mail</Th>
                <Th>Documento</Th>
                <Th>
                  <span className="sr-only">Ações</span>
                </Th>
              </THead>
              <TBody>
                {customers.data.content.map((customer) => (
                  <Tr key={customer.id}>
                    <Td>
                      <span className="flex items-center gap-3 font-medium">
                        <Avatar name={customer.name} />
                        {customer.name}
                      </span>
                    </Td>
                    <Td className="text-ink/65">{customer.email}</Td>
                    <Td className="text-ink/65 tabular">{customer.document ?? '—'}</Td>
                    <Td className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        leading={<PencilSimpleIcon size={14} aria-hidden="true" />}
                        onClick={() => setEditing({ mode: 'edit', customer })}
                      >
                        Editar
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
            <Pagination
              page={customers.data.page}
              totalPages={customers.data.totalPages}
              totalElements={customers.data.totalElements}
              onChange={setPage}
            />
          </>
        )}
      </Card>

      <Dialog
        open={editing !== null}
        title={editing?.mode === 'edit' ? 'Editar cliente' : 'Novo cliente'}
        onClose={() => setEditing(null)}
      >
        {editing && (
          <CustomerForm
            customer={editing.mode === 'edit' ? editing.customer : undefined}
            onSaved={() => setEditing(null)}
            onCancel={() => setEditing(null)}
          />
        )}
      </Dialog>
    </>
  )
}
