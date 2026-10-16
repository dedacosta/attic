import { useCallback, useEffect, useRef, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { ChevronIcon, DownloadIcon, FileIcon, FolderIcon } from '../components/icons'
import { formatDateTime } from '../lib/format'
import { useI18n } from '../i18n'
import type { FolderEntry } from '../api/types'

/** How often the listing is read again while the tab is in sight, to show files added or removed on disk */
const REFRESH_MS = 10_000

/**
 * The Folder tab: the files and subfolders put by hand in the folder on the server, as a tree of
 * folders that open and close. A file opens in a new browser tab, or is downloaded. The listing
 * follows changes on disk: it is read again every few seconds, and when the page comes back in sight.
 */
export default function FolderView() {
  const { t } = useI18n()
  const [entries, setEntries] = useState<FolderEntry[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  // The last listing as text, so an unchanged one does not render again
  const last = useRef('')

  const reload = useCallback(async () => {
    try {
      const listing = await api.listFolder()
      const text = JSON.stringify(listing)
      if (text !== last.current) {
        last.current = text
        setEntries(listing)
      }
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        reload()
      }
    }, REFRESH_MS)
    const onVisible = () => document.visibilityState === 'visible' && reload()
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [reload])

  return (
    <main className="content">
      <h1 className="page-title">{t.tabFolder}</h1>

      {loadError !== null && (
        <div className="banner" role="alert">
          {apiErrorMessage(loadError, t)}{' '}
          <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
        </div>
      )}

      {entries === null && loadError === null && <p className="empty">{t.loading}</p>}
      {entries !== null && entries.length === 0 && <p className="empty">{t.folderEmpty}</p>}
      {entries !== null && entries.length > 0 && <FolderList entries={entries} />}
    </main>
  )
}

function FolderList({ entries }: { entries: FolderEntry[] }) {
  return (
    <ul className="folder-list">
      {/* Keyed by path, so folders stay open when the listing is read again */}
      {entries.map((entry) => (
        <li key={entry.path}>
          {entry.folder ? <FolderItem folder={entry} /> : <FileItem file={entry} />}
        </li>
      ))}
    </ul>
  )
}

function FolderItem({ folder }: { folder: FolderEntry }) {
  const { t } = useI18n()
  return (
    <details className="folder-item">
      <summary className="folder-row">
        <ChevronIcon className="folder-chevron" width={18} height={18} />
        <FolderIcon className="folder-icon" width={20} height={20} />
        <span className="folder-name">{folder.name}</span>
        <span className="folder-meta">{t.folderEntryCount(folder.children.length)}</span>
      </summary>
      {folder.children.length > 0
        ? <FolderList entries={folder.children} />
        : <p className="folder-nothing">{t.folderEmptySubfolder}</p>}
    </details>
  )
}

function FileItem({ file }: { file: FolderEntry }) {
  const { t } = useI18n()
  return (
    <div className="folder-row folder-file">
      <a className="folder-open" href={api.folderFileUrl(file.path)} target="_blank" rel="noreferrer">
        <FileIcon className="folder-icon" width={20} height={20} />
        <span className="folder-name">{file.name}</span>
      </a>
      <span className="folder-meta">{formatSize(file.size, t.locale)}</span>
      <span className="folder-meta folder-date">{formatDateTime(file.modified, t.locale)}</span>
      <a className="icon-button" href={api.folderFileUrl(file.path, true)} download={file.name}
        aria-label={t.downloadNamed(file.name)} title={t.download}>
        <DownloadIcon width={18} height={18} />
      </a>
    </div>
  )
}

/** "850 B", "12 kB", "3.4 MB", in the language's number format */
function formatSize(bytes: number, locale: string): string {
  const units = ['byte', 'kilobyte', 'megabyte', 'gigabyte'] as const
  let value = bytes
  let unit = 0
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000
    unit++
  }
  return new Intl.NumberFormat(locale, {
    style: 'unit',
    unit: units[unit],
    unitDisplay: 'short',
    maximumFractionDigits: value < 10 && unit > 0 ? 1 : 0,
  }).format(value)
}
