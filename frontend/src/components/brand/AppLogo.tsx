import { cn } from '../../lib/cn'

/** PulseBoard mark with the "POS" product tag. */
export function AppLogo({ inverted = false, className }: { inverted?: boolean; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <span
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-lg',
          inverted ? 'bg-white text-ink' : 'bg-ink text-white',
        )}
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 24 24"
          className="size-5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M3 12h4l2.5-6 5 12 2.5-6h4" />
        </svg>
      </span>
      <span className={cn('text-[15px] font-semibold tracking-tight', inverted ? 'text-white' : 'text-ink')}>
        PulseBoard
      </span>
      <span
        className={cn(
          'rounded-md px-1.5 py-0.5 text-[11px] font-semibold tracking-wide',
          inverted ? 'bg-white/15 text-white' : 'bg-brand-50 text-brand-700',
        )}
      >
        POS
      </span>
    </span>
  )
}
