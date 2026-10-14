import { ArrowClockwiseIcon, ArrowSquareOutIcon } from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { integrationApi } from '../../api/endpoints'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { IntegrationStatusBadge, Tag } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Drawer } from '../../components/ui/Dialog'
import { Skeleton } from '../../components/ui/States'
import { cn } from '../../lib/cn'
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
    <Drawer open={eventId !== null} title={data ? `Evento #${data.sequence}` : 'Evento'} onClose={onClose}>
      <div className="space-y-6 p-5 text-sm">
        <ErrorAlert error={event.error ?? retry.error} />
        {event.isPending && (
          <div className="space-y-3" aria-busy="true">
            <span className="sr-only">Carregando…</span>
            <Skeleton className="h-6 w-40" />
            <Skeleton className="h-32 w-full" />
          </div>
        )}
        {data && (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <IntegrationStatusBadge status={data.status} failureKind={data.failureKind} />
              <Tag tone="brand">{EVENT_TYPE_LABELS[data.eventType]}</Tag>
              {data.orderId ? (
                <Link
                  className="ml-auto inline-flex items-center gap-1 text-brand-600 hover:underline"
                  to={`/orders/${data.orderId}`}
                >
                  Pedido #{data.orderNumber ?? '—'}
                  <ArrowSquareOutIcon size={14} aria-hidden="true" />
                </Link>
              ) : (
                <span className="ml-auto text-ink/80">
                  {data.productName ?? 'Produto removido'} · <span className="font-mono">{data.productSku}</span>
                </span>
              )}
            </div>

            {data.blockedBy !== null && (
              <Alert tone="warning">Aguardando o evento #{data.blockedBy} do mesmo pedido ser enviado.</Alert>
            )}
            {data.lastError && <Alert title="Último erro">{data.lastError}</Alert>}

            <Section title="Entrega">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                <Field label="Tentativas" value={String(data.attempts)} />
                <Field label="Último HTTP status" value={data.lastHttpStatus?.toString() ?? '—'} />
                <Field label="Última tentativa" value={formatDateTime(data.lastAttemptAt)} />
                <Field label="Próxima tentativa" value={formatDateTime(data.nextAttemptAt)} />
                <Field label="Enviado em" value={formatDateTime(data.processedAt)} />
                <Field label="Código do PulseBoard" value={data.lastErrorCode ?? '—'} mono />
              </dl>
            </Section>

            <Section title="Rastreamento">
              <dl className="grid gap-y-3">
                <Field label={data.orderId ? 'ID externo' : 'SKU no PulseBoard'} value={data.externalId} mono />
                <Field label="X-Request-Id enviado" value={data.requestId} mono />
                <Field label="X-Request-Id da resposta" value={data.lastRequestId ?? '—'} mono />
                <Field label="ID no PulseBoard" value={data.remoteId ?? '—'} mono />
              </dl>
            </Section>

            <Section title="Payload enviado">
              <pre
                aria-label="Payload enviado"
                className="overflow-x-auto rounded-lg bg-ink p-4 font-mono text-xs leading-relaxed text-white/90"
              >
                {JSON.stringify(data.payload, null, 2)}
              </pre>
            </Section>

            {data.status === 'FAILED' && (
              <Button
                block
                loading={retry.isPending}
                leading={<ArrowClockwiseIcon size={16} aria-hidden="true" />}
                onClick={() => retry.mutate(data.id)}
              >
                Reprocessar
              </Button>
            )}
          </>
        )}
      </div>
    </Drawer>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h3 className="text-xs font-semibold tracking-wide text-ink/55 uppercase">{title}</h3>
      {children}
    </section>
  )
}

function Field({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-ink/65">{label}</dt>
      <dd className={cn('mt-0.5', mono ? 'font-mono text-xs break-all' : 'tabular')}>{value}</dd>
    </div>
  )
}
