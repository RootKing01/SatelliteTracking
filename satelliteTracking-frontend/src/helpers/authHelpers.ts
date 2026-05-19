import type { Dispatch, SetStateAction } from 'react'

export function clearAuthFields(
  setAuthUsernameOrEmail: Dispatch<SetStateAction<string>>,
  setAuthUsername: Dispatch<SetStateAction<string>>,
  setAuthEmail: Dispatch<SetStateAction<string>>,
  setAuthPassword: Dispatch<SetStateAction<string>>,
) {
  setAuthUsernameOrEmail('')
  setAuthUsername('')
  setAuthEmail('')
  setAuthPassword('')
}

export default clearAuthFields
