import '../../styles/auth/auth-panel.css'

type AuthMode = 'login' | 'register'

type AuthPanelProps = {
  authChecking: boolean
  authMode: AuthMode
  authInfo: string
  authError: string
  authSubmitting: boolean
  authUsernameOrEmail: string
  authUsername: string
  authEmail: string
  authPassword: string
  onSwitchMode: (mode: AuthMode) => void
  onAuthUsernameOrEmailChange: (value: string) => void
  onAuthUsernameChange: (value: string) => void
  onAuthEmailChange: (value: string) => void
  onAuthPasswordChange: (value: string) => void
  onSubmit: () => void
}

export function AuthPanel({
  authChecking,
  authMode,
  authInfo,
  authError,
  authSubmitting,
  authUsernameOrEmail,
  authUsername,
  authEmail,
  authPassword,
  onSwitchMode,
  onAuthUsernameOrEmailChange,
  onAuthUsernameChange,
  onAuthEmailChange,
  onAuthPasswordChange,
  onSubmit,
}: AuthPanelProps) {
  if (authChecking) {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <h1>Satellite Tracker</h1>
          <p>Verifica sessione in corso...</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <h1>Satellite Tracker</h1>
        <p className="auth-info">{authInfo}</p>

        <div className="auth-tabs">
          <button
            type="button"
            className={authMode === 'login' ? 'is-active' : ''}
            onClick={() => onSwitchMode('login')}
          >
            Accesso
          </button>
          <button
            type="button"
            className={authMode === 'register' ? 'is-active' : ''}
            onClick={() => onSwitchMode('register')}
          >
            Iscrizione
          </button>
        </div>

        <form
          className="auth-form"
          onSubmit={(event) => {
            event.preventDefault()
            onSubmit()
          }}
        >
          {authMode === 'login' ? (
            <label>
              Username o email
              <input
                value={authUsernameOrEmail}
                onChange={(event) => onAuthUsernameOrEmailChange(event.target.value)}
                autoComplete="username"
                required
              />
            </label>
          ) : (
            <>
              <label>
                Username
                <input
                  value={authUsername}
                  onChange={(event) => onAuthUsernameChange(event.target.value)}
                  autoComplete="username"
                  required
                />
              </label>
              <label>
                Email
                <input
                  type="email"
                  value={authEmail}
                  onChange={(event) => onAuthEmailChange(event.target.value)}
                  autoComplete="email"
                  required
                />
              </label>
            </>
          )}

          <label>
            Password
            <input
              type="password"
              value={authPassword}
              onChange={(event) => onAuthPasswordChange(event.target.value)}
              autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
              required
            />
          </label>

          {authError ? <p className="auth-error">{authError}</p> : null}

          <button type="submit" className="auth-submit" disabled={authSubmitting}>
            {authSubmitting ? 'Invio in corso...' : authMode === 'login' ? 'Accedi' : 'Crea account'}
          </button>
        </form>

        <div className="auth-hint">
          <p>
            Profilo base: <strong>demo</strong> / <strong>Demo123!</strong>
          </p>
        </div>
      </section>
    </main>
  )
}
