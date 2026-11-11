import { Fragment, type ReactNode } from 'react'

/** **bold** inside a line, as React nodes: never HTML, so text from records cannot inject markup. */
function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') && part.length > 4 ? <strong key={i}>{part.slice(2, -2)}</strong> : (
      <Fragment key={i}>{part}</Fragment>
    ),
  )
}

/** The little formatting the assistant uses: paragraphs, bulleted lists and bold. */
export function RichText({ text }: { text: string }) {
  const blocks: ReactNode[] = []
  let items: string[] = []
  let paragraph: string[] = []

  const flushList = () => {
    if (items.length) {
      blocks.push(
        <ul key={blocks.length}>
          {items.map((item, i) => (
            <li key={i}>{inline(item)}</li>
          ))}
        </ul>,
      )
      items = []
    }
  }
  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push(<p key={blocks.length}>{inline(paragraph.join(' '))}</p>)
      paragraph = []
    }
  }

  for (const raw of text.split('\n')) {
    const line = raw.trim()
    const bullet = /^([-*•]|\d+[.)])\s+(.*)$/.exec(line)
    if (bullet) {
      flushParagraph()
      items.push(bullet[2])
    } else if (line === '') {
      flushParagraph()
      flushList()
    } else {
      flushList()
      paragraph.push(line)
    }
  }
  flushParagraph()
  flushList()
  return <>{blocks}</>
}
