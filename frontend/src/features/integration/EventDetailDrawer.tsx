import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { integrationApi } from '../../api/endpoints'
import { ErrorAlert } from '../../components/ui/Alert'
import { IntegrationStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Drawer } from '../../components/ui/Drawer'
import { formatDateTime } from '../../lib/dates'
import { EVENT_TYPE_LABELS } from '../../lib/labels'
import { POLL_INTERVAL_MS, useRetryEvent } from './events'

/** Everything needed to investigate one delivery, including the exact body sent. */
export function EventDetailDrawer({ eventId, onClose }: { eventId: string | null; onClose: () => void }) {
  const event = useQuery({
    queryKey: ['integration', 'event', eventId],
    queryFn: () => integrationApi.event(eventId as string),
    enabled: eventId !== null,
    refetchInterval: POLL_INTERVAL_MS,
  })
  const retry = useRetryEvent()
  const data = event.data

  return (
    <Drawer
      open={eventId !== null}
      title={data ? `Evento #${data.sequence}` : 'Evento'}
      onClose={onClose}
    >
      <div className="space-y-4 text-sm">
        <ErrorAlert error={event.error ?? retry.error} />
        {event.isPending && <p className="text-slate-500">Carregando…</p>}
        {data && (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <IntegrationStatusBadge status={data.status} failureKind={data.failureKind} />
              <span>{EVENT_TYPE_LABELS[data.eventType]}</span>
              <Link className="text-indigo-700 hover:underline" to={`/orders/${data.orderId}`}>
                Pedido #{data.orderNumber ?? '—'}
              </Link>
            </div>
            <dl className="grid grid-cols-2 gap-3">
              <Field label="ID externo" value={data.externalId} mono />
              <Field label="Tentativas" value={String(data.attempts)} />
              <Field label="Último HTTP status" value={data.lastHttpStatus?.toString() ?? '—'} />
              <Field label="Código do PulseBoard" value={data.lastErrorCode ?? '—'} mono />
              <Field label="X-Request-Id enviado" value={data.requestId} mono />
              <Field label="X-Request-Id da resposta" value={data.lastRequestId ?? '—'} mono />
              <Field label="Última tentativa" value={formatDateTime(data.lastAttemptAt)} />
              <Field label="Próxima tentativa" value={formatDateTime(data.nextAttemptAt)} />
              <Field label="Enviado em" value={formatDateTime(data.processedAt)} />
              <Field label="ID no PulseBoard" value={data.remoteId ?? '—'} mono />
            </dl>
            {data.blockedBy !== null && (
              <p className="rounded-md bg-amber-50 px-3 py-2 text-amber-800">
                Aguardando o evento #{data.blockedBy} do mesmo pedido ser enviado.
              </p>
            )}
            {data.lastError && (
              <div>
                <h3 className="font-medium text-slate-700">Último erro</h3>
                <p className="mt-1 break-words">{data.lastError}</p>
              </div>
            )}
            <div>
              <h3 className="font-medium text-slate-700">Payload enviado</h3>
              <pre
                aria-label="Payload enviado"
                className="mt-1 overflow-x-auto rounded-md bg-slate-900 p-3 font-mono text-xs text-slate-100"
              >
                {JSON.stringify(data.payload, null, 2)}
              </pre>
            </div>
            {data.status === 'FAILED' && (
              <div className="flex justify-end">
                <Button disabled={retry.isPending} onClick={() => retry.mutate(data.id)}>
                  Reprocessar
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </Drawer>
  )
}

function Field({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className={mono ? 'font-mono text-xs break-all' : ''}>{value}</dd>
    </div>
  )
}
