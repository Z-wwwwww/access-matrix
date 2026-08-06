import { describe, it, expect, vi, beforeEach } from 'vitest'

function memoryStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear()
  }
}
vi.stubGlobal('localStorage', memoryStorage())
vi.stubGlobal('sessionStorage', memoryStorage())

vi.mock('@/router', () => ({
  default: { currentRoute: { value: { fullPath: '/system/user' } }, push: vi.fn(), replace: vi.fn() }
}))
vi.mock('@/lang', () => ({
  default: { global: { te: (k) => k === 'error.common.duplicateKey', t: () => '该名称或编码已被占用' } }
}))
vi.mock('@/utils/tenant', () => ({ currentTenant: () => 'demo' }))

import request from '@/services/request'

/**
 * A 4xx must reach the caller as the SERVER's message, not as axios's
 * "Request failed with status code 400".
 *
 * The interceptor only ever read `data.msg` for status 500; every other error
 * status fell through to `Promise.reject(error)`, handing callers the raw
 * AxiosError. Callers do `catch (e) { toast.error(e.message) }`, so the user got
 * an English axios string while the backend had sent a perfectly good localized
 * reason in the body.
 *
 * This got worse the moment the framework client errors were mapped off 500 (the
 * malformed-JSON / bad-param / wrong-verb family): those responses moved from the
 * one branch that DID surface `msg` into the branch that doesn't.
 *
 * The rejection also has to stay an AxiosError: ExportFileButton recovers a
 * failed download's real message via `e.response.data instanceof Blob`, which a
 * bare `new Error(msg)` destroys.
 */
describe('request interceptor — error messages reaching the caller', () => {
  beforeEach(() => {
    request.defaults.adapter = undefined
  })

  function respondWith(status, data) {
    request.defaults.adapter = async (config) => {
      const err = new Error('Request failed with status code ' + status)
      err.config = config
      err.response = { status, data, headers: {}, config }
      err.isAxiosError = true
      throw err
    }
  }

  it('surfaces the server message on a 400', async () => {
    respondWith(400, { code: 701, msg: "Invalid value for parameter 'page'" })

    await expect(request.get('/admin/user/list'))
      .rejects.toThrow("Invalid value for parameter 'page'")
  })

  it('localizes an i18n key sent as the message', async () => {
    respondWith(400, { code: 700, msg: 'error.common.duplicateKey' })

    await expect(request.post('/admin/role', {})).rejects.toThrow('该名称或编码已被占用')
  })

  it('keeps the AxiosError so response.data stays reachable', async () => {
    // ExportFileButton needs e.response.data to pull the message out of a Blob.
    respondWith(400, { code: 700, msg: 'nope' })

    await request.get('/admin/user/list').catch((e) => {
      expect(e.response, 'response must survive so blob-error recovery still works').toBeTruthy()
      expect(e.response.status).toBe(400)
    })
  })

  it('still handles 500 the same way', async () => {
    respondWith(500, { code: 500, msg: 'Internal server error' })

    await expect(request.get('/admin/user/list')).rejects.toThrow('Internal server error')
  })

  it('falls back to a readable message when the body carries none', async () => {
    respondWith(503, null)

    await expect(request.get('/admin/user/list'))
      .rejects.not.toThrow('Request failed with status code 503')
  })
})
