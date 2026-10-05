import { useEffect, useMemo } from 'react'
import { useI18n } from '../i18n'
import { FileIcon, ImageIcon } from './icons'
import type { PictureChange } from '../api/types'

export const PICTURE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/avif']

export const PDF_TYPE = 'application/pdf'

// HEIC is accepted but not offered: then iPhones convert photos to JPEG, which gets a thumbnail
const OFFERED_PICTURE_TYPES = PICTURE_TYPES.filter((type) => type !== 'image/heic').join(',')

interface Props {
  /** Thumbnail or picture currently stored, if any */
  current: string | null
  /** Full-size picture currently stored, if any */
  fullUrl: string | null
  /** Content type of the stored picture */
  currentType?: string | null
  /** Also accept a PDF, as documents do */
  acceptPdf?: boolean
  change: PictureChange
  onChange: (change: PictureChange) => void
  onError: (message: string | null) => void
  /** Only show the picture, without add / change / remove */
  readOnly?: boolean
}

/** Picture preview with add / change / remove buttons, used by the item and document forms. */
export default function PictureField(props: Props) {
  const { current, fullUrl, currentType = null, acceptPdf = false, change, onChange, onError, readOnly = false } = props
  const { t } = useI18n()
  // A PDF has no preview: it shows as a file
  const pdf = change.kind === 'replace' ? change.file.type === PDF_TYPE : change.kind === 'keep' && currentType === PDF_TYPE

  const newPictureUrl = useMemo(
    () => (change.kind === 'replace' ? URL.createObjectURL(change.file) : null),
    [change],
  )
  useEffect(() => () => { if (newPictureUrl) URL.revokeObjectURL(newPictureUrl) }, [newPictureUrl])

  const preview = change.kind === 'replace' ? newPictureUrl : change.kind === 'remove' ? null : current
  const hasFile = preview !== null || pdf

  function choose(file: File | undefined) {
    if (!file) {
      return
    }
    if (!PICTURE_TYPES.includes(file.type) && !(acceptPdf && file.type === PDF_TYPE)) {
      onError(acceptPdf ? t.errorDocumentFileType : t.errorPictureType)
      return
    }
    onError(null)
    onChange({ kind: 'replace', file })
  }

  return (
    <div className="picture-field">
      <div className="picture-preview">
        {pdf ? (
          <span className="picture-pdf">
            <FileIcon width={40} height={40} />
            <span>{change.kind === 'replace' ? change.file.name : 'PDF'}</span>
          </span>
        ) : preview ? <img src={preview} alt="" /> : <ImageIcon width={48} height={48} />}
      </div>
      <div className="picture-actions">
        <label className="button" hidden={readOnly}>
          {acceptPdf ? (hasFile ? t.changeFile : t.addPictureOrPdf) : hasFile ? t.changePicture : t.addPicture}
          <input type="file" accept={acceptPdf ? `${OFFERED_PICTURE_TYPES},${PDF_TYPE}` : OFFERED_PICTURE_TYPES} hidden
            onChange={(e) => { choose(e.target.files?.[0]); e.target.value = '' }} />
        </label>
        {hasFile && !readOnly && (
          <button type="button" className="button button-quiet"
            onClick={() => onChange(current ? { kind: 'remove' } : { kind: 'keep' })}>
            {acceptPdf ? t.removeFile : t.removePicture}
          </button>
        )}
        {change.kind === 'keep' && fullUrl && (
          <a className="link" href={fullUrl} target="_blank" rel="noreferrer">
            {pdf ? t.openPdf : t.openFullPicture}
          </a>
        )}
      </div>
    </div>
  )
}
