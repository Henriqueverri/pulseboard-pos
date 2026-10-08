import { LockKeyIcon } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { buttonClasses } from '../components/ui/styles'

export function AccessDenied() {
  return (
    <section className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
      <span className="flex size-12 items-center justify-center rounded-xl bg-ink/[0.04] text-ink/65" aria-hidden="true">
        <LockKeyIcon size={24} />
      </span>
      <h1 className="text-xl font-semibold tracking-tight">Acesso negado</h1>
      <p className="text-sm text-ink/65">Seu papel não tem permissão para acessar esta tela.</p>
      <Link to="/" className={buttonClasses({ variant: 'outline' })}>
        Voltar ao caixa
      </Link>
    </section>
  )
}
