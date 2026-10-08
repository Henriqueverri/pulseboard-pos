import { cn } from '../../lib/cn'

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md' | 'lg'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-ink text-white hover:bg-ink/85 active:bg-ink/75',
  secondary: 'bg-ink/[0.05] text-ink hover:bg-ink/[0.09] active:bg-ink/[0.12]',
  outline: 'border border-ink/10 bg-surface text-ink hover:bg-ink/[0.04] active:bg-ink/[0.07]',
  ghost: 'text-ink hover:bg-ink/[0.05] active:bg-ink/[0.08]',
  danger: 'bg-danger-strong text-white hover:bg-danger-strong/90 active:bg-danger-strong/80',
}

const sizes: Record<ButtonSize, string> = {
  sm: 'h-8 gap-1.5 rounded-md px-3 text-xs',
  md: 'h-9 gap-1.5 rounded-lg px-4 text-sm',
  lg: 'h-11 gap-2 rounded-lg px-5 text-sm',
}

const iconSizes: Record<ButtonSize, string> = {
  sm: 'size-8 rounded-md',
  md: 'size-9 rounded-lg',
  lg: 'size-11 rounded-lg',
}

export interface ButtonStyle {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Square button for a single icon; requires an `aria-label`. */
  icon?: boolean
  block?: boolean
}

/** Button look, also for other elements such as router links. */
export function buttonClasses({
  variant = 'primary',
  size = 'md',
  icon = false,
  block = false,
}: ButtonStyle = {}): string {
  return cn(
    'inline-flex shrink-0 select-none items-center justify-center whitespace-nowrap font-medium transition-colors',
    'disabled:pointer-events-none disabled:opacity-50',
    variants[variant],
    icon ? iconSizes[size] : sizes[size],
    block && 'w-full',
  )
}

/** Look of text inputs and selects (PulseBoard `UiInput`). */
export function controlClasses({
  invalid = false,
  size = 'md',
}: { invalid?: boolean; size?: 'sm' | 'md' | 'lg' } = {}): string {
  return cn(
    'w-full min-w-0 rounded-lg border bg-surface px-3 text-sm text-ink transition-colors placeholder:text-ink/40',
    'focus:ring-2 focus:ring-offset-0 focus-visible:ring-offset-0 focus:outline-none',
    'disabled:cursor-not-allowed disabled:bg-ink/[0.03] disabled:text-ink/65',
    invalid
      ? 'border-danger-strong/60 focus:ring-danger/20'
      : 'border-ink/10 hover:border-ink/20 focus:border-brand-500 focus:ring-brand-500/15',
    size === 'sm' ? 'h-8' : size === 'lg' ? 'h-11' : 'h-9',
  )
}
