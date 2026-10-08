import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '../api/types'
import { useAuth } from '../features/auth/authContext'
import { AccessDenied } from './AccessDenied'

export interface LoginRedirectState {
  from?: string
  expired?: boolean
}

/** Sends anonymous users to the login and blocks users whose role is not in {@code roles}. */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { session, expired } = useAuth()
  const location = useLocation()

  if (!session) {
    const state: LoginRedirectState = { from: location.pathname + location.search, expired }
    return <Navigate to="/login" replace state={state} />
  }
  if (roles && !roles.includes(session.user.role)) {
    return <AccessDenied />
  }
  return children
}
