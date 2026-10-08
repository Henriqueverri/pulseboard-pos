import { CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react'
import type { ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'
import { Button } from './Button'

/** Table body styles of the PulseBoard `DataTable`; place it inside a `Card padding="none"`. */
export function Table({ caption, children }: { caption?: string; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        {caption && <caption className="sr-only">{caption}</caption>}
        {children}
      </table>
    </div>
  )
}

export function THead({ children }: { children: ReactNode }) {
  return (
    <thead>
      <tr className="border-b border-ink/[0.07]">{children}</tr>
    </thead>
  )
}

export function TBody({ children }: { children: ReactNode }) {
  return <tbody>{children}</tbody>
}

export function Tr({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <tr
      className={cn(
        'border-b border-ink/[0.05] transition-colors last:border-0 hover:bg-ink/[0.015]',
        className,
      )}
    >
      {children}
    </tr>
  )
}

export function Th({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      scope="col"
      className={cn(
        'h-10 px-4 text-left text-xs font-medium whitespace-nowrap text-ink/65 first:pl-5 last:pr-5',
        className,
      )}
      {...props}
    />
  )
}

export function Td({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('h-14 px-4 align-middle first:pl-5 last:pr-5', className)} {...props} />
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements?: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav
      aria-label="Paginação"
      className="flex items-center justify-between gap-3 border-t border-ink/[0.07] px-5 py-3 text-sm"
    >
      <span className="text-ink/65 tabular">
        Página {page + 1} de {totalPages}
        {totalElements !== undefined && ` · ${totalElements} registros`}
      </span>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={page <= 0}
          onClick={() => onChange(page - 1)}
          leading={<CaretLeftIcon size={14} aria-hidden="true" />}
        >
          Anterior
        </Button>
        <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Próxima
          <CaretRightIcon size={14} aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
