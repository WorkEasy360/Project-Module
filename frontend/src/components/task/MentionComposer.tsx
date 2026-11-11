import { useMemo, useRef, useState } from 'react'
import { InlineError } from '../common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'

export interface Mentionable {
  value: string
  label: string
}

/** The partly typed name after an "@" just before the caret, if there is one. */
function activeMention(text: string, caret: number): { start: number; query: string } | null {
  const match = /(^|\s)@([^\s@]*)$/.exec(text.slice(0, caret))
  return match ? { start: caret - match[2].length - 1, query: match[2].toLowerCase() } : null
}

/**
 * Write a comment. Typing "@" offers the people on the project; choosing one inserts their name and
 * tells them when the comment is posted. Only people still named in the text are told.
 */
export function MentionComposer({
  id,
  label,
  people,
  submitLabel = 'Comment',
  onSubmit,
}: {
  id: string
  label: string
  people: Mentionable[]
  submitLabel?: string
  onSubmit: (body: string, mentions: string[]) => Promise<void>
}) {
  const [body, setBody] = useState('')
  const [caret, setCaret] = useState(0)
  const [chosen, setChosen] = useState<Mentionable[]>([])
  const [error, setError] = useState<string | null>(null)
  const [posting, setPosting] = useState(false)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  const mention = useMemo(() => activeMention(body, caret), [body, caret])
  const suggestions = mention
    ? people.filter((person) => person.label.toLowerCase().includes(mention.query)).slice(0, 6)
    : []

  function choose(person: Mentionable) {
    if (!mention) return
    const before = body.slice(0, mention.start)
    const after = body.slice(caret)
    const inserted = `@${person.label} `
    setBody(before + inserted + after)
    setChosen((current) => (current.some((c) => c.value === person.value) ? current : [...current, person]))
    const position = before.length + inserted.length
    setCaret(position)
    requestAnimationFrame(() => {
      inputRef.current?.focus()
      inputRef.current?.setSelectionRange(position, position)
    })
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!body.trim()) return
    setPosting(true)
    setError(null)
    try {
      const mentions = chosen.filter((person) => body.includes(`@${person.label}`)).map((person) => person.value)
      await onSubmit(body, mentions)
      setBody('')
      setChosen([])
      setCaret(0)
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setPosting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="composer">
      {error && <InlineError message={error} />}
      <label className="sr-only" htmlFor={id}>
        {label}
      </label>
      <div className="composer-field">
        <textarea
          id={id}
          ref={inputRef}
          value={body}
          rows={3}
          placeholder="Write a comment. Type @ to mention someone."
          onChange={(e) => {
            setBody(e.target.value)
            setCaret(e.target.selectionStart)
          }}
          onSelect={(e) => setCaret(e.currentTarget.selectionStart)}
        />
        {suggestions.length > 0 && (
          <ul className="composer-mentions" role="listbox" aria-label="People to mention">
            {suggestions.map((person) => (
              <li key={person.value} role="option" aria-selected="false">
                <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => choose(person)}>
                  {person.label}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary btn-sm" disabled={posting || !body.trim()}>
          {posting ? 'Posting…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
