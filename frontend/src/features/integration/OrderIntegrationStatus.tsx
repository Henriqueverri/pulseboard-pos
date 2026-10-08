import { ArrowRightIcon, PlugsConnectedIcon } from '@phosphor-icons/react'
import { Link } from 'react-router'
import type { IntegrationEvent } from '../../api/types'
import { IntegrationStatusBadge } from '../../components/ui/Badge'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/States'
import { Table, TBody, Td, Th, THead, Tr } from '../../components/ui/Table'
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
    <Card
      aria-label="Integração com o PulseBoard"
      title="Integração com o PulseBoard"
      description="Envio da venda e do estorno deste pedido."
      padding="none"
      actions={
        showIntegrationLink &&
        events.length > 0 && (
          <Link
            to="/integration"
            className="inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline"
          >
            Abrir Integração
            <ArrowRightIcon size={14} aria-hidden="true" />
          </Link>
        )
      }
    >
      {events.length === 0 ? (
        <EmptyState
          compact
          icon={PlugsConnectedIcon}
          title="Nada enviado ao PulseBoard"
          description="Só pagamentos e estornos são enviados."
        />
      ) : (
        <Table caption="Envios ao PulseBoard">
          <THead>
            <Th>Envio</Th>
            <Th>Status</Th>
            <Th className="text-right">Tentativas</Th>
            <Th>Último erro</Th>
            <Th>Próxima tentativa</Th>
            <Th>Enviado em</Th>
          </THead>
          <TBody>
            {events.map((event) => (
              <Tr key={event.id}>
                <Td>
                  {EVENT_TYPE_LABELS[event.eventType]}{' '}
                  <span className="text-ink/55 tabular">#{event.sequence}</span>
                </Td>
                <Td>
                  <IntegrationStatusBadge status={event.status} failureKind={event.failureKind} />
                </Td>
                <Td className="text-right tabular">{event.attempts}</Td>
                <Td className="max-w-xs break-words text-ink/80">{lastErrorText(event) ?? '—'}</Td>
                <Td className="whitespace-nowrap text-ink/65 tabular">{formatDateTime(event.nextAttemptAt)}</Td>
                <Td className="whitespace-nowrap text-ink/65 tabular">{formatDateTime(event.processedAt)}</Td>
              </Tr>
            ))}
          </TBody>
        </Table>
      )}
    </Card>
  )
}
