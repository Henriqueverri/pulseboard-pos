import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { retryDelay, shouldRetry } from './queryClient'

const waking = new ApiError(503, 'api_waking', 'O servidor está iniciando.')

describe('shouldRetry', () => {
  it('retries network errors and 5xx twice', () => {
    expect(shouldRetry(0, new ApiError(0, 'network_error', ''))).toBe(true)
    expect(shouldRetry(1, new ApiError(500, 'http_500', ''))).toBe(true)
    expect(shouldRetry(2, new ApiError(500, 'http_500', ''))).toBe(false)
  })

  it('never retries 4xx or unknown errors', () => {
    expect(shouldRetry(0, new ApiError(409, 'concurrent_modification', ''))).toBe(false)
    expect(shouldRetry(0, new Error('boom'))).toBe(false)
  })

  it('keeps retrying for about 90 s while the API is starting', () => {
    expect(shouldRetry(10, waking)).toBe(true)
    expect(shouldRetry(10, new ApiError(502, 'http_502', ''))).toBe(true)
    expect(shouldRetry(22, waking)).toBe(false)
    expect(22 * retryDelay(0, waking)).toBeGreaterThanOrEqual(85_000)
  })
})

describe('retryDelay', () => {
  it('waits a fixed interval while waking and backs off otherwise', () => {
    expect(retryDelay(5, waking)).toBe(4_000)
    expect(retryDelay(0, new ApiError(500, 'http_500', ''))).toBe(1_000)
    expect(retryDelay(1, new ApiError(500, 'http_500', ''))).toBe(2_000)
  })
})
