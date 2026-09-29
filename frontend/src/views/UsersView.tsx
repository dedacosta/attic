import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { api, ApiError, apiErrorMessage } from '../api/api'
import { byName, formatDateTime } from '../lib/format'
import { contactError } from '../lib/contact'
import ContactDialog from '../components/ContactDialog'
import { usePermissions } from '../lib/permissions'
import { MIN_PASSWORD_LENGTH } from '../lib/passwords'
import { useModal } from '../lib/useModal'
import { useI18n, type Messages } from '../i18n'
import ConfirmDialog from '../components/ConfirmDialog'
import { PlusIcon, TrashIcon } from '../components/icons'
import type { Heir, Role, UserAccount } from '../api/types'
import type { Contact } from '../api/api'

const ROLES: Role[] = ['SUPER_ADMIN', 'ADMIN', 'USER']

/** Server refusals shown in the user's language */
function accountError(error: unknown, t: Messages): string {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return t.errorUsernameTaken
    }
    if (error.status === 403) {
      return t.errorNotAllowed
    }
    if (error.detail === 'you cannot delete your own account') {
      return t.errorDeleteSelf
    }
    if (error.detail === 'email is not valid') {
      return t.errorEmail
    }
    if (error.detail === 'phone is not valid') {
      return t.errorPhone
    }
    if (error.detail === 'heir is already linked to another account') {
      return t.errorHeirLinked
    }
    if (error.detail === 'there must be at least one super-administrator') {
      return t.errorLastAdmin
    }
  }
  return apiErrorMessage(error, t)
}

/**
 * The Users tab: account management. Administrators may add user accounts, reset users' passwords
 * and link users to heirs; changing roles, managing administrators and deleting accounts is for
 * super-administrators.
 */
export default function UsersView({ currentUser }: { currentUser: string }) {
  const { t } = useI18n()
  const { isSuperAdmin } = usePermissions()
  const [users, setUsers] = useState<UserAccount[]>([])
  const [heirs, setHeirs] = useState<Heir[]>([])
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)
  const [resetting, setResetting] = useState<string | null>(null)
  const [editingContact, setEditingContact] = useState<UserAccount | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      const [loadedUsers, loadedHeirs] = await Promise.all([api.listUsers(), api.listHeirs()])
      setUsers(loadedUsers)
      setHeirs(byName(loadedHeirs, t.locale))
    } catch (e) {
      setMessage({ text: accountError(e, t), error: true })
    }
  }, [t])

  const heirOptions = (
    <>
      <option value="">{t.noHeir}</option>
      {heirs.map((heir) => <option key={heir.id} value={heir.id}>{heir.name}</option>)}
    </>
  )

  useEffect(() => {
    reload()
  }, [reload])

  /** Runs an account change and shows the outcome; resolves to whether it succeeded */
  async function run(action: () => Promise<unknown>, success?: string): Promise<boolean> {
    setMessage(null)
    let succeeded = false
    try {
      await action()
      succeeded = true
      if (success) {
        setMessage({ text: success, error: false })
      }
    } catch (e) {
      setMessage({ text: accountError(e, t), error: true })
    }
    await reload()
    return succeeded
  }

  const roleLabel = (role: Role) => (role === 'SUPER_ADMIN' ? t.roleSuperAdmin : role === 'ADMIN' ? t.roleAdmin : t.roleUser)

  return (
    <main className="content users-page" aria-labelledby="users-title">
      <h1 id="users-title" className="page-title">{t.users}</h1>

      <section className="users-panel">
        <ul className="user-list">
          {users.map((user) => (
            <li key={user.username} className="user-row">
              <div className="user-name">
                <strong>{user.username}</strong>
                {user.username.toLowerCase() === currentUser.toLowerCase() && <span className="tag">{t.you}</span>}
                <small>{t.accountCreatedOn(formatDateTime(user.createdAt, t.locale))}</small>
                {(user.email || user.phone) && (
                  <small className="user-contact">
                    {user.email && <a href={`mailto:${user.email}`}>{user.email}</a>}
                    {user.email && user.phone && ' · '}
                    {user.phone && <a href={`tel:${user.phone.replace(/[^+0-9]/g, '')}`}>{user.phone}</a>}
                  </small>
                )}
              </div>
              <select value={user.role} aria-label={`${t.role}: ${user.username}`} disabled={!isSuperAdmin}
                onChange={(e) => run(() => api.changeRole(user.username, e.target.value as Role))}>
                {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
              </select>
              <select value={user.heirId ?? ''} aria-label={`${t.accountHeir}: ${user.username}`}
                disabled={!isSuperAdmin && user.role !== 'USER'}
                onChange={(e) => run(() => api.linkHeir(user.username, e.target.value || null))}>
                {heirOptions}
              </select>
              {(isSuperAdmin || user.role === 'USER') && (
                <button type="button" className="button button-small" onClick={() => setEditingContact(user)}>
                  {t.contactDetails}
                </button>
              )}
              {(isSuperAdmin || user.role === 'USER') && (
                <button type="button" className="button button-small" onClick={() => setResetting(user.username)}>
                  {t.resetPassword}
                </button>
              )}
              {isSuperAdmin && (
                <button type="button" className="icon-button" onClick={() => setDeleting(user.username)}
                  aria-label={t.deleteNamed(user.username)}>
                  <TrashIcon width={18} height={18} />
                </button>
              )}
            </li>
          ))}
        </ul>

        {message && (
          <p className={message.error ? 'form-error' : 'form-success'} role={message.error ? 'alert' : 'status'}>
            {message.text}
          </p>
        )}

        <NewUserForm onCreate={(username, password, role, heirId, contact) =>
          run(() => api.createUser(username, password, role, heirId, contact), t.addedUser(username))} roleLabel={roleLabel}
          roles={isSuperAdmin ? ROLES : ['USER']} heirOptions={heirOptions} />
      </section>

      {editingContact && (
        <ContactDialog title={t.editContact(editingContact.username)}
          contact={{ email: editingContact.email, phone: editingContact.phone }}
          onClose={() => setEditingContact(null)}
          onSave={async (contact) => {
            await api.changeContact(editingContact.username, contact)
            await reload()
          }} />
      )}

      {resetting && (
        <ResetPasswordDialog username={resetting} onClose={() => setResetting(null)}
          onReset={(password) => run(() => api.resetPassword(resetting, password), t.passwordReset(resetting))} />
      )}

      {deleting && (
        <ConfirmDialog title={t.deleteUserTitle} message={t.deleteUserMessage(deleting)} confirmLabel={t.delete}
          onCancel={() => setDeleting(null)}
          onConfirm={async () => {
            await run(() => api.deleteUser(deleting))
            setDeleting(null)
          }} />
      )}
    </main>
  )
}

function NewUserForm(props: {
  /** Resolves to whether the account was created */
  onCreate: (username: string, password: string, role: Role, heirId: string | null, contact: Contact) => Promise<boolean>
  roleLabel: (role: Role) => string
  /** Roles this administrator may give */
  roles: Role[]
  heirOptions: ReactNode
}) {
  const { t } = useI18n()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('USER')
  const [heirId, setHeirId] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!username.trim()) {
      return setError(t.errorUsername)
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      return setError(t.errorPasswordShort(MIN_PASSWORD_LENGTH))
    }
    const problem = contactError(email, phone, t)
    if (problem) {
      return setError(problem)
    }
    setError(null)
    // Keep what was typed when the account could not be created, e.g. because the name is taken
    const contact = { email: email.trim() || null, phone: phone.trim() || null }
    if (!(await props.onCreate(username.trim(), password, role, heirId || null, contact))) {
      return
    }
    setUsername('')
    setPassword('')
    setRole('USER')
    setHeirId('')
    setEmail('')
    setPhone('')
  }

  return (
    <form className="new-user" onSubmit={submit} noValidate>
      <h3>{t.addUser}</h3>
      <div className="fields">
        <label className="field">
          <span>{t.username}</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off"
            autoCapitalize="none" autoCorrect="off" />
        </label>
        <label className="field">
          <span>{t.password}</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password" />
        </label>
        <label className="field">
          <span>{t.role}</span>
          <select value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={props.roles.length === 1}>
            {props.roles.map((r) => <option key={r} value={r}>{props.roleLabel(r)}</option>)}
          </select>
        </label>
        <label className="field">
          <span>{t.email} <small>{t.optional}</small></span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off"
            autoCapitalize="none" />
        </label>
        <label className="field">
          <span>{t.phone} <small>{t.optional}</small></span>
          <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="off" />
        </label>
        <label className="field">
          <span>{t.accountHeir} <small>{t.optional}</small></span>
          <select value={heirId} onChange={(e) => setHeirId(e.target.value)}>
            {props.heirOptions}
          </select>
        </label>
        <div className="field field-end field-wide">
          <button type="submit" className="button button-primary">
            <PlusIcon width={18} height={18} /> {t.addUser}
          </button>
        </div>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
    </form>
  )
}

function ResetPasswordDialog(props: { username: string; onReset: (password: string) => Promise<unknown>; onClose: () => void }) {
  const { t } = useI18n()
  const ref = useModal()
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (password.length < MIN_PASSWORD_LENGTH) {
      return setError(t.errorPasswordShort(MIN_PASSWORD_LENGTH))
    }
    await props.onReset(password)
    props.onClose()
  }

  return (
    <dialog ref={ref} className="dialog dialog-small" aria-labelledby="reset-title"
      onCancel={(e) => { e.preventDefault(); props.onClose() }}>
      <form onSubmit={submit} noValidate>
        <h2 id="reset-title">{t.resetPassword}</h2>
        <label className="field">
          <span>{t.newPasswordFor(props.username)}</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password" autoFocus />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="dialog-actions dialog-actions-spaced">
          <button type="button" className="button" onClick={props.onClose}>{t.cancel}</button>
          <button type="submit" className="button button-primary">{t.save}</button>
        </div>
      </form>
    </dialog>
  )
}
