import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type Ref,
  type SelectHTMLAttributes,
} from 'react'

const controlClass =
  'block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-invalid:border-red-500'

interface FieldProps {
  label: string
  error?: string
  hint?: ReactNode
}

function FieldShell({
  id,
  label,
  error,
  hint,
  children,
}: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-500">{hint}</p>}
      {error && (
        <p id={`${id}-error`} className="text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

export function TextField({
  label,
  error,
  hint,
  ...props
}: FieldProps & InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> }) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <input
        id={id}
        className={controlClass}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...props}
      />
    </FieldShell>
  )
}

export function SelectField({
  label,
  error,
  hint,
  children,
  ...props
}: FieldProps &
  SelectHTMLAttributes<HTMLSelectElement> & { ref?: Ref<HTMLSelectElement> }) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <select
        id={id}
        className={controlClass}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  )
}
