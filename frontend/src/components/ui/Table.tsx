import type { ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react'

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="min-w-full divide-y divide-slate-200 text-sm">{children}</table>
    </div>
  )
}

export function Th({ className = '', ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      scope="col"
      className={`px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 ${className}`}
      {...props}
    />
  )
}

export function Td({ className = '', ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={`px-3 py-2 align-middle ${className}`} {...props} />
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav aria-label="Paginação" className="flex items-center justify-end gap-3 text-sm">
      <button
        type="button"
        className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        Anterior
      </button>
      <span>
        Página {page + 1} de {totalPages}
      </span>
      <button
        type="button"
        className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Próxima
      </button>
    </nav>
  )
}
