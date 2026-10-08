import { TrayIcon, type Icon } from '@phosphor-icons/react'
import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-ink/[0.06]', className)} aria-hidden="true" />
}

/** Placeholder rows while a list loads for the first time. */
export function SkeletonRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="divide-y divide-ink/[0.05]" aria-busy="true">
      <span className="sr-only">Carregando…</span>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="flex h-14 items-center gap-6 px-5">
          <Skeleton className="h-3.5 w-16" />
          <Skeleton className="h-3.5 w-1/3 max-w-[220px]" />
          <Skeleton className="ml-auto h-3.5 w-20" />
        </div>
      ))}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  icon: IconComponent = TrayIcon,
  compact = false,
  children,
}: {
  title: string
  description?: ReactNode
  icon?: Icon
  compact?: boolean
  children?: ReactNode
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center',
        compact ? 'gap-2 px-4 py-8' : 'gap-3 px-6 py-14',
      )}
    >
      <span
        className={cn(
          'flex items-center justify-center rounded-xl bg-ink/[0.04] text-ink/65',
          compact ? 'size-9' : 'size-11',
        )}
        aria-hidden="true"
      >
        <IconComponent size={compact ? 18 : 22} />
      </span>
      <div className="max-w-sm">
        <p className="text-sm font-semibold text-ink">{title}</p>
        {description && <p className="mt-1 text-xs text-ink/65">{description}</p>}
      </div>
      {children && <div className="mt-1 flex flex-wrap justify-center gap-2">{children}</div>}
    </div>
  )
}
