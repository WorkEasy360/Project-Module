import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { assistantApi, type AssistantReply } from '../../api/assistant'
import { ApiError } from '../../api/client'
import { toUserMessage } from '../../api/errorMessage'
import { useIdentity } from '../../context/IdentityContext'
import { todayIso } from '../../utils/format'
import { Icon } from '../common/Icon'
import { RichText } from './RichText'
import { canListen, canSpeak, listen, speak, stopSpeaking, voiceCommand, type Recognition } from './voice'

/** How long a pause means the person has finished speaking. */
const PAUSE_MS = 1400
/** A voice chat pauses itself after this long without hearing anything. */
const SILENCE_LIMIT_MS = 120_000

export const ASSISTANT_NAME = 'WorkEasy Assistant'
/** Other parts of the app open the assistant with this event, e.g. the header's "Guide me". */
export const OPEN_ASSISTANT_EVENT = 'workeasy:open-assistant'

/** Opens the assistant from anywhere; with a message, sends it straight away. */
export function openAssistant(message?: string) {
  window.dispatchEvent(new CustomEvent(OPEN_ASSISTANT_EVENT, { detail: { message } }))
}

interface Line {
  from: 'you' | 'assistant' | 'note'
  text: string
}

interface Saved {
  conversationId: string | null
  lines: Line[]
  pending: string[]
}

const STORAGE_KEY = 'projectmodule.assistant'
const VOICE_KEY = 'projectmodule.assistant.voice'
const EMPTY: Saved = { conversationId: null, lines: [], pending: [] }

function load(): Saved {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as Saved) } : EMPTY
  } catch {
    return EMPTY
  }
}

function persist(saved: Saved) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(saved))
  } catch {
    // The conversation still works; it just will not survive a reload.
  }
}

function readVoicePreference(): boolean {
  try {
    return window.localStorage.getItem(VOICE_KEY) === 'on'
  } catch {
    return false
  }
}

const QUICK_ACTIONS: { label: string; message: string }[] = [
  { label: 'What should I work on today?', message: 'What should I work on today?' },
  { label: "What's overdue?", message: 'Which of my tasks are overdue or blocked?' },
  { label: 'Summarise my projects', message: 'Give me a short summary of how each of my projects is doing.' },
  { label: 'How do I invite my team?', message: 'How do I invite people to my workspace?' },
]

const HAND_OFF = 'Here is a job for you. Please take care of all of it:\n'

/**
 * The WorkEasy Assistant: chat, speak, or hand over a whole job. It answers from real data and
 * never changes anything until the person approves the exact changes it shows.
 */
export function AssistantPanel() {
  const navigate = useNavigate()
  const location = useLocation()
  const { identity } = useIdentity()
  const [open, setOpen] = useState(false)
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [saved, setSaved] = useState<Saved>(load)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [listening, setListening] = useState(false)
  const [voiceReplies, setVoiceReplies] = useState(readVoicePreference)
  const [voiceChat, setVoiceChat] = useState(false)
  const [speaking, setSpeaking] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const recognitionRef = useRef<Recognition | null>(null)
  const savedRef = useRef(saved)
  savedRef.current = saved
  // A voice chat runs across many async callbacks, so it reads the latest functions through refs.
  const voiceChatRef = useRef(false)
  const pauseTimerRef = useRef<number | undefined>(undefined)
  const lastHeardRef = useRef(0)
  const sendRef = useRef<(message: string) => Promise<void>>(async () => {})
  const decideRef = useRef<(approve: boolean) => Promise<void>>(async () => {})
  const listenTurnRef = useRef<() => void>(() => {})
  const endVoiceChatRef = useRef<(note?: string) => void>(() => {})

  useEffect(() => {
    assistantApi.status().then((s) => setEnabled(s.enabled)).catch(() => setEnabled(false))
  }, [])

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [saved, busy, open])

  useEffect(() => {
    if (!open) return
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  useEffect(() => () => {
    voiceChatRef.current = false
    window.clearTimeout(pauseTimerRef.current)
    recognitionRef.current?.stop()
    stopSpeaking()
  }, [])

  // Closing the panel ends a voice chat: the microphone is never on when the panel is not shown.
  useEffect(() => {
    if (!open) endVoiceChatRef.current()
  }, [open])

  function update(next: Saved) {
    savedRef.current = next
    setSaved(next)
    persist(next)
  }

  function apply(base: Saved, reply: AssistantReply) {
    const lines = [...base.lines, ...reply.messages.map((text): Line => ({ from: 'assistant', text }))]
    update({ conversationId: reply.conversationId, lines, pending: reply.pendingChanges })
    if (reply.navigateTo) navigate(reply.navigateTo)
    const spoken = reply.messages.join(' ')
    const count = reply.pendingChanges.length
    if (voiceChatRef.current) {
      const ask = count === 0 ? '' : count === 1 ? ' Should I make this change? Say approve or decline.' : ` Should I make these ${count} changes? Say approve or decline.`
      say(`${spoken}${ask}`.trim(), () => listenTurnRef.current())
    } else if (voiceReplies && spoken) {
      speak(count > 0 ? `${spoken} Please approve or decline the change.` : spoken)
    }
  }

  function fail(base: Saved, err: unknown) {
    const text = err instanceof ApiError && err.problem?.detail ? err.problem.detail : toUserMessage(err)
    update({ ...base, lines: [...base.lines, { from: 'note', text }] })
    if (voiceChatRef.current) say(text, () => listenTurnRef.current())
  }

  /** Speaks in a voice chat; the microphone is off meanwhile, so it never hears itself. */
  function say(text: string, then: () => void) {
    if (!text) {
      then()
      return
    }
    setSpeaking(true)
    speak(text, () => {
      setSpeaking(false)
      then()
    })
  }

  const send = useCallback(
    async (message: string) => {
      const text = message.trim()
      if (!text || busy) return
      const current = savedRef.current
      const base: Saved = { ...current, lines: [...current.lines, { from: 'you', text }], pending: [] }
      update(base)
      setDraft('')
      setBusy(true)
      try {
        const zone = Intl.DateTimeFormat().resolvedOptions().timeZone
        const context = `page ${location.pathname}; site ${window.location.origin}; local date ${todayIso()}; time zone ${zone}`
        apply(base, await assistantApi.chat({ conversationId: current.conversationId, message: text, context }))
      } catch (err) {
        fail(base, err)
      } finally {
        setBusy(false)
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [busy, location.pathname, voiceReplies],
  )

  // "Guide me" and other buttons open the assistant, optionally with a question.
  useEffect(() => {
    function onOpen(event: Event) {
      setOpen(true)
      const message = (event as CustomEvent<{ message?: string }>).detail?.message
      if (message) void send(message)
    }
    window.addEventListener(OPEN_ASSISTANT_EVENT, onOpen)
    return () => window.removeEventListener(OPEN_ASSISTANT_EVENT, onOpen)
  }, [send])

  async function decide(approve: boolean) {
    const current = savedRef.current
    if (!current.conversationId || busy) return
    const base: Saved = {
      ...current,
      pending: [],
      lines: [...current.lines, { from: 'note', text: approve ? 'You approved the change.' : 'You declined the change.' }],
    }
    update(base)
    setBusy(true)
    try {
      apply(base, await assistantApi.decide(current.conversationId, approve))
    } catch (err) {
      fail(base, err)
    } finally {
      setBusy(false)
    }
  }

  sendRef.current = send
  decideRef.current = decide

  function addNote(text: string) {
    update({ ...savedRef.current, lines: [...savedRef.current.lines, { from: 'note', text }] })
  }

  /**
   * A voice chat: one click and the conversation goes back and forth by voice. It listens, sends
   * what was said after a short pause, reads the answer aloud, then listens again, until the person
   * says "stop" (or clicks the microphone, or closes the panel).
   */
  function startVoiceChat() {
    // Browsers allow the microphone only on secure (https) pages.
    if (window.isSecureContext === false) {
      addNote('Voice chat needs a secure address that starts with https://. Open the app through its https address to talk to me; typing works here.')
      return
    }
    stopSpeaking()
    voiceChatRef.current = true
    setVoiceChat(true)
    lastHeardRef.current = Date.now()
    listenTurn()
  }

  function endVoiceChat(note?: string) {
    if (!voiceChatRef.current) return
    voiceChatRef.current = false
    setVoiceChat(false)
    window.clearTimeout(pauseTimerRef.current)
    const recognition = recognitionRef.current
    recognitionRef.current = null
    recognition?.stop()
    setListening(false)
    stopSpeaking()
    setSpeaking(false)
    setDraft('')
    if (note) addNote(note)
  }
  endVoiceChatRef.current = endVoiceChat

  function listenTurn() {
    if (!voiceChatRef.current || recognitionRef.current) return
    let heard = ''
    let handled = false
    let recognition: Recognition | null = null
    const finishTurn = () => {
      if (handled) return
      handled = true
      window.clearTimeout(pauseTimerRef.current)
      if (recognitionRef.current === recognition) recognitionRef.current = null
      recognition?.stop()
      setListening(false)
      if (heard) heardSomething(heard)
    }
    recognition = listen(
      (text) => {
        heard = text
        setDraft(text)
        lastHeardRef.current = Date.now()
        window.clearTimeout(pauseTimerRef.current)
        if (text) pauseTimerRef.current = window.setTimeout(finishTurn, PAUSE_MS)
      },
      (error) => {
        if (recognitionRef.current === recognition) recognitionRef.current = null
        if (handled || !voiceChatRef.current) return
        if (error === 'not-allowed' || error === 'service-not-allowed' || error === 'audio-capture') {
          handled = true
          endVoiceChat("I couldn't use the microphone. Allow microphone access for this site, then click the microphone again.")
          return
        }
        if (heard) {
          finishTurn()
          return
        }
        handled = true
        setListening(false)
        if (Date.now() - lastHeardRef.current > SILENCE_LIMIT_MS) {
          endVoiceChat('Voice chat paused because it was quiet for a while. Click the microphone to talk again.')
          return
        }
        // Browsers stop listening after a few silent seconds; carry on.
        window.setTimeout(() => listenTurnRef.current(), 250)
      },
      true,
    )
    if (!recognition) {
      endVoiceChat("I couldn't start the microphone in this browser.")
      return
    }
    recognitionRef.current = recognition
    setListening(true)
  }
  listenTurnRef.current = listenTurn

  function heardSomething(text: string) {
    setDraft('')
    const command = voiceCommand(text)
    if (command === 'stop') {
      update({ ...savedRef.current, lines: [...savedRef.current.lines, { from: 'you', text }] })
      endVoiceChat('Voice chat ended.')
      return
    }
    if (command && savedRef.current.pending.length > 0) {
      void decideRef.current(command === 'approve')
      return
    }
    void sendRef.current(text)
  }

  function toggleVoiceChat() {
    if (voiceChatRef.current) endVoiceChat('Voice chat ended.')
    else startVoiceChat()
  }

  function toggleVoiceReplies() {
    const next = !voiceReplies
    setVoiceReplies(next)
    if (!next) stopSpeaking()
    try {
      window.localStorage.setItem(VOICE_KEY, next ? 'on' : 'off')
    } catch {
      // Remembered for this visit only.
    }
  }

  function handOff() {
    setDraft(HAND_OFF)
    requestAnimationFrame(() => {
      const box = inputRef.current
      if (box) {
        box.focus()
        box.setSelectionRange(box.value.length, box.value.length)
      }
    })
  }

  const firstName = identity?.name?.split(' ')[0]

  if (!open) {
    return (
      <button type="button" className="assistant-launch" onClick={() => setOpen(true)} aria-label="Open the assistant" title={ASSISTANT_NAME}>
        <img src="/brand/assistant-avatar-sm.png" alt="" width={60} height={60} />
      </button>
    )
  }

  return (
    <section className="assistant-panel" role="dialog" aria-label={ASSISTANT_NAME}>
      <header className="assistant-head">
        <img className="assistant-avatar" src="/brand/assistant-avatar-sm.png" alt="" width={38} height={38} />
        <span className="assistant-title">
          <strong>{ASSISTANT_NAME}</strong>
          <span className={`assistant-status${enabled ? ' online' : ''}`}>
            {enabled === false
              ? 'Not set up yet'
              : busy
                ? 'Working on it…'
                : speaking
                  ? 'Speaking…'
                  : listening
                    ? 'Listening…'
                    : voiceChat
                      ? 'Voice chat on'
                      : 'Online'}
          </span>
        </span>
        <span className="assistant-head-actions">
          {canSpeak() && (
            <button
              type="button"
              className={`icon-btn${voiceReplies ? ' active' : ''}`}
              onClick={toggleVoiceReplies}
              aria-pressed={voiceReplies}
              aria-label={voiceReplies ? 'Stop reading replies aloud' : 'Read replies aloud'}
              title={voiceReplies ? 'Replies are read aloud' : 'Read replies aloud'}
            >
              <Icon name="speaker" size={16} />
            </button>
          )}
          {saved.lines.length > 0 && (
            <button type="button" className="icon-btn" onClick={() => update(EMPTY)} disabled={busy} aria-label="Start a new chat" title="New chat">
              <Icon name="plus" size={16} />
            </button>
          )}
          <button type="button" className="icon-btn" onClick={() => setOpen(false)} aria-label="Close the assistant">
            <Icon name="close" size={16} />
          </button>
        </span>
      </header>

      <div className="assistant-body" ref={listRef}>
        {enabled === false && (
          <p className="assistant-note">
            The assistant isn't set up yet. An administrator needs to add an Anthropic API key to the server.
          </p>
        )}
        {enabled !== false && saved.lines.length === 0 && (
          <div className="assistant-welcome">
            <img src="/brand/assistant-avatar.png" alt="" width={84} height={84} />
            <h2>Hi{firstName ? ` ${firstName}` : ''}, how can I help?</h2>
            <p>Ask about your work, tell me what to update, or hand me a whole job. I'll show you every change before I make it.</p>
            <div className="assistant-suggestions">
              {QUICK_ACTIONS.map((action) => (
                <button key={action.label} type="button" className="assistant-chip" onClick={() => send(action.message)} disabled={!enabled}>
                  {action.label}
                </button>
              ))}
              <button type="button" className="assistant-chip assistant-chip-strong" onClick={handOff} disabled={!enabled}>
                <Icon name="tasks" size={14} /> Hand off a job
              </button>
            </div>
          </div>
        )}
        {saved.lines.map((line, i) =>
          line.from === 'assistant' ? (
            <div key={i} className="assistant-row">
              <img className="assistant-row-avatar" src="/brand/assistant-avatar-sm.png" alt="" width={26} height={26} />
              <div className="assistant-line from-assistant">
                <RichText text={line.text} />
              </div>
            </div>
          ) : (
            <p key={i} className={`assistant-line from-${line.from}`}>
              {line.text}
            </p>
          ),
        )}
        {saved.pending.length > 0 && (
          <div className="assistant-approval" role="group" aria-label="Change waiting for your approval">
            <strong>
              <Icon name="check" size={14} /> Approve {saved.pending.length === 1 ? 'this change' : `these ${saved.pending.length} changes`}?
            </strong>
            <ul>
              {saved.pending.map((change, i) => (
                <li key={i}>{change}</li>
              ))}
            </ul>
            <div className="assistant-approval-actions">
              <button type="button" className="btn btn-sm" onClick={() => decide(false)} disabled={busy}>
                Decline
              </button>
              <button type="button" className="btn btn-sm btn-primary" onClick={() => decide(true)} disabled={busy}>
                Approve {saved.pending.length > 1 ? 'all' : ''}
              </button>
            </div>
          </div>
        )}
        {busy && (
          <div className="assistant-row">
            <img className="assistant-row-avatar" src="/brand/assistant-avatar-sm.png" alt="" width={26} height={26} />
            <div className="assistant-line from-assistant assistant-typing" aria-label="The assistant is working">
              <span />
              <span />
              <span />
            </div>
          </div>
        )}
      </div>

      {voiceChat && (
        <div className={`assistant-voicebar${listening ? ' listening' : ''}`} role="status">
          <span className="assistant-voicebar-dot" aria-hidden="true" />
          <span>
            {speaking ? 'Speaking…' : busy ? 'Working on it…' : listening ? 'Listening… just talk.' : 'Voice chat on'} Say “stop” to end.
          </span>
          <button type="button" className="assistant-voicebar-end" onClick={() => endVoiceChat('Voice chat ended.')}>
            End
          </button>
        </div>
      )}

      <form
        className="assistant-input"
        onSubmit={(e) => {
          e.preventDefault()
          send(draft)
        }}
      >
        <label className="sr-only" htmlFor="assistant-message">
          Message the assistant
        </label>
        <textarea
          id="assistant-message"
          ref={inputRef}
          rows={2}
          value={draft}
          maxLength={4000}
          placeholder={enabled === false ? 'Not available yet' : listening ? 'Listening…' : 'Ask, or describe a job for me…'}
          disabled={enabled === false}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send(draft)
            }
          }}
        />
        {canListen() && (
          <button
            type="button"
            className={`assistant-mic${voiceChat ? ' listening' : ''}`}
            onClick={toggleVoiceChat}
            disabled={enabled === false || (busy && !voiceChat)}
            aria-pressed={voiceChat}
            aria-label={voiceChat ? 'End voice chat' : 'Speak to the assistant'}
            title={voiceChat ? 'End voice chat' : 'Start a voice chat: just talk, and say “stop” to end'}
          >
            <Icon name="mic" size={17} />
          </button>
        )}
        <button
          type="submit"
          className="assistant-send"
          disabled={busy || !draft.trim() || enabled === false}
          aria-label="Send"
        >
          <Icon name="arrowRight" size={17} />
        </button>
      </form>
    </section>
  )
}
