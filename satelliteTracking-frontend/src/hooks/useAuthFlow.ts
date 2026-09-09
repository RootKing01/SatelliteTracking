import { useCallback, useEffect, useState } from 'react'
import {
  executeLoginFlow,
  executeLogoutFlow,
  executeRegisterFlow,
  getCurrentUser,
  type AuthUser,
} from '../api/authClient'

type AuthMode = 'login' | 'register'

type UseAuthFlowOptions = {
  onLogoutSuccess?: () => void
}

export function useAuthFlow({ onLogoutSuccess }: UseAuthFlowOptions = {}) {
  const [authChecking, setAuthChecking] = useState(true)
  const [authSubmitting, setAuthSubmitting] = useState(false)
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authUser, setAuthUser] = useState<AuthUser | null>(null)
  const [authUsernameOrEmail, setAuthUsernameOrEmail] = useState('')
  const [authUsername, setAuthUsername] = useState('')
  const [authEmail, setAuthEmail] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [authPasswordConfirm, setAuthPasswordConfirm] = useState('')
  const [authError, setAuthError] = useState('')
  const [authInfo, setAuthInfo] = useState('Accedi con il profilo base oppure registrane uno nuovo.')

  const resetAuthFields = useCallback(() => {
    setAuthUsernameOrEmail('')
    setAuthUsername('')
    setAuthEmail('')
    setAuthPassword('')
    setAuthPasswordConfirm('')
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    void getCurrentUser(controller.signal)
      .then((response) => {
        if (response.authenticated && response.user) {
          setAuthUser(response.user)
          setAuthInfo(`Sessione attiva: ${response.user.username}`)
        } else {
          setAuthUser(null)
        }
      })
      .catch(() => {
        setAuthUser(null)
      })
      .finally(() => {
        setAuthChecking(false)
      })

    return () => controller.abort()
  }, [])

  const submitLogin = useCallback(async () => {
    if (authSubmitting) return

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeLoginFlow({
        usernameOrEmail: authUsernameOrEmail,
        password: authPassword,
      })

      if (!result.user) {
        setAuthUser(null)
        setAuthError(result.error)
        return
      }

      setAuthUser(result.user)
      setAuthInfo(result.info)
      resetAuthFields()
    } finally {
      setAuthSubmitting(false)
    }
  }, [authPassword, authSubmitting, authUsernameOrEmail, resetAuthFields])

  const submitRegister = useCallback(async () => {
    if (authSubmitting) return

    if (authPassword !== authPasswordConfirm) {
      setAuthError('Le password non coincidono.')
      return
    }

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeRegisterFlow({
        username: authUsername,
        email: authEmail,
        password: authPassword,
        passwordConfirm: authPasswordConfirm,
      })

      if (!result.user) {
        setAuthUser(null)
        setAuthError(result.error)
        return
      }

      setAuthUser(result.user)
      setAuthInfo(result.info)
      resetAuthFields()
    } finally {
      setAuthSubmitting(false)
    }
  }, [authEmail, authPassword, authPasswordConfirm, authSubmitting, authUsername, resetAuthFields])

  const handleLogout = useCallback(async () => {
    if (authSubmitting) return

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeLogoutFlow()
      if (result.error) {
        setAuthError(result.error)
        return
      }

      setAuthUser(null)
      resetAuthFields()
      setAuthInfo('Sessione chiusa, esegui un nuovo accesso.')
      onLogoutSuccess?.()
    } finally {
      setAuthSubmitting(false)
    }
  }, [authSubmitting, onLogoutSuccess, resetAuthFields])

  return {
    authChecking,
    authSubmitting,
    authMode,
    authUser,
    authUsernameOrEmail,
    authUsername,
    authEmail,
    authPassword,
    authPasswordConfirm,
    authError,
    authInfo,
    setAuthUser,
    setAuthMode,
    setAuthUsernameOrEmail,
    setAuthUsername,
    setAuthEmail,
    setAuthPassword,
    setAuthPasswordConfirm,
    setAuthError,
    setAuthInfo,
    submitLogin,
    submitRegister,
    handleLogout,
  }
}
