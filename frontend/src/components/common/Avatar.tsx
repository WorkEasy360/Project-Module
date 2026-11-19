import { useEffect, useState } from 'react'
import { fetchObjectUrl } from '../../api/client'
import { usePeople } from '../../context/PeopleContext'
import { initials } from '../../utils/format'

/**
 * Photos already loaded this visit, by person and photo version, so each is fetched once.
 * The photo needs the session token, so it is fetched and shown from a local address.
 */
const cache = new Map<string, Promise<string>>()

function photoUrl(userId: string, version: string): Promise<string> {
  const key = `${userId}:${version}`
  let url = cache.get(key)
  if (!url) {
    url = fetchObjectUrl(`/workspace/people/${userId}/avatar?v=${encodeURIComponent(version)}`)
    url.catch(() => cache.delete(key))
    cache.set(key, url)
  }
  return url
}

/**
 * A person's profile photo, or their initials in a green circle when they have none. Pass the
 * class of the circle it replaces so it keeps that circle's size and place.
 */
export function Avatar({ userId, name, className = 'ops-avatar' }: { userId: string | null | undefined; name: string; className?: string }) {
  const { people } = usePeople()
  const version = userId ? people.find((p) => p.id === userId)?.avatarVersion ?? null : null
  const [src, setSrc] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    if (!userId || !version) {
      setSrc(null)
      return
    }
    photoUrl(userId, version)
      .then((url) => alive && setSrc(url))
      .catch(() => alive && setSrc(null))
    return () => {
      alive = false
    }
  }, [userId, version])

  return (
    <span className={`${className}${src ? ' has-photo' : ''}`} aria-hidden="true">
      {src ? <img src={src} alt="" /> : initials(name)}
    </span>
  )
}
