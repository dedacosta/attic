import { useState, type FormEvent } from 'react'
import { api, ApiError, apiErrorMessage } from '../api/api'
import { MIN_PASSWORD_LENGTH } from '../lib/passwords'
import { useI18n, type Messages } from '../i18n'
import { HouseIcon } from './icons'
import LanguageSwitch from './LanguageSwitch'

interface Props {
  /** No account exists yet: create the first one instead of signing in */
  setup: boolean
  /** The token of an invitation link: create an account with it instead of signing in */
  invitation: string | null
  onSignedIn: () => void
}

/** Full-page sign-in, or account creation on the very first start or with an invitation. */
export default function SignInScreen({ setup, invitation, onSignedIn }: Props) {
  const { t } = useI18n()
  const [registering, setRegistering] = useState(invitation !== null)
  const newAccount = setup || registering
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [remember, setRemember] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!username.trim()) {
      return setError(t.errorUsername)
    }
    if (newAccount && password.length < MIN_PASSWORD_LENGTH) {
      return setError(t.errorPasswordShort(MIN_PASSWORD_LENGTH))
    }
    if (newAccount && password !== repeat) {
      return setError(t.errorPasswordsDiffer)
    }
    setBusy(true)
    setError(null)
    try {
      if (setup) {
        await api.setup(username.trim(), password)
      } else if (registering && invitation !== null) {
        await api.register(invitation, username.trim(), password)
        // Should signing in fail now, the account exists anyway: offer the normal sign-in
        setRegistering(false)
      }
      if (await api.signIn(username.trim(), password, remember)) {
        onSignedIn()
        return
      }
      setError(t.errorLogin)
    } catch (e) {
      setError(registrationError(e, t) ?? apiErrorMessage(e, t))
    }
    setBusy(false)
  }

  return (
    <div className="sign-in-page">
      <div className="sign-in-language">
        <LanguageSwitch />
      </div>
      <form className="sign-in-card" onSubmit={submit} noValidate>
        <div className="brand sign-in-brand">
          <HouseIcon width={32} height={32} />
          <span>Attic</span>
        </div>
        <h1>{setup ? t.setupTitle : registering ? t.registerTitle : t.signInTitle}</h1>
        {setup && <p className="hint">{t.setupIntro}</p>}
        {!setup && registering && <p className="hint">{t.registerIntro}</p>}
        <label className="field">
          <span>{t.username}</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username"
            autoCapitalize="none" autoCorrect="off" autoFocus required />
        </label>
        <label className="field">
          <span>{t.password}</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete={newAccount ? 'new-password' : 'current-password'} required />
        </label>
        {newAccount && (
          <label className="field">
            <span>{t.repeatPassword}</span>
            <input type="password" value={repeat} onChange={(e) => setRepeat(e.target.value)}
              autoComplete="new-password" required />
          </label>
        )}
        <label className="checkbox">
          <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
          <span>{t.staySignedIn}</span>
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button type="submit" className="button button-primary sign-in-submit" disabled={busy}>
          {busy ? t.signingIn : newAccount ? t.createAccount : t.signIn}
        </button>
        {!setup && registering && (
          <button type="button" className="link sign-in-switch" onClick={() => { setRegistering(false); setError(null) }}>
            {t.haveAccount}
          </button>
        )}
      </form>
    </div>
  )
}

/** Refusals of an invitation, in the user's language */
function registrationError(error: unknown, t: Messages): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  if (error.status === 409) {
    return t.errorUsernameTaken
  }
  return error.detail === 'invitation is not valid' ? t.errorInvitationInvalid : null
}
