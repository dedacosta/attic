import { useCallback, useEffect, useState } from 'react'
import { api, apiErrorMessage, ApiError } from '../api/api'
import PropertyDialog from '../components/PropertyDialog'
import PropertyPage from '../components/PropertyPage'
import { HouseIcon, PlusIcon } from '../components/icons'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import type { Property } from '../api/types'

/** The family house: its page, or a way to set it up. */
export default function HouseView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  // undefined while loading, null when the house is not set up yet
  const [house, setHouse] = useState<Property | null | undefined>(undefined)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [settingUp, setSettingUp] = useState(false)

  const reload = useCallback(async () => {
    try {
      setHouse(await api.house())
      setLoadError(null)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setHouse(null)
        setLoadError(null)
      } else {
        setLoadError(e)
      }
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  if (loadError !== null) {
    return (
      <main className="content">
        <div className="banner" role="alert">
          {apiErrorMessage(loadError, t)}{' '}
          <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
        </div>
      </main>
    )
  }

  if (house === undefined) {
    return <main className="content"><p className="empty">{t.loading}</p></main>
  }

  if (house === null) {
    return (
      <main className="content">
        <div className="empty">
          <HouseIcon width={48} height={48} />
          <p>{t.houseNotSetUp}</p>
          {canEdit && (
            <button type="button" className="button button-primary" onClick={() => setSettingUp(true)}>
              <PlusIcon width={18} height={18} /> {t.setUpHouse}
            </button>
          )}
        </div>
        {settingUp && (
          <PropertyDialog property={null} kind="HOUSE"
            onSaved={(saved) => { setSettingUp(false); setHouse(saved) }}
            onClose={() => { setSettingUp(false); reload() }} />
        )}
      </main>
    )
  }

  return <PropertyPage property={house} onChanged={reload} />
}
