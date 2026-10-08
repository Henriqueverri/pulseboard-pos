import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import type { LoginRedirectState } from '../../app/RequireAuth'
import { Alert, ErrorAlert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { useAuth } from './authContext'
import { loginSchema, type LoginValues } from './schema'

export function LoginPage() {
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LoginRedirectState
  const target = state.from && state.from !== '/login' ? state.from : '/'
  const [error, setError] = useState<unknown>(null)
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
      await login(email, password)
      navigate(target, { replace: true })
    } catch (failure) {
      setError(failure)
    }
  })

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <form
        noValidate
        onSubmit={onSubmit}
        className="w-full max-w-sm space-y-4 rounded-lg bg-white p-6 shadow"
      >
        <div>
          <h1 className="text-xl font-semibold">PulseBoard POS</h1>
          <p className="text-sm text-slate-500">Entre para abrir o caixa.</p>
        </div>
        {state.expired && !error && (
          <Alert tone="info">Sua sessão expirou. Entre novamente.</Alert>
        )}
        <ErrorAlert error={error} />
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
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
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Entrando…' : 'Entrar'}
        </Button>
      </form>
    </main>
  )
}
