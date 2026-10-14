import { CaretDownIcon, MagnifyingGlassIcon } from '@phosphor-icons/react'
import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type Ref,
  type SelectHTMLAttributes,
} from 'react'
import { cn } from '../../lib/cn'
import { controlClasses } from './styles'

interface FieldProps {
  label: string
  error?: string
  hint?: ReactNode
  optional?: boolean
}

function FieldShell({
  id,
  label,
  error,
  hint,
  optional,
  children,
}: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="flex items-baseline gap-1 text-sm font-medium text-ink">
        {label}
        {optional && <span className="text-xs font-normal text-ink/65">(opcional)</span>}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-xs text-danger-strong">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-xs text-ink/65">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

function describedBy(id: string, error?: string, hint?: ReactNode) {
  return error ? `${id}-error` : hint ? `${id}-hint` : undefined
}

export function TextField({
  label,
  error,
  hint,
  optional,
  className,
  ...props
}: FieldProps & InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> }) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} optional={optional}>
      <input
        id={id}
        className={cn(controlClasses({ invalid: Boolean(error) }), className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        {...props}
      />
    </FieldShell>
  )
}

export function Select({
  className,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { ref?: Ref<HTMLSelectElement> }) {
  return (
    <div className={cn('relative', className)}>
      <select className={cn(controlClasses(), 'appearance-none pr-9')} {...props}>
        {children}
      </select>
      <CaretDownIcon
        size={14}
        className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-ink/50"
        aria-hidden="true"
      />
    </div>
  )
}

export function SelectField({
  label,
  error,
  hint,
  optional,
  className,
  children,
  ...props
}: FieldProps & SelectHTMLAttributes<HTMLSelectElement> & { ref?: Ref<HTMLSelectElement> }) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} optional={optional}>
      <Select
        id={id}
        className={className}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        {...props}
      >
        {children}
      </Select>
    </FieldShell>
  )
}

/** On/off checkbox shown as a switch, with its label and a short explanation. */
export function SwitchField({
  label,
  description,
  ...props
}: { label: string; description: ReactNode } & Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
    ref?: Ref<HTMLInputElement>
  }) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 rounded-lg border border-ink/10 px-3 py-2.5">
      <span>
        <span className="block text-sm font-medium">{label}</span>
        <span className="block text-xs text-ink/65">{description}</span>
      </span>
      <input type="checkbox" role="switch" className="peer sr-only" {...props} />
      <span
        className="relative h-5 w-9 shrink-0 rounded-full bg-ink/15 transition-colors peer-checked:bg-brand-600 peer-focus-visible:ring-2 peer-focus-visible:ring-brand-500/60 after:absolute after:top-0.5 after:left-0.5 after:size-4 after:rounded-full after:bg-white after:shadow-soft after:transition-transform peer-checked:after:translate-x-4"
        aria-hidden="true"
      />
    </label>
  )
}

/** Search box with the magnifier icon; pass an `aria-label`. */
export function SearchInput({
  className,
  size = 'md',
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> & { size?: 'md' | 'lg' }) {
  return (
    <div className={cn('relative', className)}>
      <MagnifyingGlassIcon
        size={size === 'lg' ? 18 : 16}
        className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink/50"
        aria-hidden="true"
      />
      <input type="search" className={cn(controlClasses({ size }), size === 'lg' ? 'pl-10' : 'pl-9')} {...props} />
    </div>
  )
}

/** Label above a bare control, for filter bars. */
export function FilterField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-ink/65">{label}</span>
      {children}
    </label>
  )
}
