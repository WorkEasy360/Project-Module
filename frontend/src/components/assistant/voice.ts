/**
 * Speaking to the assistant and hearing it answer, through the browser's own speech features.
 * Both are optional: Chrome and Edge support listening; most browsers support speaking. Nothing is
 * sent anywhere except the recognised text, which goes to the assistant like typed text.
 */

interface RecognitionResultEvent {
  results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }>
}

export interface Recognition {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((event: RecognitionResultEvent) => void) | null
  onend: (() => void) | null
  onerror: ((event: { error: string }) => void) | null
  start: () => void
  stop: () => void
}

type RecognitionConstructor = new () => Recognition

function recognitionConstructor(): RecognitionConstructor | null {
  const w = window as unknown as { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

export function canListen(): boolean {
  return recognitionConstructor() !== null
}

export function canSpeak(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined'
}

/**
 * Listens for one thing the person says. Calls onText with what was heard so far, and onDone once
 * when listening ends, with the error if there was one. With continuous, it keeps listening through
 * short pauses; the caller decides when the person has finished.
 */
export function listen(
  onText: (text: string, final: boolean) => void,
  onDone: (error?: string) => void,
  continuous = false,
): Recognition | null {
  const Ctor = recognitionConstructor()
  if (!Ctor) return null
  const recognition = new Ctor()
  recognition.lang = navigator.language || 'en-US'
  recognition.interimResults = true
  recognition.continuous = continuous
  let failure: string | undefined
  let finished = false
  recognition.onresult = (event) => {
    let text = ''
    let final = false
    for (let i = 0; i < event.results.length; i++) {
      text += event.results[i][0].transcript
      final = final || event.results[i].isFinal
    }
    onText(text.trim(), final)
  }
  // Browsers report an error and then end; tell the caller once.
  recognition.onerror = (event) => {
    failure = event.error
  }
  recognition.onend = () => {
    if (finished) return
    finished = true
    onDone(failure)
  }
  try {
    recognition.start()
  } catch {
    return null
  }
  return recognition
}

/** What the reply sounds like without the formatting marks the assistant may use. */
export function plainForSpeech(text: string): string {
  return text
    .replace(/\*\*/g, '')
    .replace(/^\s*[-*•–]\s+/gm, '')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * Reads text aloud, sentence by sentence (some browsers cut long speech short), and calls onEnd
 * once when it has finished, also if the browser never reports the end.
 */
export function speak(text: string, onEnd?: () => void): void {
  let ended = false
  const finish = () => {
    if (ended) return
    ended = true
    onEnd?.()
  }
  const plain = plainForSpeech(text)
  if (!canSpeak() || !plain) {
    finish()
    return
  }
  window.speechSynthesis.cancel()
  const sentences = plain.match(/[^.!?]+[.!?]*/g)?.map((s) => s.trim()).filter(Boolean) ?? [plain]
  // A safety net: about 13 characters a second, plus a little.
  const fallback = window.setTimeout(finish, 3000 + plain.length * 80)
  sentences.forEach((sentence, i) => {
    const utterance = new SpeechSynthesisUtterance(sentence)
    utterance.lang = navigator.language || 'en-US'
    if (i === sentences.length - 1) {
      utterance.onend = () => {
        window.clearTimeout(fallback)
        finish()
      }
      utterance.onerror = () => {
        window.clearTimeout(fallback)
        finish()
      }
    }
    window.speechSynthesis.speak(utterance)
  })
}

export function stopSpeaking(): void {
  if (canSpeak()) window.speechSynthesis.cancel()
}

function normalise(text: string): string {
  return text.toLowerCase().replace(/[^\p{L}\p{N}' ]+/gu, ' ').replace(/\s+/g, ' ').trim()
}

const POLITE = "(?: please| now| thanks| thank you| ji)?"
const STOP = new RegExp(
  `^(?:ok |okay )?(?:stop|stop listening|stop talking|stop voice chat|that's all|thats all|that is all|that's it|end|end chat|end the chat|end conversation|end the conversation|end voice chat|goodbye|good bye|bye|bye bye|exit|quit|bas|ruko|ruk jao|band karo)${POLITE}$`,
)
const APPROVE = new RegExp(
  `^(?:yes|yeah|yep|yup|approve|approved|approve it|approve all|approve them|go ahead|do it|confirm|sure|ok|okay|ok go ahead|okay go ahead|ok do it|okay do it|haan|haan ji|ha|theek hai|thik hai|kar do)${POLITE}$`,
)
const DECLINE = new RegExp(
  `^(?:no|nope|decline|declined|decline it|cancel|cancel it|don't|dont|do not|reject|don't do it|nahi|nahin|mat karo)${POLITE}$`,
)

/** What a spoken phrase means in a voice chat, when it is a command rather than a question. */
export function voiceCommand(text: string): 'stop' | 'approve' | 'decline' | null {
  const said = normalise(text)
  if (STOP.test(said)) return 'stop'
  if (APPROVE.test(said)) return 'approve'
  if (DECLINE.test(said)) return 'decline'
  return null
}
