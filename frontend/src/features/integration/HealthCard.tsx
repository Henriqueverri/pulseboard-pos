import type { IntegrationHealth } from '../../api/types'
import { Badge } from '../../components/ui/Badge'
import { formatDateTime } from '../../lib/dates'

export function HealthCard({ health }: { health: IntegrationHealth }) {
  const state = !health.enabled ? (
    <Badge>Desligada</Badge>
  ) : health.pausedUntil ? (
    <Badge tone="red">Pausada até {formatDateTime(health.pausedUntil)}</Badge>
  ) : (
    <Badge tone="green">Ligada</Badge>
  )

  return (
    <section
      aria-label="Saúde da integração"
      className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 text-sm sm:grid-cols-3 lg:grid-cols-6"
    >
      <div>
        <h3 className="text-slate-500">Estado</h3>
        <p className="mt-1">{state}</p>
      </div>
      <div className="sm:col-span-2">
        <h3 className="text-slate-500">Destino</h3>
        <p className="mt-1 font-mono text-xs break-all">{health.targetUrl}</p>
      </div>
      <div>
        <h3 className="text-slate-500">API Key</h3>
        <p className="mt-1 font-mono text-xs">
          {health.keyPrefix ? `pb_${health.keyPrefix}_…` : 'não configurada'}
        </p>
      </div>
      <div>
        <h3 className="text-slate-500">Pendentes / falhas</h3>
        <p className="mt-1 font-medium tabular-nums">
          {health.pending} / {health.failed}
        </p>
      </div>
      <div>
        <h3 className="text-slate-500">Último envio</h3>
        <p className="mt-1">{formatDateTime(health.lastSentAt)}</p>
      </div>
    </section>
  )
}
