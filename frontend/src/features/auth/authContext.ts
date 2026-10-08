import { createContext, useContext } from 'react'
import type { Role } from '../../api/types'
import type { Session } from './session'

export interface AuthContextValue {
  session: Session | null
  /** True after the server rejected the session (401), until the next login. */
  expired: boolean
  login: (email: string, password: string) => Promise<Session>
  logout: () => void
  hasRole: (...roles: Role[]) => boolean
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
