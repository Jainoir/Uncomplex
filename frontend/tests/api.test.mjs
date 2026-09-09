import { test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/api.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText
let api
let storage
const json = (status, body = {}) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
})
const auth = { token: 'fresh', refreshToken: 'rotated', email: 'person@example.com' }
const seed = () => {
  storage.set('uncomplex.access', 'expired')
  storage.set('uncomplex.refresh', 'original')
  storage.set('uncomplex.email', auth.email)
}
const loadClient = async () => (await import('data:text/javascript;base64,' +
  Buffer.from(compiled + '\n//' + Math.random()).toString('base64'))).api

test('requests have a deadline and timeout errors are actionable', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.ok(options.signal instanceof AbortSignal)
    throw new DOMException('Deadline exceeded', 'TimeoutError')
  }
  await assert.rejects(api.getShared('alpha'), error => error.status === 408 && /timed out/.test(error.message))
})

beforeEach(async () => {
  storage = new Map()
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key),
  }
  globalThis.window = new EventTarget()
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: {} })
  api = await loadClient()
})

test('parallel expired requests rotate once and both retry successfully', async () => {
  seed()
  let refreshes = 0
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/refresh')) {
      refreshes++
      await new Promise(resolve => setTimeout(resolve, 20))
      return json(200, auth)
    }
    return options.headers.Authorization === 'Bearer fresh' ? json(200, []) : json(401)
  }
  assert.deepEqual(await Promise.all([api.myRoadmaps(), api.myRoadmaps()]), [[], []])
  assert.equal(refreshes, 1)
})

test('browser lock coordinates refresh between separate tabs', async () => {
  seed()
  let queue = Promise.resolve()
  navigator.locks = { request: (_name, callback) => {
    const result = queue.then(callback)
    queue = result.catch(() => {})
    return result
  } }
  const secondTab = await loadClient()
  let refreshes = 0
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/refresh')) { refreshes++; return json(200, auth) }
    return options.headers.Authorization === 'Bearer fresh' ? json(200, []) : json(401)
  }
  await Promise.all([api.myRoadmaps(), secondTab.myRoadmaps()])
  assert.equal(refreshes, 1)
})

test('shared links and login omit an expired bearer token', async () => {
  seed()
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.Authorization, undefined)
    return json(200, auth)
  }
  await api.getShared('docker-example')
  await api.login('person@example.com', 'password')
})

test('temporary refresh failure preserves the session for a later retry', async () => {
  seed()
  globalThis.fetch = async url => json(url.endsWith('/refresh') ? 503 : 401)
  await assert.rejects(api.myRoadmaps(), error => error.status === 503)
  assert.equal(storage.get('uncomplex.refresh'), 'original')
})

test('rejected refresh clears the session and notifies the UI', async () => {
  seed()
  let notified = false
  window.addEventListener('uncomplex:auth', () => { notified = true })
  globalThis.fetch = async () => json(401)
  await assert.rejects(api.myRoadmaps(), error => error.status === 401)
  assert.equal(api.isLoggedIn(), false)
  assert.equal(notified, true)
})

test('logout during refresh cannot resurrect the session', async () => {
  seed()
  let finishRefresh
  let refreshStarted
  const started = new Promise(resolve => { refreshStarted = resolve })
  globalThis.fetch = async url => {
    if (url.endsWith('/refresh')) {
      refreshStarted()
      return new Promise(resolve => { finishRefresh = () => resolve(json(200, auth)) })
    }
    if (url.endsWith('/logout')) return new Response(null, { status: 204 })
    return json(401)
  }
  const request = api.myRoadmaps()
  const rejected = assert.rejects(request, error => error.status === 401)
  await started
  await api.logout()
  finishRefresh()
  await rejected
  assert.equal(api.isLoggedIn(), false)
  assert.equal(storage.has('uncomplex.access'), false)
})

test('timeouts clear a cold start, and generation gets the larger budget', async () => {
  const { TIMEOUTS } = await import('data:text/javascript;base64,' +
    Buffer.from(compiled + '\n//' + Math.random()).toString('base64'))

  // The API has been measured booting in ~140s on its free instance. A timeout below that
  // cannot survive a cold start: the first request after an idle period fails every time,
  // which is exactly what a flat 120s did.
  const MEASURED_COLD_START_MS = 140_000
  assert.ok(TIMEOUTS.default > MEASURED_COLD_START_MS,
    `default ${TIMEOUTS.default}ms must exceed the ~${MEASURED_COLD_START_MS}ms cold start`)

  // Generation adds a real model call (~46s warm) on top of any wake-up.
  assert.ok(TIMEOUTS.generation > TIMEOUTS.default,
    'generation needs a longer budget than an ordinary request')
})

test('warm-up sends one anonymous uncached health request without waiting for it', async () => {
  seed()
  const calls = []
  let release
  globalThis.fetch = (url, options) => {
    calls.push({ url, options })
    return new Promise(resolve => { release = resolve })
  }
  const first = api.warmup()
  const second = api.warmup()
  assert.equal(first, second)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].url, '/actuator/health')
  assert.equal(calls[0].options.credentials, 'omit')
  assert.equal(calls[0].options.cache, 'no-store')
  assert.equal(calls[0].options.headers, undefined)
  assert.ok(calls[0].options.signal instanceof AbortSignal)
  release(json(200, { status: 'UP' }))
  await first
  await api.warmup()
  assert.equal(calls.length, 1)
})

for (const failure of ['http', 'network', 'timeout']) {
  test(`warm-up ${failure} failure preserves the session and does not retry`, async () => {
    seed()
    let calls = 0
    globalThis.fetch = async () => {
      calls++
      if (failure === 'network') throw new TypeError('Failed to fetch')
      if (failure === 'timeout') throw new DOMException('Deadline exceeded', 'TimeoutError')
      return json(401)
    }
    await api.warmup()
    await api.warmup()
    assert.equal(calls, 1)
    assert.equal(storage.get('uncomplex.access'), 'expired')
    assert.equal(storage.get('uncomplex.refresh'), 'original')
  })
}

test('generation is sent with its own longer abort signal', async () => {
  seed()
  const signals = []
  globalThis.fetch = async (_url, options) => {
    signals.push(options.signal)
    return json(201, { shareToken: 'alpha' })
  }
  await api.generate('kubernetes', 'container orchestration', 'BEGINNER', 'GENERAL_UNDERSTANDING')

  assert.equal(signals.length, 1)
  assert.ok(signals[0] instanceof AbortSignal)
  assert.equal(signals[0].aborted, false)
})
