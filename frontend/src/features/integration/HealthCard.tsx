import type { ReactNode } from 'react'
import type { IntegrationHealth } from '../../api/types'
import { Tag } from '../../components/ui/Badge'
import { formatDateTime } from '../../lib/dates'

function Stat({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 px-5 py-4">
      <h3 className="text-xs text-ink/65">{label}</h3>
      <div className="mt-1.5 text-sm">{children}</div>
    </div>
  )
}

export function HealthCard({ health }: { health: IntegrationHealth }) {
  const state = !health.enabled ? (
    <Tag dot>Desligada</Tag>
  ) : health.pausedUntil ? (
    <Tag tone="danger" dot>
      Pausada até {formatDateTime(health.pausedUntil)}
    </Tag>
  ) : (
    <Tag tone="success" dot>
      Ligada
    </Tag>
  )

  return (
    <section
      aria-label="Saúde da integração"
      className="grid divide-ink/[0.07] rounded-xl border border-ink/[0.07] bg-surface max-lg:divide-y sm:grid-cols-2 lg:grid-cols-[auto_minmax(0,2fr)_minmax(0,1fr)_auto_auto] lg:divide-x"
    >
      <Stat label="Estado">{state}</Stat>
      <Stat label="Destino">
        <p className="font-mono text-xs break-all">{health.targetUrl}</p>
      </Stat>
      <Stat label="API Key">
        <p className="font-mono text-xs">{health.keyPrefix ? `pb_${health.keyPrefix}_…` : 'não configurada'}</p>
      </Stat>
      <Stat label="Pendentes / falhas">
        <p className="text-lg font-semibold tracking-tight tabular">
          {health.pending} / {health.failed}
        </p>
      </Stat>
      <Stat label="Último envio">
        <p className="whitespace-nowrap tabular">{formatDateTime(health.lastSentAt)}</p>
      </Stat>
    </section>
  )
}
