import { useEffect, useState } from 'react'
import { ApiError, retryWhileWaking } from '../../api/client'
import { systemApi } from '../../api/endpoints'

export type WarmupPhase = 'checking' | 'waking' | 'ready' | 'unavailable'

/**
 * Pings the API as soon as the login opens, so a hibernated Render Free instance starts while
 * the user types. A hibernated API holds the request instead of failing, so a slow answer also
 * counts as waking.
 */
export function useApiWarmup({ slowAfterMs = 2_000, timeoutMs = 90_000 } = {}): WarmupPhase {
  const [phase, setPhase] = useState<WarmupPhase>('checking')

  useEffect(() => {
    let active = true
    const update = (next: WarmupPhase) => {
      if (active) {
        setPhase(next)
      }
    }
    const slow = setTimeout(() => {
      if (active) {
        setPhase((current) => (current === 'checking' ? 'waking' : current))
      }
    }, slowAfterMs)

    retryWhileWaking(() => systemApi.health(), { timeoutMs, onWaking: () => update('waking') })
      .then(() => update('ready'))
      // Any other failure is not about starting up: the login itself will report it.
      .catch((error: unknown) => update(error instanceof ApiError && error.isWaking ? 'unavailable' : 'ready'))
      .finally(() => clearTimeout(slow))

    return () => {
      active = false
      clearTimeout(slow)
    }
  }, [slowAfterMs, timeoutMs])

  return phase
}
