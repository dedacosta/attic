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

export interface CatalogItem {
  id: string
  name: string
  quantity: number
  date: string | null
  /** The building the item is in, and its room there */
  propertyId: string | null
  roomId: string | null
  existent: boolean
  valueEur: number
  owner: string
  comments: string | null
  /** Cover first */
  pictures: Picture[]
}

export type CatalogItemInput = Omit<CatalogItem, 'id' | 'pictures'>

/** A line of the inventory, in a room or in a list of the house (other items, missing items) */
export interface InventoryItem {
  description: string
  quantity: number
  /** What the line is worth, all of its quantity together */
  valueEur: number
}

/** A room of a house in the inventory, the formal list of what matters there, with its items in order */
export interface InventoryRoom {
  id: string
  /** The building the room is in; it never changes */
  propertyId: string
  /** The name of the room, entered on the house and shown on the screen */
  title: string
  /** Entered on the house with the name, to tell the rooms apart */
  description: string | null
  /** The title the room has in the PDF; null to use its name */
  pdfTitle: string | null
  comments: string | null
  items: InventoryItem[]
  /** Cover first */
  pictures: Picture[]
}

export type InventoryRoomInput = Omit<InventoryRoom, 'id' | 'pictures' | 'description'>

export interface HeirDocument {
  id: string
  /** Null for a document without heir */
  heirId: string | null
  /** Name of the heir, for display; null without heir */
  heir: string | null
  type: string
  validUntil: string | null
  comments: string | null
  /** Cover first; a file is a photo or a PDF */
  pictures: Picture[]
}

export type DocumentInput = Omit<HeirDocument, 'id' | 'heir' | 'pictures'>

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

/** A file or subfolder of the Folder tab, as put on disk by hand; path is relative to the folder, with "/" */
export interface FolderEntry {
  name: string
  path: string
  folder: boolean
  /** Bytes; 0 for a folder */
  size: number
  modified: string
  children: FolderEntry[]
}

/** A renovation of the house, its cost shared by those who pay the contribution in its year */
export interface Renovation {
  id: string
  year: number
  title: string
  description: string | null
  costEur: number
  comment: string | null
  /** What the renovation costs, in order; their total is its cost */
  expenses: RenovationExpense[]
  /**
   * The heirs who pay a part that year, as for the contribution, one per line, oldest first, with
   * whether they paid it or did workmanship; and anyone else ticked off
   */
  lines: {
    heirId: string
    heir: string
    deceased: boolean
    dueEur: number | null
    paid: boolean
    workmanship: boolean
  }[]
  paidEur: number
  missingEur: number
}

export type RenovationInput = Pick<Renovation, 'year' | 'title' | 'description'>

export type ExpenseKind = 'MATERIALS' | 'WORKMANSHIP' | 'OTHER'

export const EXPENSE_KINDS: ExpenseKind[] = ['MATERIALS', 'WORKMANSHIP', 'OTHER']

/** An expense of a renovation, and whether it is paid */
export interface RenovationExpense {
  description: string
  kind: ExpenseKind
  amountEur: number
  paid: boolean
}


/** A stored photo of an item or document */
export interface Picture {
  id: string
  url: string
  /** Null for formats without thumbnail (HEIC, PDF) */
  thumbnailUrl: string | null
  contentType: string
}

/** A photo in a form: already stored, or chosen and not uploaded yet */
export type PhotoEntry = { kind: 'stored'; picture: Picture } | { kind: 'new'; key: string; file: File }

export const storedPhotos = (pictures: Picture[]): PhotoEntry[] =>
  pictures.map((picture) => ({ kind: 'stored', picture }))

export type PropertyKind = 'BUILDING' | 'LAND'

/** One detail of a property, e.g. "Artigo matricial" → "1234" */
export interface Fact {
  label: string
  value: string
}

/** An official document of a building or a land parcel; its files are images or PDF */
export interface PropertyDocument {
  id: string
  propertyId: string
  type: string
  date: string | null
  notes: string | null
  files: Picture[]
}

export type PropertyDocumentInput = Omit<PropertyDocument, 'id' | 'propertyId' | 'files'>

/** What a building and a land parcel have in common */
interface PropertyBase {
  id: string
  name: string
  /** For land: its location, in free text */
  address: string | null
  /** Estate value; null when it has none */
  valueEur: number | null
  comments: string | null
  facts: Fact[]
  /** Cover first */
  pictures: Picture[]
  documents: PropertyDocument[]
}

export interface Building extends PropertyBase {
  kind: 'BUILDING'
}

export interface Land extends PropertyBase {
  kind: 'LAND'
}

/** A property of the estate: a building or a land parcel */
export type Property = Building | Land

/** The estate: its buildings and its land parcels, each list by name */
export interface Estate {
  buildings: Building[]
  lands: Land[]
}

export type PropertyInput = Omit<PropertyBase, 'id' | 'pictures' | 'documents'> & { kind: PropertyKind }
