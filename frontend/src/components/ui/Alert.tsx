import { CheckCircleIcon, InfoIcon, WarningCircleIcon, WarningIcon } from '@phosphor-icons/react'
import type { ReactNode } from 'react'
import { ApiError } from '../../api/client'
import { cn } from '../../lib/cn'

type Tone = 'info' | 'success' | 'warning' | 'danger'

const tones: Record<Tone, string> = {
  info: 'bg-info-soft text-info-strong',
  success: 'bg-success-soft text-success-strong',
  warning: 'bg-warning-soft text-warning-strong',
  danger: 'bg-danger-soft text-danger-strong',
}

const icons = {
  info: InfoIcon,
  success: CheckCircleIcon,
  warning: WarningIcon,
  danger: WarningCircleIcon,
}

export function Alert({
  tone = 'danger',
  title,
  children,
}: {
  tone?: Tone
  title?: ReactNode
  children?: ReactNode
}) {
  const Icon = icons[tone]
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-lg px-3.5 py-3 text-sm', tones[tone])}
    >
      <Icon size={18} weight="fill" className="mt-px shrink-0" aria-hidden="true" />
      <div className="min-w-0">
        {title && <p className="font-medium">{title}</p>}
        {children && <div className={title ? 'mt-0.5 opacity-90' : ''}>{children}</div>}
      </div>
    </div>
  )
}

/** Shows the ProblemDetail message of a failed request. */
export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) {
    return null
  }
  const message =
    error instanceof ApiError
      ? error.isForbidden
        ? `Acesso negado. ${error.message}`
        : error.message
      : 'Erro inesperado.'
  return <Alert>{message}</Alert>
}
