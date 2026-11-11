import { describe, expect, it } from 'vitest'
import { toCsv } from './csv'

describe('toCsv', () => {
  it('quotes commas, quotes and line breaks', () => {
    expect(toCsv(['Name'], [['a, "b"']])).toBe('Name\r\n"a, ""b"""')
  })

  it('leaves empty values blank', () => {
    expect(toCsv(['A', 'B'], [[null, undefined]])).toBe('A,B\r\n,')
  })

  it('neutralises anything a spreadsheet would run as a formula', () => {
    expect(toCsv(['A'], [['=HYPERLINK("x")'], ['+1'], ['@cmd']])).toBe(`A\r\n"'=HYPERLINK(""x"")"\r\n'+1\r\n'@cmd`)
  })
})
