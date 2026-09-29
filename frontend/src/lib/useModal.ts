import { useEffect, useRef } from 'react'

/** Open a native <dialog> as a modal while the component is mounted. */
export function useModal() {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])
  return ref
}
