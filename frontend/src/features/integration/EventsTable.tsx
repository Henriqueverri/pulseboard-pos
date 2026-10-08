import { Link } from 'react-router'
import type { IntegrationEvent } from '../../api/types'
import { IntegrationStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Table, Td, Th } from '../../components/ui/Table'
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
    <Table>
      <thead className="bg-slate-50">
        <tr>
          <Th>Evento</Th>
          <Th>Pedido</Th>
          <Th>Tipo</Th>
          <Th>Status</Th>
          <Th className="text-right">Tentativas</Th>
          <Th>Última tentativa</Th>
          <Th>Último erro</Th>
          <Th>Próxima tentativa</Th>
          <Th className="text-right">Ações</Th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {events.map((event) => {
          const error = lastErrorText(event)
          return (
            <tr key={event.id}>
              <Td className="tabular-nums">#{event.sequence}</Td>
              <Td>
                <Link
                  className="font-medium text-indigo-700 hover:underline"
                  to={`/orders/${event.orderId}`}
                >
                  #{event.orderNumber ?? '—'}
                </Link>
              </Td>
              <Td>{EVENT_TYPE_LABELS[event.eventType]}</Td>
              <Td>
                <IntegrationStatusBadge status={event.status} failureKind={event.failureKind} />
              </Td>
              <Td className="text-right tabular-nums">{event.attempts}</Td>
              <Td>{formatDateTime(event.lastAttemptAt)}</Td>
              <Td className="max-w-xs truncate" title={error ?? undefined}>
                {error ?? '—'}
              </Td>
              <Td>{formatDateTime(event.nextAttemptAt)}</Td>
              <Td className="text-right whitespace-nowrap">
                <Button variant="ghost" onClick={() => onSelect(event.id)}>
                  Detalhes
                </Button>
                {event.status === 'FAILED' && (
                  <Button
                    variant="secondary"
                    disabled={retrying === event.id}
                    onClick={() => onRetry(event.id)}
                    aria-label={`Reprocessar evento #${event.sequence}`}
                  >
                    Reprocessar
                  </Button>
                )}
              </Td>
            </tr>
          )
        })}
        {events.length === 0 && (
          <tr>
            <Td colSpan={9} className="py-6 text-center text-slate-500">
              Nenhum evento encontrado.
            </Td>
          </tr>
        )}
      </tbody>
    </Table>
  )
}
