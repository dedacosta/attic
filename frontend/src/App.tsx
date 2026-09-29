import { useCallback, useEffect, useState } from 'react'
import { api, apiErrorMessage, SIGNED_OUT_EVENT } from './api/api'
import AccountMenu from './components/AccountMenu'
import Footer from './components/Footer'
import LanguageSwitch from './components/LanguageSwitch'
import SignInScreen from './components/SignInScreen'
import { BoxIcon, HouseIcon, IdCardIcon, KeyIcon, HeirsIcon } from './components/icons'
import { useI18n } from './i18n'
import { PermissionsProvider, usePermissions } from './lib/permissions'
import DocumentsView from './views/DocumentsView'
import InventoryView from './views/InventoryView'
import HeirsView from './views/HeirsView'
import UsersView from './views/UsersView'
import type { Session } from './api/types'

type View = 'inventory' | 'heirs' | 'documents' | 'users'

// The view lives in the URL hash (#/documents) so that reloading keeps it
function viewFromHash(): View {
  switch (window.location.hash) {
    case '#/heirs':
      return 'heirs'
    case '#/documents':
      return 'documents'
    case '#/users':
      return 'users'
    default:
      return 'inventory'
  }
}

export default function App() {
  const { t } = useI18n()
  const [session, setSession] = useState<Session | null>(null)
  const [sessionError, setSessionError] = useState<unknown>(null)

  const refreshSession = useCallback(async () => {
    try {
      setSession(await api.session())
      setSessionError(null)
    } catch (e) {
      setSessionError(e)
    }
  }, [])

  useEffect(() => {
    refreshSession()
    // Any request answered with 401 means the session ended: ask who is signed in again
    window.addEventListener(SIGNED_OUT_EVENT, refreshSession)
    return () => window.removeEventListener(SIGNED_OUT_EVENT, refreshSession)
  }, [refreshSession])

  if (session === null) {
    return (
      <div className="sign-in-page">
        {sessionError !== null ? (
          <div className="banner" role="alert">
            {apiErrorMessage(sessionError, t)}{' '}
            <button type="button" className="link" onClick={refreshSession}>{t.tryAgain}</button>
          </div>
        ) : (
          <p className="empty">{t.loading}</p>
        )}
      </div>
    )
  }

  if (!session.authenticated || session.setupRequired) {
    return <SignInScreen setup={session.setupRequired} onSignedIn={refreshSession} />
  }

  return (
    <PermissionsProvider role={session.role} heirId={session.heirId}>
      {/* A new user, role or linked heir (e.g. changed by an administrator) starts the screens afresh */}
      <SignedInApp key={`${session.username}:${session.role}:${session.heirId}`} username={session.username ?? ''}
        onSignedOut={refreshSession} />
    </PermissionsProvider>
  )
}

function SignedInApp({ username, onSignedOut }: { username: string; onSignedOut: () => void }) {
  const { t } = useI18n()
  const { seesHeirs, isAdmin } = usePermissions()
  const [hashView, setView] = useState(viewFromHash)
  // Tabs this account has: users without a linked heir have no Heirs and Documents tabs, and
  // only administrators have the Users tab
  const allowed = (id: View) => id === 'inventory' || (id === 'users' ? isAdmin : seesHeirs)
  const view: View = allowed(hashView) ? hashView : 'inventory'

  useEffect(() => {
    const onHashChange = () => setView(viewFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const tabs = ([
    { id: 'inventory', href: '#/', label: t.tabInventory, Icon: BoxIcon },
    { id: 'heirs', href: '#/heirs', label: t.tabHeirs, Icon: HeirsIcon },
    { id: 'documents', href: '#/documents', label: t.tabDocuments, Icon: IdCardIcon },
    { id: 'users', href: '#/users', label: t.users, Icon: KeyIcon },
  ] as const).filter((tab) => allowed(tab.id))

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brand">
            <HouseIcon width={26} height={26} />
            <span>Attic</span>
          </div>
          <nav className="tabs" aria-label={t.sections}>
            {tabs.map(({ id, href, label, Icon }) => (
              <a key={id} href={href} className="tab" aria-current={view === id ? 'page' : undefined}>
                <Icon width={18} height={18} /> {label}
              </a>
            ))}
          </nav>
          <LanguageSwitch />
          <AccountMenu username={username} onSignedOut={onSignedOut} />
        </div>
      </header>

      {view === 'inventory' && <InventoryView />}
      {view === 'heirs' && <HeirsView />}
      {view === 'documents' && <DocumentsView />}
      {view === 'users' && <UsersView currentUser={username} />}

      <Footer />
    </div>
  )
}
