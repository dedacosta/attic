import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import ConfirmDialog from '../components/ConfirmDialog'
import DocumentCard from '../components/DocumentCard'
import DocumentDialog from '../components/DocumentDialog'
import SearchBar from '../components/SearchBar'
import { PlusIcon } from '../components/icons'
import { saveWithPhotos } from '../lib/photos'
import { byName, documentName, documentTypeLabel, formatDate, normalize } from '../lib/format'
import { validity } from '../lib/validity'
import { usePermissions } from '../lib/permissions'
import { useI18n, type Messages } from '../i18n'
import type { DocumentInput, Heir, HeirDocument, PhotoEntry } from '../api/types'

type ValidityFilter = 'all' | 'valid' | 'expiring' | 'expired'

/** Heir filter value for documents without heir; heir ids are UUIDs, so it cannot clash */
const NO_HEIR = 'none'

function matches(document: HeirDocument, words: string[], t: Messages): boolean {
  const text = normalize(
    [
      document.heir ?? t.noHeir,
      documentTypeLabel(document.type, t),
      document.comments ?? '',
      document.id,
      document.validUntil ? formatDate(document.validUntil, t.locale) : '',
    ].join(' '),
  )
  return words.every((word) => text.includes(word))
}

function matchesValidity(document: HeirDocument, filter: ValidityFilter): boolean {
  const status = validity(document.validUntil)
  // Documents without an expiry date count as valid
  return filter === 'all' || filter === (status === 'none' ? 'valid' : status)
}

export default function DocumentsView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [documents, setDocuments] = useState<HeirDocument[] | null>(null)
  const [heirs, setHeirs] = useState<Heir[]>([])
  const [types, setTypes] = useState<string[]>([])
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [heirFilter, setHeirFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [validityFilter, setValidityFilter] = useState<ValidityFilter>('all')
  const [editing, setEditing] = useState<HeirDocument | 'new' | null>(null)
  const [deleting, setDeleting] = useState<HeirDocument | null>(null)

  const reload = useCallback(async () => {
    try {
      const [loadedDocuments, loadedHeirs] = await Promise.all([api.listDocuments(), api.listHeirs()])
      setDocuments(loadedDocuments)
      setHeirs(loadedHeirs)
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    api.listDocumentTypes().then(setTypes, () => setTypes([]))
  }, [reload])

  const visible = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean)
    return (documents ?? []).filter(
      (document) =>
        (!heirFilter || (document.heirId ?? NO_HEIR) === heirFilter) &&
        (!typeFilter || document.type === typeFilter) &&
        matchesValidity(document, validityFilter) &&
        matches(document, words, t),
    )
  }, [documents, query, heirFilter, typeFilter, validityFilter, t])

  // Grouped by heir; the server already sorts by heir name, then type, with documents without heir last
  const groups = useMemo(() => {
    const byHeir = new Map<string, HeirDocument[]>()
    for (const document of visible) {
      const key = document.heirId ?? NO_HEIR
      byHeir.set(key, [...(byHeir.get(key) ?? []), document])
    }
    return [...byHeir.values()]
  }, [visible])

  const hasDocumentsWithoutHeir = (documents ?? []).some((document) => document.heirId === null)

  const filtered = query !== '' || heirFilter !== '' || typeFilter !== '' || validityFilter !== 'all'

  async function save(input: DocumentInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editing === 'new' ? null : editing
    await saveWithPhotos(
      () => (existing ? api.updateDocument(existing.id, input) : api.createDocument(input)),
      '/api/documents', photos, setEditing, onPhotos,
    )
    await reload()
    setEditing(null)
  }

  function closeEditor() {
    setEditing(null)
    reload()
  }

  async function confirmDelete() {
    if (!deleting) {
      return
    }
    await api.deleteDocument(deleting.id)
    // Refresh before closing so the deleted card is gone when the dialog disappears
    await reload()
    setDeleting(null)
    setEditing(null)
  }

  const validityLabels: Record<ValidityFilter, string> = {
    all: t.all,
    valid: t.statusValid,
    expiring: t.statusExpiring,
    expired: t.statusExpired,
  }

  return (
    <>
      <main className="content">
        <SearchBar query={query} onQuery={setQuery} placeholder={t.docSearchPlaceholder} label={t.docSearchLabel}>
          {canEdit && <button type="button" className="button button-primary" onClick={() => setEditing('new')}
            aria-label={t.newDocument}>
            <PlusIcon width={18} height={18} /> <span className="button-label">{t.newDocument}</span>
          </button>}
        </SearchBar>

        <div className="toolbar">
          <select value={heirFilter} onChange={(e) => setHeirFilter(e.target.value)} aria-label={t.filterByHeir}>
            <option value="">{t.allHeirs}</option>
            {byName(heirs, t.locale).map((heir) => <option key={heir.id} value={heir.id}>{heir.name}</option>)}
            {(hasDocumentsWithoutHeir || heirFilter === NO_HEIR) && <option value={NO_HEIR}>{t.noHeir}</option>}
          </select>
          <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} aria-label={t.filterByType}>
            <option value="">{t.allTypes}</option>
            {types.map((type) => <option key={type} value={type}>{documentTypeLabel(type, t)}</option>)}
          </select>
          <div className="segmented" role="group" aria-label={t.filterByValidity}>
            {(['all', 'valid', 'expiring', 'expired'] as const).map((value) => (
              <button key={value} type="button" aria-pressed={validityFilter === value}
                onClick={() => setValidityFilter(value)}>
                {validityLabels[value]}
              </button>
            ))}
          </div>
          {documents && (
            <span className="count">
              {filtered ? t.filteredDocumentCount(visible.length, documents.length) : t.documentCount(documents.length)}
            </span>
          )}
        </div>

        {loadError !== null && (
          <div className="banner" role="alert">
            {apiErrorMessage(loadError, t)}{' '}
            <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
          </div>
        )}

        {documents === null && loadError === null && <p className="empty">{t.loading}</p>}

        {documents !== null && documents.length === 0 && (
          <div className="empty">
            <p>{t.emptyDocuments}</p>
            {canEdit && <button type="button" className="button button-primary" onClick={() => setEditing('new')}>
              <PlusIcon width={18} height={18} /> {t.addFirstDocument}
            </button>}
          </div>
        )}

        {documents !== null && documents.length > 0 && visible.length === 0 && (
          <p className="empty">{t.noDocumentMatches}</p>
        )}

        {groups.map((heirDocuments) => (
          <section key={heirDocuments[0].heirId ?? NO_HEIR} className="group" aria-label={heirDocuments[0].heir ?? t.noHeir}>
            <h2 className="group-title">
              {heirDocuments[0].heir ?? t.noHeir} <span className="group-count">{heirDocuments.length}</span>
            </h2>
            <div className="grid">
              {heirDocuments.map((document) => (
                <DocumentCard key={document.id} document={document} onEdit={() => setEditing(document)}
                  onDelete={() => setDeleting(document)} />
              ))}
            </div>
          </section>
        ))}
      </main>

      {editing !== null && (
        <DocumentDialog
          key="document-dialog"
          document={editing === 'new' ? null : editing}
          types={types}
          heirs={heirs}
          defaultHeirId={heirFilter && heirFilter !== NO_HEIR ? heirFilter : undefined}
          onSave={save}
          onDelete={() => editing !== 'new' && setDeleting(editing)}
          onClose={closeEditor}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title={t.deleteDocumentTitle}
          message={t.deleteMessage(documentName(deleting, t))}
          confirmLabel={t.delete}
          onConfirm={confirmDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}
