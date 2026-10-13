import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'

const WAKING_RETRY_DELAY_MS = 4_000
/** About 90 s: how long a hibernated Render Free API may take to start. */
const MAX_WAKING_RETRIES = 22

/**
 * Retries only failures that may go away by themselves: network errors and 5xx. Never 4xx.
 * While the API is starting, keeps trying for about 90 s instead of giving up after two.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return false
  }
  if (error.isWaking) {
    return failureCount < MAX_WAKING_RETRIES
  }
  return failureCount < 2 && (error.isNetworkError || error.status >= 500)
}

export function retryDelay(failureCount: number, error: unknown): number {
  if (error instanceof ApiError && error.isWaking) {
    return WAKING_RETRY_DELAY_MS
  }
  return Math.min(1_000 * 2 ** failureCount, 30_000)
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, retryDelay, refetchOnWindowFocus: false, staleTime: 10_000 },
      mutations: { retry: false },
    },
  })
}
