/**
 * Pure-frontend JWT payload decode. Does NOT verify the signature.
 * Use only to read non-sensitive claims (exp, sub, tid, preferred_username, scope).
 * Returns {} if the token is malformed or empty.
 */
export function decodeJwt(token) {
  if (!token || typeof token !== 'string') return {}
  const parts = token.split('.')
  if (parts.length < 2) return {}
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '=='.slice(0, (4 - (base64.length % 4)) % 4)
    return JSON.parse(decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    ))
  } catch {
    return {}
  }
}

/**
 * Returns seconds remaining until the token's `exp`, or 0 if expired/invalid.
 */
export function secondsUntilExpiry(token) {
  const { exp } = decodeJwt(token)
  if (!exp) return 0
  return Math.max(0, exp - Math.floor(Date.now() / 1000))
}
