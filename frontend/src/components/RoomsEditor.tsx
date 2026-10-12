import { useI18n } from '../i18n'
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon, TrashIcon } from './icons'

/** A room in the list of a house: the id of a room that exists, null for one being added */
export interface RoomName {
  id: string | null
  title: string
  /** What helps to tell the room from the others; empty for none */
  description: string
}

interface Props {
  rooms: RoomName[]
  onChange: (rooms: RoomName[]) => void
}

/** The rooms of a house, in order: a name and a description each. The inventory of the house is kept room by room. */
export default function RoomsEditor({ rooms, onChange }: Props) {
  const { t } = useI18n()

  const set = (index: number, change: Partial<RoomName>) =>
    onChange(rooms.map((room, i) => (i === index ? { ...room, ...change } : room)))

  function move(index: number, step: number) {
    const moved = [...rooms]
    ;[moved[index], moved[index + step]] = [moved[index + step], moved[index]]
    onChange(moved)
  }

  return (
    <div className="details-editor field-wide">
      <span className="details-editor-title">{t.houseRooms} <small>{t.optional}</small></span>
      {rooms.map((room, index) => (
        <div key={index} className="rooms-row">
          <input value={room.title} placeholder={t.roomTitlePlaceholder} aria-label={t.name}
            onChange={(e) => set(index, { title: e.target.value })} />
          <input value={room.description} placeholder={t.description} aria-label={t.description}
            onChange={(e) => set(index, { description: e.target.value })} />
          <button type="button" className="icon-button details-move" onClick={() => move(index, -1)}
            disabled={index === 0} aria-label={t.moveUp}>
            <ChevronLeftIcon width={16} height={16} />
          </button>
          <button type="button" className="icon-button details-move" onClick={() => move(index, 1)}
            disabled={index === rooms.length - 1} aria-label={t.moveDown}>
            <ChevronRightIcon width={16} height={16} />
          </button>
          <button type="button" className="icon-button" onClick={() => onChange(rooms.filter((_, i) => i !== index))}
            aria-label={t.removeRoom}>
            <TrashIcon width={16} height={16} />
          </button>
        </div>
      ))}
      {rooms.some((room) => room.id !== null) && <p className="hint">{t.roomRemovalHint}</p>}
      <button type="button" className="button button-quiet" onClick={() => onChange([...rooms, { id: null, title: '', description: '' }])}>
        <PlusIcon width={16} height={16} /> {t.addRoom}
      </button>
    </div>
  )
}
