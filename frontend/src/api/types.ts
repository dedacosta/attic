export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'USER'

export interface UserAccount {
  username: string
  role: Role
  createdAt: string
  /** The heir this account belongs to; users see only that heir */
  heirId: string | null
  email: string | null
  phone: string | null
}

/** An invitation to register that has not been used yet */
export interface Invitation {
  id: string
  role: Role
  /** The heir the new account will belong to */
  heirId: string | null
  createdBy: string
  createdAt: string
  expiresAt: string
  /** Only in the answer to creating the invitation: it is not kept on the server */
  token: string | null
}

export interface OwnAccount {
  username: string
  role: Role
  email: string | null
  phone: string | null
}

export interface Session {
  authenticated: boolean
  username: string | null
  role: Role | null
  heirId: string | null
  /** No account exists yet: the first one has to be created */
  setupRequired: boolean
}

export interface Item {
  id: string
  name: string
  quantity: number
  date: string | null
  location: string | null
  existent: boolean
  valueEur: number
  owner: string
  comments: string | null
  pictureUrl: string | null
  thumbnailUrl: string | null
}

export type ItemInput = Omit<Item, 'id' | 'pictureUrl' | 'thumbnailUrl'>

export interface HeirDocument {
  id: string
  heirId: string
  /** Name of the heir, for display */
  heir: string
  type: string
  validUntil: string | null
  comments: string | null
  pictureUrl: string | null
  thumbnailUrl: string | null
}

export type DocumentInput = Omit<HeirDocument, 'id' | 'heir' | 'pictureUrl' | 'thumbnailUrl'>

export interface Heir {
  id: string
  name: string
  birthDate: string | null
  address: string | null
  /** Parents' names, one per line */
  filiation: string | null
  sex: string | null
  /** Share of the heritage as a fraction, e.g. "1/3" */
  heritageShare: string | null
  comments: string | null
  /** Set by the server; null for heirs added before timestamps were recorded */
  createdAt: string | null
  updatedAt: string | null
}

export type HeirInput = Omit<Heir, 'id' | 'createdAt' | 'updatedAt'>

export type PictureChange = { kind: 'keep' } | { kind: 'replace'; file: File } | { kind: 'remove' }
