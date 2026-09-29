import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import ConfirmDialog from '../components/ConfirmDialog'
import DocumentDialog from '../components/DocumentDialog'
import HeirCard from '../components/HeirCard'
import HeirDialog from '../components/HeirDialog'
import SearchBar from '../components/SearchBar'
import { PlusIcon } from '../components/icons'
import { saveWithPhotos } from '../lib/photos'
import { documentName, formatDate, normalize } from '../lib/format'
import { add, compare, formatFraction, formatPercent, ONE, parseFraction, subtract, ZERO } from '../lib/fraction'
import { usePermissions } from '../lib/permissions'
import { useI18n, type Messages } from '../i18n'
import type { DocumentInput, Heir, HeirDocument, HeirInput, PhotoEntry } from '../api/types'

function matches(heir: Heir, words: string[], t: Messages): boolean {
  const text = normalize(
    [
      heir.name,
      heir.address ?? '',
      heir.filiation ?? '',
      heir.comments ?? '',
      heir.id,
      heir.birthDate ? formatDate(heir.birthDate, t.locale) : '',
    ].join(' '),
  )
  return words.every((word) => text.includes(word))
}

function HeritageSummary({ heirs }: { heirs: Heir[] }) {
  const { t } = useI18n()
  const shares = heirs.map((p) => (p.heritageShare ? parseFraction(p.heritageShare) : null)).filter((s) => s !== null)
  if (shares.length === 0) {
    return null
  }
  const total = shares.reduce(add, ZERO)
  const state = compare(total, ONE)
  return (
    <p className={`heritage heritage-${state === 0 ? 'complete' : state > 0 ? 'over' : 'partial'}`} role="status">
      <strong>{t.heritageTitle}:</strong>{' '}
      {state === 0 && t.heritageComplete}
      {state < 0 && (
        <>
          {t.heritageSummary(formatFraction(total), formatPercent(total, t.locale))} ·{' '}
          {t.heritageMissing(formatFraction(subtract(ONE, total)))}
        </>
      )}
      {state > 0 && (
        <>
          {t.heritageSummary(formatFraction(total), formatPercent(total, t.locale))}. {t.heritageOver}
        </>
      )}
    </p>
  )
}

export default function HeirsView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [heirs, setHeirs] = useState<Heir[] | null>(null)
  const [documents, setDocuments] = useState<HeirDocument[]>([])
  const [sexes, setSexes] = useState<string[]>([])
  const [documentTypes, setDocumentTypes] = useState<string[]>([])
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<Heir | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Heir | null>(null)
  const [editingDocument, setEditingDocument] = useState<HeirDocument | 'new' | null>(null)
  const [confirmingDocumentDelete, setConfirmingDocumentDelete] = useState(false)

  const reload = useCallback(async () => {
    try {
      const [loadedHeirs, loadedDocuments] = await Promise.all([api.listHeirs(), api.listDocuments()])
      setHeirs(loadedHeirs)
      setDocuments(loadedDocuments)
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    api.listSexes().then(setSexes, () => setSexes([]))
    api.listDocumentTypes().then(setDocumentTypes, () => setDocumentTypes([]))
  }, [reload])

  const documentsOf = useCallback((heirId: string) => documents.filter((d) => d.heirId === heirId), [documents])

  const visible = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean)
    return (heirs ?? []).filter((heir) => matches(heir, words, t))
  }, [heirs, query, t])

  const editingHeir = editing !== null && editing !== 'new' ? editing : null

  async function save(input: HeirInput) {
    const saved = editingHeir ? await api.updateHeir(editingHeir.id, input) : await api.createHeir(input)
    await reload()
    // A new heir stays open so that their documents can be added right away
    setEditing(editingHeir ? null : saved)
  }

  async function saveDocumentOfHeir(input: DocumentInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editingDocument !== null && editingDocument !== 'new' ? editingDocument : null
    await saveWithPhotos(
      () => (existing ? api.updateDocument(existing.id, input) : api.createDocument(input)),
      '/api/documents', photos, setEditingDocument, onPhotos,
    )
    await reload()
    setEditingDocument(null)
  }

  function closeEditor() {
    setEditing(null)
    reload()
  }

  async function confirmDelete() {
    if (!deleting) {
      return
    }
    await api.deleteHeir(deleting.id)
    await reload()
    setDeleting(null)
    setEditing(null)
  }

  const deletingDocument = editingDocument !== null && editingDocument !== 'new' ? editingDocument : null

  async function confirmDocumentDelete() {
    if (!deletingDocument) {
      return
    }
    await api.deleteDocument(deletingDocument.id)
    await reload()
    setConfirmingDocumentDelete(false)
    setEditingDocument(null)
  }

  return (
    <>
      <main className="content">
        <SearchBar query={query} onQuery={setQuery} placeholder={t.heirsSearchPlaceholder} label={t.heirsSearchLabel}>
          {canEdit && <button type="button" className="button button-primary" onClick={() => setEditing('new')}
            aria-label={t.newHeir}>
            <PlusIcon width={18} height={18} /> <span className="button-label">{t.newHeir}</span>
          </button>}
        </SearchBar>

        <div className="toolbar">
          {heirs && canEdit && <HeritageSummary heirs={heirs} />}
          {heirs && (
            <span className="count">
              {query ? t.filteredHeirCount(visible.length, heirs.length) : t.heirCount(heirs.length)}
            </span>
          )}
        </div>

        {loadError !== null && (
          <div className="banner" role="alert">
            {apiErrorMessage(loadError, t)}{' '}
            <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
          </div>
        )}

        {heirs === null && loadError === null && <p className="empty">{t.loading}</p>}

        {heirs !== null && heirs.length === 0 && (
          <div className="empty">
            <p>{t.emptyHeirs}</p>
            {canEdit && <button type="button" className="button button-primary" onClick={() => setEditing('new')}>
              <PlusIcon width={18} height={18} /> {t.addFirstHeir}
            </button>}
          </div>
        )}

        {heirs !== null && heirs.length > 0 && visible.length === 0 && (
          <p className="empty">{t.noHeirMatches}</p>
        )}

        <div className="grid">
          {visible.map((heir) => (
            <HeirCard key={heir.id} heir={heir} documents={documentsOf(heir.id)}
              onEdit={() => setEditing(heir)} onDelete={() => setDeleting(heir)} />
          ))}
        </div>
      </main>

      {editing !== null && (
        <HeirDialog
          key={editingHeir?.id ?? 'new'}
          heir={editingHeir}
          sexes={sexes}
          documents={editingHeir ? documentsOf(editingHeir.id) : []}
          onSave={save}
          onDelete={() => editingHeir && setDeleting(editingHeir)}
          onClose={closeEditor}
          onOpenDocument={setEditingDocument}
          onAddDocument={() => setEditingDocument('new')}
        />
      )}

      {editingDocument !== null && (
        <DocumentDialog
          key="document-dialog"
          document={editingDocument === 'new' ? null : editingDocument}
          types={documentTypes}
          heirs={heirs ?? []}
          defaultHeirId={editingHeir?.id}
          onSave={saveDocumentOfHeir}
          onDelete={() => setConfirmingDocumentDelete(true)}
          onClose={() => { setEditingDocument(null); reload() }}
        />
      )}

      {confirmingDocumentDelete && deletingDocument && (
        <ConfirmDialog
          title={t.deleteDocumentTitle}
          message={t.deleteMessage(documentName(deletingDocument, t))}
          confirmLabel={t.delete}
          onConfirm={confirmDocumentDelete}
          onCancel={() => setConfirmingDocumentDelete(false)}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title={t.deleteHeirTitle}
          message={t.deleteHeirMessage(deleting.name, documentsOf(deleting.id).length)}
          confirmLabel={t.delete}
          onConfirm={confirmDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}
