import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * `/proxy_url` is a deployment contract, not an implementation detail.
 *
 * Vite's `server.proxy` rewrites the prefix away in dev; `vite build` does not —
 * `server` is dev-server config and a production bundle ships the literal. Proven
 * by building with `VITE_API_BASE_URL=https://app.example.com/api` set: the bundle
 * contained `baseURL:"/proxy_url"` and zero occurrences of that host. So the
 * reverse proxy in front of the SPA must route `/proxy_url/` to the backend's
 * `/api/`, which is what `docs/deployment.md` §5 now shows — its nginx sample used
 * to expose only `location /api/`, so following it verbatim made every API call
 * fall through to `location /` and return index.html.
 *
 * This pins the two places that spell the prefix out, so neither can be renamed
 * without the rename being noticed and the nginx block updated with it.
 */
const src = (rel) => readFileSync(resolve(process.cwd(), rel), 'utf8')

const API_PREFIX = '/proxy_url'

describe('the API prefix the deployed bundle calls', () => {
  it('axios baseURL is the documented prefix', () => {
    expect(src('src/services/request.js')).toContain(`baseURL: '${API_PREFIX}'`)
  })

  it('the SSE stream path uses the same prefix', () => {
    // EventSource can't go through axios, so this one is spelled out separately —
    // which is exactly why it can drift.
    const stream = src('src/composables/useNotificationStream.js')
    expect(stream).toMatch(new RegExp(`STREAM_URL\\s*=\\s*'${API_PREFIX}/`))
  })

  it('vite only uses VITE_API_BASE_URL for the dev proxy, never as a build-time base', () => {
    const config = src('vite.config.js')
    // The variable must appear inside the server.proxy target and nowhere else;
    // if it ever feeds `define` or a build option, the note in deployment.md
    // ("a build ignores it") becomes wrong.
    const uses = [...config.matchAll(/VITE_API_BASE_URL/g)]
    expect(uses).toHaveLength(1)
    expect(config).toMatch(/proxy:[\s\S]*VITE_API_BASE_URL/)
  })

  it('deployment.md routes that prefix instead of bare /api/', () => {
    const doc = src('../docs/deployment.md')
    expect(doc).toContain('location /proxy_url/')
  })
})
