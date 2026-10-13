import { zodResolver } from '@hookform/resolvers/zod'
import {
  ArrowsClockwiseIcon,
  CashRegisterIcon,
  FingerprintIcon,
  PlugsConnectedIcon,
} from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { retryWhileWaking } from '../../api/client'
import type { LoginRedirectState } from '../../app/RequireAuth'
import { AppLogo } from '../../components/brand/AppLogo'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { useAuth } from './authContext'
import { loginSchema, type LoginValues } from './schema'
import { useApiWarmup } from './useApiWarmup'
import { WakingNotice } from './WakingNotice'

const HIGHLIGHTS = [
  {
    icon: CashRegisterIcon,
    title: 'Venda em poucos cliques',
    text: 'Busque o produto, escolha o cliente e registre o pagamento.',
  },
  {
    icon: PlugsConnectedIcon,
    title: 'Direto para o PulseBoard',
    text: 'Cada venda e cada estorno viram métricas no PulseBoard em segundos.',
  },
  {
    icon: ArrowsClockwiseIcon,
    title: 'Nada se perde',
    text: 'Se o PulseBoard estiver fora do ar, o envio é refeito automaticamente, sem duplicar vendas.',
  },
  {
    icon: FingerprintIcon,
    title: 'Rastreável',
    text: 'Cada envio tem status, tentativas e um request id compartilhado com o PulseBoard.',
  },
]

export function LoginPage() {
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LoginRedirectState
  const target = state.from && state.from !== '/login' ? state.from : '/'
  const [error, setError] = useState<unknown>(null)
  const warmup = useApiWarmup()
  const [loginWaking, setLoginWaking] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  if (session) {
    return <Navigate to={target} replace />
  }

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setError(null)
    try {
      await retryWhileWaking(() => login(email, password), { onWaking: () => setLoginWaking(true) })
      navigate(target, { replace: true })
    } catch (failure) {
      setError(failure)
    } finally {
      setLoginWaking(false)
    }
  })

  const waking = (warmup === 'waking' || loginWaking) && !error

  return (
    <div className="flex min-h-screen bg-surface">
      <aside className="relative hidden w-[44%] max-w-[640px] flex-col justify-between overflow-hidden bg-ink p-10 text-white lg:flex xl:p-14">
        <div
          className="pointer-events-none absolute -top-32 -right-32 size-[420px] rounded-full bg-brand-500/30 blur-[120px]"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -bottom-40 -left-20 size-[360px] rounded-full bg-purple/20 blur-[120px]"
          aria-hidden="true"
        />
        <AppLogo inverted className="relative" />
        <div className="relative max-w-md">
          <p className="text-3xl leading-tight font-semibold tracking-tight xl:text-4xl">
            O caixa que alimenta o seu PulseBoard.
          </p>
          <ul className="mt-10 space-y-6">
            {HIGHLIGHTS.map(({ icon: ItemIcon, title, text }) => (
              <li key={title} className="flex gap-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-white/10">
                  <ItemIcon size={18} aria-hidden="true" />
                </span>
                <span>
                  <span className="block text-sm font-medium">{title}</span>
                  <span className="mt-0.5 block text-sm text-white/60">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-xs text-white/65">© {new Date().getFullYear()} PulseBoard POS</p>
      </aside>

      <main className="flex flex-1 flex-col px-6 py-8 sm:px-10">
        <AppLogo className="lg:hidden" />
        <div className="flex flex-1 items-center justify-center py-10">
          <form noValidate onSubmit={onSubmit} className="w-full max-w-sm space-y-5">
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Entrar no caixa</h1>
              <p className="mt-1 text-sm text-ink/65">Use o e-mail e a senha do seu usuário do POS.</p>
            </div>
            {state.expired && !error && <Alert tone="info">Sua sessão expirou. Entre novamente.</Alert>}
            {waking && <WakingNotice />}
            {warmup === 'unavailable' && !loginWaking && !error && (
              <Alert tone="warning">O servidor não respondeu. Tente entrar novamente em instantes.</Alert>
            )}
            <ErrorAlert error={error} />
            <TextField
              label="E-mail"
              type="email"
              autoComplete="username"
              placeholder="voce@loja.com"
              error={errors.email?.message}
              {...register('email')}
            />
            <TextField
              label="Senha"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password')}
            />
            <Button type="submit" size="lg" block loading={isSubmitting}>
              Entrar
            </Button>
          </form>
        </div>
      </main>
    </div>
  )
}
