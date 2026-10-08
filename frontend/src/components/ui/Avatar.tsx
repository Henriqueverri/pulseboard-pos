import { cn } from '../../lib/cn'

const palettes = [
  'bg-brand-100 text-brand-700',
  'bg-info-soft text-info-strong',
  'bg-success-soft text-success-strong',
  'bg-warning-soft text-warning-strong',
  'bg-purple/25 text-ink',
]

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : (parts[0] ?? '?').slice(0, 2)
  return letters.toUpperCase()
}

/** Initials over a soft color picked deterministically from the name. */
export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  let hash = 0
  for (const char of name) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full font-semibold select-none',
        palettes[hash % palettes.length],
        size === 'sm' ? 'size-6 text-[10px]' : 'size-8 text-xs',
      )}
      aria-hidden="true"
    >
      {initials(name)}
    </span>
  )
}
