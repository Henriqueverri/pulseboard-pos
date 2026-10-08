import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../../api/client'
import { authApi } from '../../api/endpoints'
import type { Role } from '../../api/types'
import { AuthContext, type AuthContextValue } from './authContext'
import { clearSession, loadSession, saveSession, sessionFromLogin, type Session } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const [expired, setExpired] = useState(false)

  useEffect(() => {
    setUnauthorizedHandler(() => {
      queryClient.clear()
      setSession(null)
      setExpired(true)
    })
    return () => setUnauthorizedHandler(() => {})
  }, [queryClient])

  const login = useCallback(async (email: string, password: string) => {
    const next = sessionFromLogin(await authApi.login(email, password))
    saveSession(next)
    setExpired(false)
    setSession(next)
    return next
  }, [])

  const logout = useCallback(() => {
    clearSession()
    queryClient.clear()
    setExpired(false)
    setSession(null)
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      expired,
      login,
      logout,
      hasRole: (...roles: Role[]) => session !== null && roles.includes(session.user.role),
    }),
    [session, expired, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
