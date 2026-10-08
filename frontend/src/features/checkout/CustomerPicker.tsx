import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { customersApi } from '../../api/endpoints'
import type { Customer } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { CustomerForm } from '../customers/CustomerForm'
import type { CartCustomer } from './cartReducer'

export function CustomerPicker({
  customer,
  onChange,
}: {
  customer: CartCustomer | null
  onChange: (customer: Customer | null) => void
}) {
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const q = useDebouncedValue(search.trim(), 250)

  const customers = useQuery({
    queryKey: ['customers', 'list', { q, page: 0, size: 5 }],
    queryFn: () => customersApi.list({ q, page: 0, size: 5 }),
    enabled: customer === null && q.length > 0,
  })

  if (customer) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-md border border-slate-200 bg-white px-3 py-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{customer.name}</p>
          <p className="truncate text-xs text-slate-500">{customer.email}</p>
        </div>
        <Button variant="ghost" onClick={() => onChange(null)}>
          Trocar
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          type="search"
          aria-label="Buscar cliente"
          placeholder="Buscar cliente por nome ou e-mail"
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Button variant="secondary" className="shrink-0" onClick={() => setCreating(true)}>
          Novo cliente
        </Button>
      </div>
      <ErrorAlert error={customers.error} />
      {customers.data && (
        <ul className="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white">
          {customers.data.content.map((found) => (
            <li key={found.id}>
              <button
                type="button"
                className="w-full px-3 py-2 text-left hover:bg-slate-50"
                onClick={() => {
                  onChange(found)
                  setSearch('')
                }}
              >
                <span className="block text-sm font-medium">{found.name}</span>
                <span className="block text-xs text-slate-500">{found.email}</span>
              </button>
            </li>
          ))}
          {customers.data.content.length === 0 && (
            <li className="px-3 py-3 text-sm text-slate-500">Nenhum cliente encontrado.</li>
          )}
        </ul>
      )}

      <Dialog open={creating} title="Novo cliente" onClose={() => setCreating(false)}>
        <CustomerForm
          onSaved={(saved) => {
            setCreating(false)
            onChange(saved)
          }}
          onCancel={() => setCreating(false)}
        />
      </Dialog>
    </div>
  )
}
