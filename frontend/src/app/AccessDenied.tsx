import { Link } from 'react-router'

export function AccessDenied() {
  return (
    <section className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-xl font-semibold">Acesso negado</h1>
      <p className="mt-2 text-slate-600">Seu papel não tem permissão para acessar esta tela.</p>
      <Link to="/" className="mt-6 inline-block text-indigo-700 underline">
        Voltar ao caixa
      </Link>
    </section>
  )
}
