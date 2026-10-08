import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '../../lib/cn'

export function Card({
  title,
  description,
  actions,
  footer,
  padding = 'md',
  className,
  children,
  ...props
}: Omit<HTMLAttributes<HTMLElement>, 'title'> & {
  title?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  footer?: ReactNode
  padding?: 'none' | 'sm' | 'md'
}) {
  return (
    <section
      className={cn('flex min-w-0 flex-col rounded-xl border border-ink/[0.07] bg-surface', className)}
      {...props}
    >
      {(title || actions) && (
        <header
          className={cn(
            'flex flex-wrap items-start justify-between gap-x-4 gap-y-2',
            padding === 'sm' ? 'px-4 pt-4' : 'px-5 pt-5',
            padding === 'none' && 'pb-4',
          )}
        >
          <div className="min-w-0">
            {title && <h2 className="text-sm font-semibold text-ink">{title}</h2>}
            {description && <p className="mt-0.5 text-xs text-ink/65">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={cn('min-w-0 flex-1', padding === 'sm' && 'p-4', padding === 'md' && 'p-5')}>
        {children}
      </div>
      {footer && <footer className="border-t border-ink/[0.07] px-5 py-3">{footer}</footer>}
    </section>
  )
}
