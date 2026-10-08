import { ArrowClockwiseIcon } from '@phosphor-icons/react'
import { Link } from 'react-router'
import type { IntegrationEvent } from '../../api/types'
import { IntegrationStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
import { formatDateTime } from '../../lib/dates'
import { EVENT_TYPE_LABELS } from '../../lib/labels'
import { lastErrorText } from './events'

export function EventsTable({
  events,
  retrying,
  onSelect,
  onRetry,
}: {
  events: IntegrationEvent[]
  retrying: string | null
  onSelect: (id: string) => void
  onRetry: (id: string) => void
}) {
  return (
    <Table caption="Eventos de integração">
      <THead>
        <Th>Evento</Th>
        <Th>Pedido</Th>
        <Th>Tipo</Th>
        <Th>Status</Th>
        <Th className="text-right">Tentativas</Th>
        <Th>Última tentativa</Th>
        <Th>Último erro</Th>
        <Th>Próxima tentativa</Th>
        <Th className="text-right">Ações</Th>
      </THead>
      <TBody>
        {events.map((event) => {
          const error = lastErrorText(event)
          return (
            <Tr key={event.id}>
              <Td className="text-ink/65 tabular">#{event.sequence}</Td>
              <Td>
                <Link className="font-medium text-brand-600 hover:underline" to={`/orders/${event.orderId}`}>
                  #{event.orderNumber ?? '—'}
                </Link>
              </Td>
              <Td>{EVENT_TYPE_LABELS[event.eventType]}</Td>
              <Td>
                <IntegrationStatusBadge status={event.status} failureKind={event.failureKind} />
              </Td>
              <Td className="text-right tabular">{event.attempts}</Td>
              <Td className="whitespace-nowrap text-ink/65 tabular">{formatDateTime(event.lastAttemptAt)}</Td>
              <Td className="text-ink/80" title={error ?? undefined}>
                {/* max-width is ignored on auto-layout table cells, so the truncation lives on a block child. */}
                <span className="block max-w-56 truncate">{error ?? '—'}</span>
              </Td>
              <Td className="whitespace-nowrap text-ink/65 tabular">{formatDateTime(event.nextAttemptAt)}</Td>
              <Td className="text-right whitespace-nowrap">
                <span className="inline-flex gap-1">
                  {event.status === 'FAILED' && (
                    <Button
                      variant="outline"
                      size="sm"
                      loading={retrying === event.id}
                      leading={<ArrowClockwiseIcon size={14} aria-hidden="true" />}
                      onClick={() => onRetry(event.id)}
                      aria-label={`Reprocessar evento #${event.sequence}`}
                    >
                      Reprocessar
                    </Button>
                  )}
                  <Button variant="ghost" size="sm" onClick={() => onSelect(event.id)}>
                    Detalhes
                  </Button>
                </span>
              </Td>
            </Tr>
          )
        })}
      </TBody>
    </Table>
  )
}
