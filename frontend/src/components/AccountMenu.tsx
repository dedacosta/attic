import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError, apiErrorMessage } from '../api/api'
import type { OwnAccount } from '../api/types'
import { MIN_PASSWORD_LENGTH } from '../lib/passwords'
import { usePermissions } from '../lib/permissions'
import { useModal } from '../lib/useModal'
import { useI18n } from '../i18n'
import ContactDialog from './ContactDialog'
import { CloseIcon, UserIcon } from './icons'

interface Props {
  username: string
  onSignedOut: () => void
}

export default function AccountMenu({ username, onSignedOut }: Props) {
  const { t } = useI18n()
  const menu = useRef<HTMLDetailsElement>(null)
  const { isAdmin, isSuperAdmin } = usePermissions()
  const [changingPassword, setChangingPassword] = useState(false)
  const [account, setAccount] = useState<OwnAccount | null>(null)

  // Close the menu when tapping anywhere else, or with Escape
  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (menu.current && !menu.current.contains(event.target as Node)) {
        menu.current.open = false
      }
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && menu.current?.open) {
        menu.current.open = false
        menu.current.querySelector('summary')?.focus()
      }
    }
    document.addEventListener('click', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('click', close)
      document.removeEventListener('keydown', escape)
    }
  }, [])

  async function signOut() {
    await api.signOut().catch(() => undefined)
    onSignedOut()
  }

  return (
    <>
      <details ref={menu} className="account-menu">
        <summary aria-label={`${t.accountMenu}: ${username}`}>
          <UserIcon width={18} height={18} />
          <span className="account-name">{username}</span>
        </summary>
        <div className="account-popover" role="menu">
          <p className="account-role">{isSuperAdmin ? t.roleSuperAdmin : isAdmin ? t.roleAdmin : t.roleUser}</p>
          <button type="button" role="menuitem"
            onClick={async () => { menu.current!.open = false; setAccount(await api.myAccount()) }}>
            {t.myAccount}
          </button>
          <button type="button" role="menuitem" onClick={() => { menu.current!.open = false; setChangingPassword(true) }}>
            {t.changePassword}
          </button>
          <button type="button" role="menuitem" onClick={signOut}>
            {t.signOut}
          </button>
        </div>
      </details>
      {changingPassword && <ChangePasswordDialog onClose={() => setChangingPassword(false)} />}
      {account && (
        <ContactDialog title={t.myAccount}
          subtitle={`${account.username} · ${account.role === 'SUPER_ADMIN' ? t.roleSuperAdmin : account.role === 'ADMIN' ? t.roleAdmin : t.roleUser}`}
          contact={{ email: account.email, phone: account.phone }}
          onSave={(contact) => api.changeMyContact(contact)} onClose={() => setAccount(null)} />
      )}
    </>
  )
}

function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  const { t } = useI18n()
  const ref = useModal()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (next.length < MIN_PASSWORD_LENGTH) {
      return setError(t.errorPasswordShort(MIN_PASSWORD_LENGTH))
    }
    if (next !== repeat) {
      return setError(t.errorPasswordsDiffer)
    }
    setBusy(true)
    setError(null)
    try {
      await api.changePassword(current, next)
      setDone(true)
    } catch (e) {
      const wrongCurrent = e instanceof ApiError && e.status === 400 && e.detail === 'current password is wrong'
      setError(wrongCurrent ? t.errorCurrentPassword : apiErrorMessage(e, t))
    }
    setBusy(false)
  }

  return (
    <dialog ref={ref} className="dialog dialog-small" aria-labelledby="password-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!busy) onClose() }}>
      <form onSubmit={submit} noValidate>
        <div className="dialog-title-row">
          <h2 id="password-dialog-title">{t.changePassword}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label={t.close}>
            <CloseIcon />
          </button>
        </div>
        {done ? (
          <p role="status">{t.passwordChanged}</p>
        ) : (
          <div className="fields fields-single">
            <label className="field">
              <span>{t.currentPassword}</span>
              <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)}
                autoComplete="current-password" autoFocus />
            </label>
            <label className="field">
              <span>{t.newPassword}</span>
              <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" />
            </label>
            <label className="field">
              <span>{t.repeatPassword}</span>
              <input type="password" value={repeat} onChange={(e) => setRepeat(e.target.value)}
                autoComplete="new-password" />
            </label>
          </div>
        )}
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="dialog-actions dialog-actions-spaced">
          {done ? (
            <button type="button" className="button button-primary" onClick={onClose}>{t.close}</button>
          ) : (
            <>
              <button type="button" className="button" onClick={onClose} disabled={busy}>{t.cancel}</button>
              <button type="submit" className="button button-primary" disabled={busy}>
                {busy ? t.saving : t.save}
              </button>
            </>
          )}
        </div>
      </form>
    </dialog>
  )
}
