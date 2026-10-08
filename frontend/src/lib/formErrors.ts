import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '../api/client'

/**
 * Puts server-side errors on the form fields: ProblemDetail {@code errors} keyed by field, and
 * conflict codes (e.g. {@code duplicate_sku}) on the field they refer to. Returns whether anything
 * was mapped, so the caller can fall back to a general message.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
  codeFields: Partial<Record<string, Path<T>>> = {},
): boolean {
  if (!(error instanceof ApiError)) {
    return false
  }
  let mapped = false
  for (const field of fields) {
    const messages = error.errors[field]
    if (messages?.length) {
      setError(field, { type: 'server', message: messages.join(' ') })
      mapped = true
    }
  }
  const codeField = codeFields[error.code]
  if (codeField) {
    setError(codeField, { type: 'server', message: error.message })
    mapped = true
  }
  return mapped
}
