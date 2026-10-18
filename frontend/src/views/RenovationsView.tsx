import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, apiErrorMessage } from '../api/api'
import CommentBox from '../components/CommentBox'
import RenovationExpenses from '../components/RenovationExpenses'
import ConfirmDialog from '../components/ConfirmDialog'
import PdfViewer from '../components/PdfViewer'
import { ChevronIcon, CloseIcon, FileIcon, PencilIcon, PlusIcon, TrashIcon } from '../components/icons'
import { formatEuros } from '../lib/format'
import { exportRenovationsLatex } from '../lib/latex'
import { usePermissions } from '../lib/permissions'
import { useModal } from '../lib/useModal'
import { useI18n } from '../i18n'
import type { Renovation, RenovationInput } from '../api/types'
import type { GeneratedPdf } from '../lib/pdf'

/**
 * The Renovations tab: a small card per renovation that opens to show its expenses, which make its
 * cost, and who pays what of it in its year, shared like the contribution, with a box to tick off
 * each payment. Workmanship can be done by an heir. Everybody sees it; administrators add, change
 * and delete renovations and their expenses and tick off payments.
 */
export default function RenovationsView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [renovations, setRenovations] = useState<Renovation[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [editing, setEditing] = useState<Renovation | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Renovation | null>(null)
  const [preview, setPreview] = useState<GeneratedPdf | null>(null)

  const reload = useCallback(async () => {
    try {
      setRenovations(await api.listRenovations())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  function toggle(id: string) {
    setExpanded((current) => {
      const next = new Set(current)
      if (!next.delete(id)) {
        next.add(id)
      }
      return next
    })
  }

  /** Show a renovation as the server answered after a change */
  const replace = (changed: Renovation) =>
    setRenovations((current) => current?.map((r) => (r.id === changed.id ? changed : r)) ?? null)

  async function save(input: RenovationInput) {
    const saved = editing && editing !== 'new'
      ? await api.updateRenovation(editing.id, input)
      : await api.createRenovation(input)
    await reload()
    // A new renovation opens, ready to tick off payments
    setExpanded((current) => new Set(current).add(saved.id))
    setEditing(null)
  }

  async function downloadPdf() {
    // Loaded on demand: the PDF library is large
    const { exportRenovationsPdf } = await import('../lib/pdf')
    setPreview(await exportRenovationsPdf(renovations ?? [], t))
  }

  return (
    <>
      <main className="content">
        <h1 className="page-title">{t.tabRenovations}</h1>

        <div className="toolbar">
          {canEdit && (
            <button type="button" className="button button-primary" onClick={() => setEditing('new')}>
              <PlusIcon width={18} height={18} /> {t.newRenovation}
            </button>
          )}
          <button type="button" className="button" onClick={downloadPdf} disabled={!renovations?.length}
            aria-label={t.exportRenovationsPdf} title={t.exportRenovationsPdf}>
            <FileIcon width={18} height={18} /> PDF
          </button>
          <button type="button" className="button" onClick={() => exportRenovationsLatex(renovations ?? [], t)}
            disabled={!renovations?.length} aria-label={t.exportRenovationsLatex} title={t.exportRenovationsLatex}>
            <FileIcon width={18} height={18} /> LaTeX
          </button>
        </div>

        {loadError !== null && (
          <div className="banner" role="alert">
            {apiErrorMessage(loadError, t)}{' '}
            <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
          </div>
        )}

        {renovations === null && loadError === null && <p className="empty">{t.loading}</p>}
        {renovations !== null && renovations.length === 0 && <p className="empty">{t.noRenovations}</p>}

        <div className="contribution-grid">
          {renovations?.map((renovation) => (
            <RenovationCard key={renovation.id} renovation={renovation} editable={canEdit}
              expanded={expanded.has(renovation.id)} onToggle={() => toggle(renovation.id)}
              onChanged={replace} onEdit={() => setEditing(renovation)} />
          ))}
        </div>

        {editing !== null && (
          <RenovationDialog renovation={editing === 'new' ? null : editing} onSave={save}
            onDelete={() => editing !== 'new' && setDeleting(editing)} onClose={() => setEditing(null)} />
        )}

        {deleting && (
          <ConfirmDialog title={t.deleteRenovationTitle} message={t.deleteRenovationMessage(deleting.title)}
            confirmLabel={t.delete} onCancel={() => setDeleting(null)}
            onConfirm={async () => {
              await api.deleteRenovation(deleting.id)
              await reload()
              setDeleting(null)
              setEditing(null)
            }} />
        )}
      </main>
      {preview && (
        <PdfViewer source={preview.blob} title={preview.title} fileName={preview.fileName}
          onClose={() => setPreview(null)} />
      )}
    </>
  )
}

function RenovationCard(props: {
  renovation: Renovation
  editable: boolean
  expanded: boolean
  onToggle: () => void
  onChanged: (renovation: Renovation) => void
  onEdit: () => void
}) {
  const { renovation, editable, expanded, onToggle, onChanged, onEdit } = props
  const { t } = useI18n()
  const [error, setError] = useState<string | null>(null)
  const id = `renovation-${renovation.id}`

  async function change(action: () => Promise<Renovation>) {
    setError(null)
    try {
      onChanged(await action())
    } catch (e) {
      setError(apiErrorMessage(e, t))
    }
  }

  return (
    <section className={expanded ? 'card contribution-card contribution-card-open' : 'card contribution-card'}
      aria-labelledby={id}>
      <h2 className="contribution-heading">
        <button type="button" className="contribution-summary" onClick={onToggle} aria-expanded={expanded}
          aria-controls={`${id}-lines`}>
          <span className="contribution-title">
            <span>{renovation.year}</span>
            <ChevronIcon className="contribution-chevron" width={18} height={18} />
          </span>
          <span id={id} className="renovation-title">{renovation.title}</span>
          <span className="contribution-facts">
            <span>{t.amountPaid}{t.colon} <strong>{formatEuros(renovation.paidEur, t.locale)}</strong></span>
            <span className={renovation.missingEur > 0 ? 'contribution-missing' : undefined}>
              {t.amountMissing}{t.colon} <strong>{formatEuros(renovation.missingEur, t.locale)}</strong>
            </span>
          </span>
          {!expanded && renovation.comment && (
            <span className="contribution-comment-preview">{renovation.comment}</span>
          )}
        </button>
      </h2>
      {expanded && (
        <div id={`${id}-lines`} className="contribution-body">
          {renovation.description && <p className="renovation-description">{renovation.description}</p>}
          <h3 className="renovation-section">{t.expenses}</h3>
          <RenovationExpenses renovation={renovation} editable={editable} onChanged={onChanged} />
          <h3 className="renovation-section">
            {t.tabHeirs}
            <span className="renovation-cost">
              {t.cost}{t.colon} <strong>{formatEuros(renovation.costEur, t.locale)}</strong>
            </span>
          </h3>
          {renovation.lines.length === 0 && <p className="hint">{t.nobodyPays}</p>}
          <ul className="renovation-heirs">
            {renovation.lines.map((line) => (
              <li key={line.heirId} className="renovation-heir">
                <span className="contribution-heir">{line.deceased ? `${line.heir} †` : line.heir}</span>
                <span className="renovation-heir-due">
                  {line.dueEur !== null ? formatEuros(line.dueEur, t.locale) : '—'}
                </span>
                <label className="renovation-payment">
                  <input type="checkbox" checked={line.paid} disabled={!editable} aria-label={t.paidBy(line.heir)}
                    onChange={(e) =>
                      change(() => api.setRenovationPayment(renovation.id, line.heirId, { paid: e.target.checked }))} />
                  <span>{t.amountPaid}</span>
                </label>
                <label className="renovation-payment">
                  <input type="checkbox" checked={line.workmanship} disabled={!editable}
                    aria-label={t.workmanshipOf(line.heir)}
                    onChange={(e) => change(() =>
                      api.setRenovationPayment(renovation.id, line.heirId, { workmanship: e.target.checked }))} />
                  <span>⚒ {t.expenseKinds.WORKMANSHIP}</span>
                </label>
              </li>
            ))}
          </ul>
          {editable ? (
            <CommentBox comment={renovation.comment}
              onSave={async (text) => {
                if (text.trim() !== (renovation.comment ?? '')) {
                  await change(() => api.setRenovationComment(renovation.id, text))
                }
              }} />
          ) : (
            renovation.comment && <p className="contribution-comment">{renovation.comment}</p>
          )}
          {error && <p className="form-error" role="alert">{error}</p>}
          {editable && (
            <footer className="contribution-footer">
              <button type="button" className="button button-small" onClick={onEdit}>
                <PencilIcon width={16} height={16} /> {t.edit}
              </button>
            </footer>
          )}
        </div>
      )}
    </section>
  )
}

/** Add or change a renovation; deleting one starts here too, so that it is never a slip */
function RenovationDialog(props: {
  renovation: Renovation | null
  onSave: (input: RenovationInput) => Promise<void>
  onDelete: () => void
  onClose: () => void
}) {
  const { renovation, onSave, onDelete, onClose } = props
  const { t } = useI18n()
  const ref = useModal()
  const [form, setForm] = useState({
    year: String(renovation?.year ?? new Date().getFullYear()),
    title: renovation?.title ?? '',
    description: renovation?.description ?? '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    const year = Number(form.year)
    if (!Number.isInteger(year) || year < 1900 || year > 2999) {
      return setError(t.errorYear)
    }
    if (!form.title.trim()) {
      return setError(t.errorTitle)
    }
    setSaving(true)
    setError(null)
    try {
      await onSave({
        year,
        title: form.title.trim(),
        description: form.description.trim() || null,
      })
    } catch (e) {
      setError(apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="renovation-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="renovation-dialog-title">{renovation ? t.editRenovation : t.newRenovation}</h2>
          <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
            <CloseIcon />
          </button>
        </header>
        <div className="dialog-content">
          <div className="fields">
            <label className="field">
              <span>{t.year}</span>
              <input inputMode="numeric" value={form.year} onChange={(e) => set('year', e.target.value)} />
            </label>
            <label className="field field-wide">
              <span>{t.renovationTitle}</span>
              <input value={form.title} onChange={(e) => set('title', e.target.value)} maxLength={200}
                autoFocus={!renovation} autoComplete="off" />
            </label>
            <label className="field field-wide">
              <span>{t.description} <small>{t.optional}</small></span>
              <textarea rows={3} value={form.description} onChange={(e) => set('description', e.target.value)} />
            </label>
          </div>
          {error && <p className="form-error" role="alert">{error}</p>}
        </div>
        <footer className="dialog-actions">
          {renovation && (
            <button type="button" className="button button-quiet button-danger-text" onClick={onDelete}
              disabled={saving}>
              <TrashIcon width={18} height={18} /> {t.delete}
            </button>
          )}
          <span className="spacer" />
          <button type="button" className="button" onClick={onClose} disabled={saving}>{t.cancel}</button>
          <button type="submit" className="button button-primary" disabled={saving}>
            {saving ? t.saving : renovation ? t.save : t.create}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
