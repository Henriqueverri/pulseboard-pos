import {
  CashRegisterIcon,
  ListIcon,
  PackageIcon,
  PlugsConnectedIcon,
  ReceiptIcon,
  SignOutIcon,
  UsersIcon,
  type Icon,
} from '@phosphor-icons/react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import type { Role } from '../api/types'
import { AppLogo } from '../components/brand/AppLogo'
import { Avatar } from '../components/ui/Avatar'
import { Button } from '../components/ui/Button'
import { Drawer } from '../components/ui/Dialog'
import { useAuth } from '../features/auth/authContext'
import { cn } from '../lib/cn'
import { ROLE_LABELS } from '../lib/labels'

interface NavItem {
  to: string
  label: string
  icon: Icon
  end?: boolean
}

const NAVIGATION: { label: string; role?: Role; items: NavItem[] }[] = [
  {
    label: 'Operação',
    items: [
      { to: '/', label: 'Caixa', icon: CashRegisterIcon, end: true },
      { to: '/orders', label: 'Pedidos', icon: ReceiptIcon },
      { to: '/customers', label: 'Clientes', icon: UsersIcon },
    ],
  },
  {
    label: 'Administração',
    role: 'ADMIN',
    items: [
      { to: '/products', label: 'Produtos', icon: PackageIcon },
      { to: '/integration', label: 'Integração', icon: PlugsConnectedIcon },
    ],
  },
]

function AppNav({ onNavigate }: { onNavigate?: () => void }) {
  const { hasRole } = useAuth()
  return (
    <nav aria-label="Principal" className="flex flex-col gap-6">
      {NAVIGATION.filter((group) => !group.role || hasRole(group.role)).map((group) => (
        <div key={group.label}>
          <p className="mb-1.5 px-3 text-xs font-medium text-ink/65">{group.label}</p>
          <ul className="flex flex-col gap-0.5">
            {group.items.map(({ to, label, icon: ItemIcon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'flex h-9 items-center gap-3 rounded-lg px-3 text-sm transition-colors',
                      isActive
                        ? 'bg-ink/[0.06] font-medium text-ink'
                        : 'text-ink/65 hover:bg-ink/[0.04] hover:text-ink',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      <ItemIcon
                        size={18}
                        weight={isActive ? 'fill' : 'regular'}
                        className="shrink-0"
                        aria-hidden="true"
                      />
                      {label}
                    </>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}

function UserCard() {
  const { session, logout } = useAuth()
  if (!session) {
    return null
  }
  const { user } = session
  return (
    <div className="flex items-center gap-3">
      <Avatar name={user.name} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-ink">{user.name}</p>
        <p className="truncate text-xs text-ink/65">{ROLE_LABELS[user.role]}</p>
      </div>
      <Button variant="ghost" size="sm" icon aria-label="Sair" title="Sair" onClick={logout}>
        <SignOutIcon size={18} aria-hidden="true" />
      </Button>
    </div>
  )
}

export function AppLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <div className="min-h-screen">
      <a
        href="#main"
        className="sr-only z-50 rounded-lg bg-ink px-3 py-2 text-sm text-white focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Pular para o conteúdo
      </a>

      <aside className="fixed inset-y-0 left-0 z-30 hidden w-sidebar flex-col border-r border-ink/[0.07] bg-surface lg:flex">
        <div className="flex h-16 shrink-0 items-center px-5">
          <NavLink to="/" className="rounded-lg" aria-label="PulseBoard POS: Caixa">
            <AppLogo />
          </NavLink>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-3 py-4">
          {!drawerOpen && <AppNav />}
        </div>
        <div className="shrink-0 border-t border-ink/[0.07] p-3">
          <UserCard />
        </div>
      </aside>

      <div className="flex min-h-screen min-w-0 flex-col lg:pl-sidebar">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-ink/[0.07] bg-canvas/85 px-4 backdrop-blur-md sm:px-6 lg:hidden">
          <Button variant="ghost" icon className="-ml-2" aria-label="Abrir menu" onClick={() => setDrawerOpen(true)}>
            <ListIcon size={20} aria-hidden="true" />
          </Button>
          <AppLogo />
        </header>
        <main
          id="main"
          tabIndex={-1}
          className="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8 lg:py-8"
        >
          <Outlet />
        </main>
      </div>

      <Drawer open={drawerOpen} title="Menu" side="left" hideHeader onClose={() => setDrawerOpen(false)}>
        <div className="flex h-full flex-col">
          <div className="flex h-16 shrink-0 items-center px-5">
            <AppLogo />
          </div>
          <div className="flex-1 px-3 py-2">
            <AppNav onNavigate={() => setDrawerOpen(false)} />
          </div>
          <div className="border-t border-ink/[0.07] p-3">
            <UserCard />
          </div>
        </div>
      </Drawer>
    </div>
  )
}
