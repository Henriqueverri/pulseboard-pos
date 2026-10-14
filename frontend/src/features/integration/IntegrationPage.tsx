import { ArrowClockwiseIcon, PlugsConnectedIcon } from '@phosphor-icons/react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { integrationApi, type IntegrationEventFilters } from '../../api/endpoints'
import type { IntegrationEventStatus } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { FilterField, Select } from '../../components/ui/Field'
import { EmptyState, Skeleton, SkeletonRows } from '../../components/ui/States'
import { Pagination } from '../../components/ui/Table'
import { formatDateTime } from '../../lib/dates'
import { INTEGRATION_STATUS_LABELS } from '../../lib/labels'
import { EventDetailDrawer } from './EventDetailDrawer'
import { EventsTable } from './EventsTable'
import { POLL_INTERVAL_MS, useRetryEvent } from './events'
import { HealthCard } from './HealthCard'

const STATUSES = Object.keys(INTEGRATION_STATUS_LABELS) as IntegrationEventStatus[]

function filtersFrom(params: URLSearchParams): IntegrationEventFilters {
  const status = params.get('status')
  const page = Number(params.get('page'))
  return {
    status: STATUSES.includes(status as IntegrationEventStatus)
      ? (status as IntegrationEventStatus)
      : undefined,
    page: Number.isSafeInteger(page) && page > 0 ? page : 0,
    size: 20,
  }
}

/** The outbox in the open: what was sent, what is waiting and why, and what failed. */
export function IntegrationPage() {
  const [params, setParams] = useSearchParams()
  const filters = filtersFrom(params)
  const [selected, setSelected] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const health = useQuery({
    queryKey: ['integration', 'health'],
    queryFn: integrationApi.health,
    refetchInterval: POLL_INTERVAL_MS,
  })
  const events = useQuery({
    queryKey: ['integration', 'events', filters],
    queryFn: () => integrationApi.events(filters),
    refetchInterval: POLL_INTERVAL_MS,
    placeholderData: keepPreviousData,
  })
  const retry = useRetryEvent()
  const retryConfiguration = useMutation({
    mutationFn: integrationApi.retryConfigurationFailures,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['integration'] }),
  })

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) {
        next.set(key, value)
      } else {
        next.delete(key)
      }
    }
    if (!('page' in changes)) {
      next.delete('page')
    }
    setParams(next, { replace: true })
  }

  return (
    <>
      <PageHeader
        title="Integração com o PulseBoard"
        description="Cada pagamento, estorno e mudança de saldo de estoque vira um evento entregue ao PulseBoard: o que foi enviado, o que está aguardando e por quê, e o que falhou."
      />

      <div className="space-y-6">
        {health.data?.pausedUntil && (
          <Alert title="Integração pausada: API Key inválida, revogada ou expirada.">
            Nenhum evento é enviado até {formatDateTime(health.data.pausedUntil)}. Corrija a chave
            (PULSEBOARD_API_KEY), reinicie a API e use “Reprocessar falhas de configuração”.
          </Alert>
        )}
        <ErrorAlert error={health.error} />
        {health.isPending && <Skeleton className="h-20 w-full rounded-xl" />}
        {health.data && <HealthCard health={health.data} />}

        <Card padding="none">
          <div className="flex flex-wrap items-end justify-between gap-3 border-b border-ink/[0.07] p-4">
            <FilterField label="Status">
              <Select
                className="w-44"
                value={filters.status ?? ''}
                onChange={(event) => update({ status: event.target.value })}
              >
                <option value="">Todos</option>
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {INTEGRATION_STATUS_LABELS[status]}
                  </option>
                ))}
              </Select>
            </FilterField>
            <Button
              variant="outline"
              loading={retryConfiguration.isPending}
              leading={<ArrowClockwiseIcon size={16} aria-hidden="true" />}
              onClick={() => retryConfiguration.mutate()}
            >
              Reprocessar falhas de configuração
            </Button>
          </div>

          {(retryConfiguration.data || events.error || retry.error || retryConfiguration.error) && (
            <div className="space-y-3 border-b border-ink/[0.07] p-4">
              {retryConfiguration.data && (
                <Alert tone="success">
                  {retryConfiguration.data.retried === 1
                    ? '1 evento voltou para a fila.'
                    : `${retryConfiguration.data.retried} eventos voltaram para a fila.`}
                </Alert>
              )}
              <ErrorAlert error={events.error ?? retry.error ?? retryConfiguration.error} />
            </div>
          )}

          {events.isPending && <SkeletonRows />}
          {events.data?.content.length === 0 && (
            <EmptyState
              icon={PlugsConnectedIcon}
              title="Nenhum evento encontrado."
              description="Os eventos aparecem aqui quando um pedido é pago ou estornado."
            />
          )}
          {events.data && events.data.content.length > 0 && (
            <>
              <EventsTable
                events={events.data.content}
                retrying={retry.isPending ? (retry.variables ?? null) : null}
                onSelect={setSelected}
                onRetry={(id) => retry.mutate(id)}
              />
              <Pagination
                page={events.data.page}
                totalPages={events.data.totalPages}
                totalElements={events.data.totalElements}
                onChange={(page) => update({ page: page > 0 ? String(page) : '' })}
              />
            </>
          )}
        </Card>
      </div>

      <EventDetailDrawer eventId={selected} onClose={() => setSelected(null)} />
    </>
  )
}
