import { describe, it, expect } from 'vitest'
import { newCodeVerifier, deriveCodeChallenge, newState } from './pkce'

describe('PKCE — RFC 7636 conformance', () => {
  it('verifier is base64url, length within 43-128 chars', () => {
    const v = newCodeVerifier()
    expect(v.length).toBeGreaterThanOrEqual(43)
    expect(v.length).toBeLessThanOrEqual(128)
    // base64url: only A-Z a-z 0-9 - _ allowed, NO padding (=) or + /
    expect(v).toMatch(/^[A-Za-z0-9_-]+$/)
  })

  it('challenge is base64url SHA-256 of the verifier (44 chars no padding)', async () => {
    const v = newCodeVerifier()
    const c = await deriveCodeChallenge(v)
    // SHA-256 → 32 bytes → base64 = 44 chars w/ padding → base64url no-pad = 43
    expect(c.length).toBe(43)
    expect(c).toMatch(/^[A-Za-z0-9_-]+$/)
  })

  it('same verifier always derives the same challenge', async () => {
    const v = newCodeVerifier()
    const c1 = await deriveCodeChallenge(v)
    const c2 = await deriveCodeChallenge(v)
    expect(c1).toBe(c2)
  })

  it('different verifiers derive different challenges', async () => {
    const v1 = newCodeVerifier()
    const v2 = newCodeVerifier()
    expect(v1).not.toBe(v2)
    const c1 = await deriveCodeChallenge(v1)
    const c2 = await deriveCodeChallenge(v2)
    expect(c1).not.toBe(c2)
  })

  it('state is base64url and reasonably long (16 bytes = 22 chars)', () => {
    const s = newState()
    expect(s.length).toBe(22)
    expect(s).toMatch(/^[A-Za-z0-9_-]+$/)
  })

  it('two consecutive states differ — random source', () => {
    expect(newState()).not.toBe(newState())
  })

  // RFC 7636 Appendix B test vector pins the SHA-256→base64url derivation
  // against a known input/output pair. If our base64url math drifts (extra
  // padding, wrong char substitution) this test catches it before any
  // Keycloak round-trip would.
  it('matches RFC 7636 Appendix B test vector', async () => {
    const verifier  = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'
    const expected  = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'
    const challenge = await deriveCodeChallenge(verifier)
    expect(challenge).toBe(expected)
  })
})
