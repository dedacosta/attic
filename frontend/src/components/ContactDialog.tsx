import { useState, type FormEvent } from 'react'
import { apiErrorMessage, type Contact } from '../api/api'
import { contactError } from '../lib/contact'
import { useModal } from '../lib/useModal'
import { useI18n } from '../i18n'

interface Props {
  title: string
  /** Shown above the fields, e.g. the account name and role */
  subtitle?: string
  contact: Contact
  onSave: (contact: Contact) => Promise<unknown>
  onClose: () => void
}

/** Edit the email and phone of an account */
export default function ContactDialog({ title, subtitle, contact, onSave, onClose }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const [email, setEmail] = useState(contact.email ?? '')
  const [phone, setPhone] = useState(contact.phone ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const problem = contactError(email, phone, t)
    if (problem) {
      return setError(problem)
    }
    setBusy(true)
    setError(null)
    try {
      await onSave({ email: email.trim() || null, phone: phone.trim() || null })
      onClose()
    } catch (e) {
      setError(apiErrorMessage(e, t))
      setBusy(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog dialog-small" aria-labelledby="contact-title"
      onCancel={(e) => { e.preventDefault(); if (!busy) onClose() }}>
      <form onSubmit={submit} noValidate>
        <h2 id="contact-title">{title}</h2>
        {subtitle && <p className="hint">{subtitle}</p>}
        <div className="fields fields-single">
          <label className="field">
            <span>{t.email} <small>{t.optional}</small></span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email"
              autoCapitalize="none" autoFocus />
          </label>
          <label className="field">
            <span>{t.phone} <small>{t.optional}</small></span>
            <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" />
          </label>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="dialog-actions dialog-actions-spaced">
          <button type="button" className="button" onClick={onClose} disabled={busy}>{t.cancel}</button>
          <button type="submit" className="button button-primary" disabled={busy}>{busy ? t.saving : t.save}</button>
        </div>
      </form>
    </dialog>
  )
}
