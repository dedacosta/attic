import { useState } from 'react'
import { apiErrorMessage } from '../api/api'
import { useI18n } from '../i18n'
import { useModal } from '../lib/useModal'

interface Props {
  title: string
  message: string
  confirmLabel: string
  onConfirm: () => Promise<void>
  onCancel: () => void
}

export default function ConfirmDialog({ title, message, confirmLabel, onConfirm, onCancel }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog dialog-small" onCancel={(e) => { e.preventDefault(); if (!busy) onCancel() }}>
      <h2>{title}</h2>
      <p>{message}</p>
      {error !== null && <p className="form-error" role="alert">{apiErrorMessage(error, t)}</p>}
      <div className="dialog-actions">
        <button type="button" className="button" onClick={onCancel} disabled={busy}>
          {t.cancel}
        </button>
        <button type="button" className="button button-danger" onClick={confirm} disabled={busy} autoFocus>
          {busy ? t.deleting : confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
