import { useEffect, useState } from 'react'
import { getCurrentUser } from '../api/authClient'

/**
 * Hook custom per gestire la verifica della sessione utente community.
 * Restituisce: sessionVerified, communitySessionValid, error, funzione per reset errori.
 */
export function useCommunitySession(authUser: any) {
  const [sessionVerified, setSessionVerified] = useState(false)
  const [communitySessionValid, setCommunitySessionValid] = useState(false)
  const [sessionError, setSessionError] = useState('')

  useEffect(() => {
    if (!authUser) {
      setSessionVerified(true)
      setCommunitySessionValid(false)
      setSessionError('')
      return
    }
    let cancelled = false
    setSessionVerified(false)
    setSessionError('')
    void getCurrentUser()
      .then((response) => {
        if (cancelled) return
        const isValid = Boolean(response.authenticated && response.user)
        setCommunitySessionValid(isValid)
        setSessionError(isValid ? '' : 'Sessione scaduta. Esegui di nuovo l\'accesso.')
      })
      .catch(() => {
        if (cancelled) return
        setCommunitySessionValid(false)
        setSessionError('Sessione non verificabile. Esegui di nuovo l\'accesso.')
      })
      .finally(() => {
        if (!cancelled) setSessionVerified(true)
      })
    return () => { cancelled = true }
  }, [authUser])

  return { sessionVerified, communitySessionValid, sessionError, setSessionError }
}
