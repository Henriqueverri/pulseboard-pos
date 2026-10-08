import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../features/auth/authContext'
import { ROLE_LABELS } from '../lib/labels'

function navClass({ isActive }: { isActive: boolean }) {
  return `rounded-md px-3 py-2 text-sm font-medium ${
    isActive ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-200'
  }`
}

export function AppLayout() {
  const { session, hasRole, logout } = useAuth()

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex flex-wrap items-center gap-4">
            <span className="font-semibold">PulseBoard POS</span>
            <nav aria-label="Principal" className="flex flex-wrap gap-1">
              <NavLink to="/" end className={navClass}>
                Caixa
              </NavLink>
              <NavLink to="/orders" className={navClass}>
                Pedidos
              </NavLink>
              <NavLink to="/customers" className={navClass}>
                Clientes
              </NavLink>
              {hasRole('ADMIN') && (
                <>
                  <NavLink to="/products" className={navClass}>
                    Produtos
                  </NavLink>
                  <NavLink to="/integration" className={navClass}>
                    Integração
                  </NavLink>
                </>
              )}
            </nav>
          </div>
          {session && (
            <div className="flex items-center gap-3 text-sm">
              <span>
                {session.user.name}{' '}
                <span className="text-slate-500">({ROLE_LABELS[session.user.role]})</span>
              </span>
              <button
                type="button"
                className="rounded-md px-3 py-2 font-medium text-slate-700 hover:bg-slate-200"
                onClick={logout}
              >
                Sair
              </button>
            </div>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
