import { Link } from 'react-router'
import type { IntegrationEvent } from '../../api/types'
import { IntegrationStatusBadge } from '../../components/ui/Badge'
import { Table, Td, Th } from '../../components/ui/Table'
import { formatDateTime } from '../../lib/dates'
import { EVENT_TYPE_LABELS } from '../../lib/labels'
import { lastErrorText } from './events'

/** How this order's sale and refund reached PulseBoard. */
export function OrderIntegrationStatus({
  events,
  showIntegrationLink,
}: {
  events: IntegrationEvent[]
  showIntegrationLink: boolean
}) {
  return (
    <section aria-labelledby="order-integration" className="space-y-2">
      <div className="flex items-center justify-between gap-3">
        <h2 id="order-integration" className="text-lg font-semibold">
          Integração com o PulseBoard
        </h2>
        {showIntegrationLink && events.length > 0 && (
          <Link to="/integration" className="text-sm text-indigo-700 hover:underline">
            Abrir Integração
          </Link>
        )}
      </div>
      {events.length === 0 ? (
        <p className="text-sm text-slate-500">
          Nada enviado ao PulseBoard: só pagamentos e estornos são enviados.
        </p>
      ) : (
        <Table>
          <thead className="bg-slate-50">
            <tr>
              <Th>Envio</Th>
              <Th>Status</Th>
              <Th className="text-right">Tentativas</Th>
              <Th>Último erro</Th>
              <Th>Próxima tentativa</Th>
              <Th>Enviado em</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {events.map((event) => (
              <tr key={event.id}>
                <Td>
                  {EVENT_TYPE_LABELS[event.eventType]}{' '}
                  <span className="text-slate-500 tabular-nums">#{event.sequence}</span>
                </Td>
                <Td>
                  <IntegrationStatusBadge status={event.status} failureKind={event.failureKind} />
                </Td>
                <Td className="text-right tabular-nums">{event.attempts}</Td>
                <Td className="max-w-xs break-words">{lastErrorText(event) ?? '—'}</Td>
                <Td>{formatDateTime(event.nextAttemptAt)}</Td>
                <Td>{formatDateTime(event.processedAt)}</Td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </section>
  )
}
