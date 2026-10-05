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
  /** Content type of the picture: an image, or application/pdf for a PDF */
  pictureType: string | null
}

export type DocumentInput = Omit<HeirDocument, 'id' | 'heir' | 'pictureUrl' | 'thumbnailUrl' | 'pictureType'>

export interface Heir {
  id: string
  name: string
  birthDate: string | null
  address: string | null
  /** Parents' names, one per line */
  filiation: string | null
  sex: string | null
  /** Share of the heritage as a fraction, e.g. "1/3" */
  /** Entered for heirs without a parent; children receive theirs from their parent */
  heritageShare: string | null
  /** What the heir receives: their own share, or their part of a deceased parent's share */
  calculatedShare: string | null
  deceased: boolean
  deathDate: string | null
  comments: string | null
  /** The heir this one is a child of */
  parentId: string | null
  /** Set by the server; null for heirs added before timestamps were recorded */
  createdAt: string | null
  updatedAt: string | null
}

export type HeirInput = Omit<Heir, 'id' | 'calculatedShare' | 'createdAt' | 'updatedAt'>

/** A year of the annual contribution */
export interface ContributionYear {
  year: number
  /**
   * Every heir. Those who pay are alive that year with no living parent above them; the others
   * (not born yet, deceased, or a parent still alive) have a dash.
   */
  lines: {
    heirId: string
    heir: string
    deceased: boolean
    pays: boolean
    /** Part of the yearly amount the heir owes: 1, or e.g. 0.5 when two children share a parent's */
    portion: number | null
    amountEur: number | null
  }[]
  totalEur: number
  comment: string | null
}

/** A renovation of the house, its cost shared by those who pay the contribution in its year */
export interface Renovation {
  id: string
  year: number
  title: string
  description: string | null
  costEur: number
  comment: string | null
  /** Those who pay that year, with their part of the cost, and anyone else ticked off as paid */
  lines: { heirId: string; heir: string; deceased: boolean; dueEur: number | null; paid: boolean }[]
  paidEur: number
  missingEur: number
}

export type RenovationInput = Pick<Renovation, 'year' | 'title' | 'description' | 'costEur'>

export type PictureChange = { kind: 'keep' } | { kind: 'replace'; file: File } | { kind: 'remove' }
