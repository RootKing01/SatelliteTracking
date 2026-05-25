
import '../../styles/auth/auth-panel.css'
import logo from '../../assets/logo.svg'

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
  authPasswordConfirm: string
  onSwitchMode: (mode: AuthMode) => void
  onAuthUsernameOrEmailChange: (value: string) => void
  onAuthUsernameChange: (value: string) => void
  onAuthEmailChange: (value: string) => void
  onAuthPasswordChange: (value: string) => void
  onAuthPasswordConfirmChange: (value: string) => void
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
  authPasswordConfirm,
  onSwitchMode,
  onAuthUsernameOrEmailChange,
  onAuthUsernameChange,
  onAuthEmailChange,
  onAuthPasswordChange,
  onAuthPasswordConfirmChange,
  onSubmit,
}: AuthPanelProps) {

  if (authChecking) {
    return (
      <main className="auth-shell">
        <section className="auth-card glass">
          <div className="auth-card-header">
            <h1>Satellite Tracker</h1>
          </div>
          <img src={logo} alt="Satellite Tracker Logo" className="auth-logo" />
          <p className="auth-status">Verifica sessione in corso...</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-shell">
      <div className="auth-ambient auth-ambient-left" aria-hidden="true" />
      <div className="auth-ambient auth-ambient-right" aria-hidden="true" />
      <section className="auth-card glass">
        <div className="auth-card-header">
          <h1>Satellite Tracker</h1>
          <p className="auth-subtitle">Traccia in live satelliti, conferma gli avvistamenti, sii protagonista nei thread della community</p>
        </div>
        <div className="auth-logo-glow-wrap">
          <img src={logo} alt="Satellite Tracker Logo" className="auth-logo auth-logo-glow" />
        </div>
        <div className="auth-separator-glow"></div>
        <p className="auth-info">{authInfo}</p>
        <div className="auth-switcher">
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
            <label className="auth-field">
              <span>Username o email</span>
              <input
                value={authUsernameOrEmail}
                onChange={(event) => onAuthUsernameOrEmailChange(event.target.value)}
                autoComplete="username"
                placeholder="demo@satellitetracker.local"
                required
              />
            </label>
          ) : (
            <div className="auth-register-grid">
              <label className="auth-field">
                <span>Username</span>
                <input
                  value={authUsername}
                  onChange={(event) => onAuthUsernameChange(event.target.value)}
                  autoComplete="username"
                  placeholder="nomeutente"
                  required
                />
              </label>
              <label className="auth-field">
                <span>Email</span>
                <input
                  type="email"
                  value={authEmail}
                  onChange={(event) => onAuthEmailChange(event.target.value)}
                  autoComplete="email"
                  placeholder="nome@dominio.it"
                  required
                />
              </label>
            </div>
          )}

          <label className="auth-field">
            <span>Password</span>
            <input
              type="password"
              value={authPassword}
              onChange={(event) => onAuthPasswordChange(event.target.value)}
              autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
              placeholder={authMode === 'login' ? 'Inserisci la password' : 'Crea una password forte'}
              required
            />
          </label>

          {authMode === 'register' ? (
            <label className="auth-field">
              <span>Conferma password</span>
              <input
                type="password"
                value={authPasswordConfirm}
                onChange={(event) => onAuthPasswordConfirmChange(event.target.value)}
                autoComplete="new-password"
                placeholder="Ripeti la password"
                required
              />
            </label>
          ) : null}

          {authError ? <p className="auth-error highlight-error">{authError}</p> : null}

          <button type="submit" className="auth-submit" disabled={authSubmitting}>
            {authMode === 'login' ? 'Accedi' : 'Registrati'}
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
