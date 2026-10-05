import { useCallback, useEffect, useState } from 'react'
import { api, apiErrorMessage, SIGNED_OUT_EVENT } from './api/api'
import AccountMenu from './components/AccountMenu'
import Footer from './components/Footer'
import LanguageSwitch from './components/LanguageSwitch'
import SignInScreen from './components/SignInScreen'
import { BoxIcon, CoinsIcon, HammerIcon, HouseIcon, IdCardIcon, KeyIcon, HeirsIcon, LandIcon } from './components/icons'
import { useI18n } from './i18n'
import { PermissionsProvider, usePermissions } from './lib/permissions'
import ContributionsView from './views/ContributionsView'
import DocumentsView from './views/DocumentsView'
import InventoryView from './views/InventoryView'
import RenovationsView from './views/RenovationsView'
import HeirsView from './views/HeirsView'
import HouseView from './views/HouseView'
import LandView from './views/LandView'
import UsersView from './views/UsersView'
import type { Session } from './api/types'

type View = 'inventory' | 'heirs' | 'documents' | 'house' | 'land' | 'contributions' | 'renovations' | 'users'

// The view lives in the URL hash (#/documents) so that reloading keeps it
function viewFromHash(): View {
  // A land parcel's page is #/land/<id>
  if (window.location.hash.startsWith('#/land')) {
    return 'land'
  }
  switch (window.location.hash) {
    case '#/inventory':
      return 'inventory'
    case '#/heirs':
      return 'heirs'
    case '#/documents':
      return 'documents'
    case '#/contributions':
      return 'contributions'
    case '#/renovations':
      return 'renovations'
    case '#/users':
      return 'users'
    // The house is the start page (#/ or #/house)
    default:
      return 'house'
  }
}

const REGISTER_PREFIX = '#/register/'

/** The token of an invitation link (#/register/<token>); in the hash, it never reaches the server's logs */
function invitationFromHash(): string | null {
  const hash = window.location.hash
  return hash.startsWith(REGISTER_PREFIX) ? decodeURIComponent(hash.slice(REGISTER_PREFIX.length)) || null : null
}

export default function App() {
  const { t } = useI18n()
  const [session, setSession] = useState<Session | null>(null)
  const [sessionError, setSessionError] = useState<unknown>(null)
  const [invitation, setInvitation] = useState(invitationFromHash)

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
    return <SignInScreen setup={session.setupRequired} invitation={session.setupRequired ? null : invitation}
      onSignedIn={() => {
        if (invitation !== null) {
          // The token is used up: leave the invitation link
          window.history.replaceState(null, '', window.location.pathname + window.location.search)
          setInvitation(null)
        }
        refreshSession()
      }} />
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
  // only administrators have the Users tab. Everybody sees the rest.
  const allowed = (id: View) => (id === 'users' ? isAdmin : id === 'heirs' || id === 'documents' ? seesHeirs : true)
  const view: View = allowed(hashView) ? hashView : 'house'

  useEffect(() => {
    const onHashChange = () => setView(viewFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const tabs = ([
    { id: 'house', href: '#/', label: t.tabHouse, Icon: HouseIcon },
    { id: 'land', href: '#/land', label: t.tabLand, Icon: LandIcon },
    { id: 'heirs', href: '#/heirs', label: t.tabHeirs, Icon: HeirsIcon },
    { id: 'inventory', href: '#/inventory', label: t.tabInventory, Icon: BoxIcon },
    { id: 'documents', href: '#/documents', label: t.tabDocuments, Icon: IdCardIcon },
    { id: 'contributions', href: '#/contributions', label: t.tabContributions, Icon: CoinsIcon },
    { id: 'renovations', href: '#/renovations', label: t.tabRenovations, Icon: HammerIcon },
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
      {view === 'house' && <HouseView />}
      {view === 'land' && <LandView />}
      {view === 'contributions' && <ContributionsView />}
      {view === 'renovations' && <RenovationsView />}
      {view === 'users' && <UsersView currentUser={username} />}

      <Footer />
    </div>
  )
}
