import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { customersApi } from '../../api/endpoints'
import type { Customer } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { Pagination, Table, Td, Th } from '../../components/ui/Table'
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
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Clientes</h1>
        <Button onClick={() => setEditing({ mode: 'create' })}>Novo cliente</Button>
      </div>

      <input
        type="search"
        aria-label="Buscar clientes"
        placeholder="Buscar por nome ou e-mail"
        className="w-full max-w-md rounded-md border border-slate-300 px-3 py-2 text-sm"
        value={search}
        onChange={(event) => {
          setSearch(event.target.value)
          setPage(0)
        }}
      />

      <ErrorAlert error={customers.error} />

      {customers.data && (
        <>
          <Table>
            <thead className="bg-slate-50">
              <tr>
                <Th>Nome</Th>
                <Th>E-mail</Th>
                <Th>Documento</Th>
                <Th>
                  <span className="sr-only">Ações</span>
                </Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {customers.data.content.map((customer) => (
                <tr key={customer.id}>
                  <Td className="font-medium">{customer.name}</Td>
                  <Td>{customer.email}</Td>
                  <Td>{customer.document ?? '—'}</Td>
                  <Td className="text-right">
                    <Button variant="ghost" onClick={() => setEditing({ mode: 'edit', customer })}>
                      Editar
                    </Button>
                  </Td>
                </tr>
              ))}
              {customers.data.content.length === 0 && (
                <tr>
                  <Td colSpan={4} className="py-6 text-center text-slate-500">
                    Nenhum cliente encontrado.
                  </Td>
                </tr>
              )}
            </tbody>
          </Table>
          <Pagination
            page={customers.data.page}
            totalPages={customers.data.totalPages}
            onChange={setPage}
          />
        </>
      )}
      {customers.isPending && <p className="text-sm text-slate-500">Carregando…</p>}

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
    </section>
  )
}
