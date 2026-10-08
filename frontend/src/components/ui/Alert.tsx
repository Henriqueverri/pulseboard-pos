import type { ReactNode } from 'react'
import { ApiError } from '../../api/client'

export function Alert({
  tone = 'error',
  children,
}: {
  tone?: 'error' | 'success' | 'info'
  children: ReactNode
}) {
  const tones = {
    error: 'border-red-200 bg-red-50 text-red-800',
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    info: 'border-sky-200 bg-sky-50 text-sky-800',
  }
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-md border px-3 py-2 text-sm ${tones[tone]}`}>
      {children}
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
