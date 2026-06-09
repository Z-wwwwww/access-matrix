import { describe, it, expect } from 'vitest'
import { isValidUsername, isValidEmail } from './validators'

describe('isValidUsername — 3-64, lowercase alnum start, [a-z0-9_-]', () => {
  it('accepts valid names', () => {
    expect(isValidUsername('ops')).toBe(true)        // min length 3
    expect(isValidUsername('ops2')).toBe(true)
    expect(isValidUsername('a1_b-c')).toBe(true)
    expect(isValidUsername('a'.repeat(64))).toBe(true)  // max length 64
  })

  it('rejects too short (KC user-profile min is 3)', () => {
    expect(isValidUsername('op')).toBe(false)        // ← the reported case
    expect(isValidUsername('a')).toBe(false)
  })

  it('rejects bad format / length', () => {
    expect(isValidUsername('a'.repeat(65))).toBe(false)   // too long
    expect(isValidUsername('Ops')).toBe(false)            // uppercase
    expect(isValidUsername('_ops')).toBe(false)           // must start alnum
    expect(isValidUsername('op s')).toBe(false)           // space
    expect(isValidUsername('op.s')).toBe(false)           // dot not allowed
  })

  it('allows empty (required is checked separately)', () => {
    expect(isValidUsername('')).toBe(true)
    expect(isValidUsername(null)).toBe(true)
    expect(isValidUsername(undefined)).toBe(true)
  })
})

describe('isValidEmail (sanity)', () => {
  it('basic cases', () => {
    expect(isValidEmail('a@b.co')).toBe(true)
    expect(isValidEmail('nope')).toBe(false)
    expect(isValidEmail('')).toBe(true)
  })
})
