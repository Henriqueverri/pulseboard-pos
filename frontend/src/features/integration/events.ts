import { useMutation, useQueryClient } from '@tanstack/react-query'
import { integrationApi } from '../../api/endpoints'
import type { IntegrationEvent } from '../../api/types'

/** The Integration screen and the order detail refresh at this pace while deliveries are open. */
export const POLL_INTERVAL_MS = 5_000

/** What the "Último erro" column shows: what holds the event back, or PulseBoard's answer. */
export function lastErrorText(event: IntegrationEvent): string | null {
  if (event.blockedBy !== null) {
    return `bloqueado por #${event.blockedBy}`
  }
  if (!event.lastErrorCode) {
    return null
  }
  return event.lastError ? `${event.lastErrorCode}: ${event.lastError}` : event.lastErrorCode
}

export function isOpen(event: IntegrationEvent): boolean {
  return event.status === 'PENDING' || event.status === 'PROCESSING'
}

/** FAILED → PENDING; everything showing integration state is refreshed. */
export function useRetryEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => integrationApi.retry(id),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['integration'] }),
        queryClient.invalidateQueries({ queryKey: ['orders', 'detail'] }),
      ]),
  })
}
