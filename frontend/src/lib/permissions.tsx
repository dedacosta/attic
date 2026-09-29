import { createContext, useContext, type ReactNode } from 'react'
import type { Role } from '../api/types'

interface Permissions {
  /** Administrators edit everything; users can only look (apart from their own heir card) */
  canEdit: boolean
  /** May open account management (administrators and super-administrators) */
  isAdmin: boolean
  /** May delete accounts, change roles and manage administrators */
  isSuperAdmin: boolean
  /** The heir linked to the account; a user sees only that heir and their documents */
  ownHeirId: string | null
  /** Whether the Heirs and Documents tabs exist for this account */
  seesHeirs: boolean
}

const PermissionsContext = createContext<Permissions>({
  canEdit: false,
  isAdmin: false,
  isSuperAdmin: false,
  ownHeirId: null,
  seesHeirs: false,
})

export function PermissionsProvider(props: { role: Role | null; heirId: string | null; children: ReactNode }) {
  const isSuperAdmin = props.role === 'SUPER_ADMIN'
  const isAdmin = isSuperAdmin || props.role === 'ADMIN'
  const value = {
    canEdit: isAdmin,
    isAdmin,
    isSuperAdmin,
    ownHeirId: props.heirId,
    seesHeirs: isAdmin || props.heirId !== null,
  }
  return <PermissionsContext.Provider value={value}>{props.children}</PermissionsContext.Provider>
}

export function usePermissions(): Permissions {
  return useContext(PermissionsContext)
}
