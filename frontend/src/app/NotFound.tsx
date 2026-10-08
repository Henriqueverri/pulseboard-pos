import { Link } from 'react-router'

export function NotFound() {
  return (
    <section className="space-y-2">
      <h1 className="text-2xl font-semibold">Página não encontrada</h1>
      <Link to="/" className="text-indigo-700 hover:underline">
        Voltar ao caixa
      </Link>
    </section>
  )
}
