import { useState, type FormEvent } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { MIN_PASSWORD_LENGTH } from '../lib/passwords'
import { useI18n } from '../i18n'
import { HouseIcon } from './icons'
import LanguageSwitch from './LanguageSwitch'

interface Props {
  /** No account exists yet: create the first one instead of signing in */
  setup: boolean
  onSignedIn: () => void
}

/** Full-page sign-in, or account creation on the very first start. */
export default function SignInScreen({ setup, onSignedIn }: Props) {
  const { t } = useI18n()
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
    if (setup && password.length < MIN_PASSWORD_LENGTH) {
      return setError(t.errorPasswordShort(MIN_PASSWORD_LENGTH))
    }
    if (setup && password !== repeat) {
      return setError(t.errorPasswordsDiffer)
    }
    setBusy(true)
    setError(null)
    try {
      if (setup) {
        await api.setup(username.trim(), password)
      }
      if (await api.signIn(username.trim(), password, remember)) {
        onSignedIn()
        return
      }
      setError(t.errorLogin)
    } catch (e) {
      setError(apiErrorMessage(e, t))
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
        <h1>{setup ? t.setupTitle : t.signInTitle}</h1>
        {setup && <p className="hint">{t.setupIntro}</p>}
        <label className="field">
          <span>{t.username}</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username"
            autoCapitalize="none" autoCorrect="off" autoFocus required />
        </label>
        <label className="field">
          <span>{t.password}</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete={setup ? 'new-password' : 'current-password'} required />
        </label>
        {setup && (
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
          {busy ? t.signingIn : setup ? t.createAccount : t.signIn}
        </button>
      </form>
    </div>
  )
}
