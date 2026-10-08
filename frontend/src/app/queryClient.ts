import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'

/** Retries only failures that may go away by themselves: network errors and 5xx. Never 4xx. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) {
    return false
  }
  return error instanceof ApiError && (error.isNetworkError || error.status >= 500)
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false, staleTime: 10_000 },
      mutations: { retry: false },
    },
  })
}
