import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { Spinner } from './Spinner'
import { buttonClasses, type ButtonStyle } from './styles'

export function Button({
  variant,
  size = 'md',
  icon,
  block,
  loading = false,
  leading,
  className,
  type = 'button',
  disabled,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> &
  ButtonStyle & { loading?: boolean; leading?: ReactNode }) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(buttonClasses({ variant, size, icon, block }), className)}
      {...props}
    >
      {loading ? (
        <span aria-hidden="true" className="flex">
          <Spinner size={size === 'sm' ? 14 : 16} />
        </span>
      ) : (
        leading
      )}
      {children}
    </button>
  )
}
