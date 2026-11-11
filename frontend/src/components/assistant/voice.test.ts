import { describe, expect, it } from 'vitest'
import { plainForSpeech, voiceCommand } from './voice'

describe('voiceCommand', () => {
  it('hears the ways people end a voice chat', () => {
    for (const said of ['stop', 'Stop.', 'ok stop', 'stop listening', "that's all", 'goodbye', 'bye bye', 'end the chat', 'bas', 'ruk jao']) {
      expect(voiceCommand(said)).toBe('stop')
    }
  })

  it('does not end the chat when "stop" is part of a request', () => {
    expect(voiceCommand('stop the timer on Design homepage')).toBeNull()
    expect(voiceCommand('what tasks are blocked')).toBeNull()
  })

  it('hears approve and decline', () => {
    for (const said of ['yes', 'Approve', 'approve all', 'go ahead', 'Yes please', 'haan', 'okay do it']) {
      expect(voiceCommand(said)).toBe('approve')
    }
    for (const said of ['no', 'Decline.', 'cancel', "don't", 'nahi']) {
      expect(voiceCommand(said)).toBe('decline')
    }
  })
})

describe('plainForSpeech', () => {
  it('drops formatting marks so they are not read out', () => {
    expect(plainForSpeech('**Apollo** is on track:\n- 3 open\n- 1 blocked')).toBe('Apollo is on track: 3 open 1 blocked')
  })
})
