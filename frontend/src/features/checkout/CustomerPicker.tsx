import { UserPlusIcon } from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { customersApi } from '../../api/endpoints'
import type { Customer } from '../../api/types'
import { ErrorAlert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { SearchInput } from '../../components/ui/Field'
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
      <div className="flex items-center gap-3 rounded-lg bg-ink/[0.03] px-3 py-2.5">
        <Avatar name={customer.name} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-ink">{customer.name}</p>
          <p className="truncate text-xs text-ink/65">{customer.email}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => onChange(null)}>
          Trocar
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <SearchInput
          className="flex-1"
          aria-label="Buscar cliente"
          placeholder="Nome ou e-mail"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Button
          variant="outline"
          leading={<UserPlusIcon size={16} aria-hidden="true" />}
          onClick={() => setCreating(true)}
        >
          Novo cliente
        </Button>
      </div>
      <ErrorAlert error={customers.error} />
      {customers.data && (
        <ul className="overflow-hidden rounded-lg border border-ink/[0.07] bg-surface shadow-soft">
          {customers.data.content.map((found) => (
            <li key={found.id} className="border-b border-ink/[0.05] last:border-0">
              <button
                type="button"
                className="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-ink/[0.03]"
                onClick={() => {
                  onChange(found)
                  setSearch('')
                }}
              >
                <Avatar name={found.name} size="sm" />
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">{found.name}</span>
                  <span className="block truncate text-xs text-ink/65">{found.email}</span>
                </span>
              </button>
            </li>
          ))}
          {customers.data.content.length === 0 && (
            <li className="px-3 py-3 text-sm text-ink/65">Nenhum cliente encontrado.</li>
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
