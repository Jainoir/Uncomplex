// Capture the real frontend with deterministic, local-only example API responses.
// Run after `npm ci` in frontend/: node scripts/capture-readme.mjs
// Set PLAYWRIGHT_CHROMIUM_EXECUTABLE to use an installed Chrome/Chromium browser.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const require = createRequire(join(root, 'frontend/package.json'))
const { chromium, expect } = require('@playwright/test')
const { createServer } = await import(pathToFileURL(require.resolve('vite')).href)
const output = join(root, 'docs/screenshots')
const origin = 'http://127.0.0.1:5187'
const email = 'learner@example.com'
const roadmap = {
  id: 1, shareToken: 'rate-limiting-demo', shareUrl: '/r/rate-limiting-demo',
  topic: 'Rate limiting', title: 'Before you build a rate limiter',
  summary: 'Understand how requests reach your API, how clients are identified, and how limits stay consistent across servers.',
  experienceLevel: 'BEGINNER', goal: 'SYSTEM_DESIGN_INTERVIEW',
  estimatedTotalMinutes: 210, createdAt: '2026-09-15T12:00:00Z',
  prerequisites: [
    { id: 1, position: 1, name: 'Client-server architecture', difficulty: 'BEGINNER', estimatedMinutes: 30,
      description: 'A client sends a request; a server processes it and sends a response. Follow that journey through a web application.',
      reason: 'You need to know where requests enter the system before choosing where to enforce a limit.', resources: [] },
    { id: 2, position: 2, name: 'HTTP requests and responses', difficulty: 'BEGINNER', estimatedMinutes: 35,
      description: 'Learn how methods, headers and status codes describe the conversation between a client and your API.',
      reason: 'A rate limiter counts incoming requests and tells clients when to retry. HTTP is how that decision reaches the caller.',
      resources: [{ title: 'MDN: An overview of HTTP', url: 'https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview',
        sourceType: 'OFFICIAL_DOCUMENTATION', credibilityReason: 'Mozilla documentation explains the request-response model and HTTP fundamentals.', reachable: null }] },
    { id: 3, position: 3, name: 'API authentication and identity', difficulty: 'BEGINNER', estimatedMinutes: 25,
      description: 'Distinguish anonymous traffic, API keys and signed-in users when deciding who a request belongs to.',
      reason: 'A per-user limit only works if the server can reliably identify the user.', resources: [] },
    { id: 4, position: 4, name: 'Caching and Redis', difficulty: 'INTERMEDIATE', estimatedMinutes: 35,
      description: 'Use fast key-value storage for shared counters, and understand why their expiry matters.',
      reason: 'Multiple API instances need access to the same budget for each client.', resources: [] },
    { id: 5, position: 5, name: 'Token buckets and fixed windows', difficulty: 'INTERMEDIATE', estimatedMinutes: 40,
      description: 'Compare a continuously refilling budget with a counter that resets at a fixed interval.',
      reason: 'The algorithm determines how your API handles bursts and sustained traffic.', resources: [] },
    { id: 6, position: 6, name: 'Distributed coordination', difficulty: 'INTERMEDIATE', estimatedMinutes: 45,
      description: 'Understand concurrent updates, atomic operations and failure when several servers share state.',
      reason: 'Two simultaneous requests must not both spend the last available token.', resources: [] },
  ],
}
const completed = new Set()
let saved = false
let releaseGeneration
const generationGate = new Promise(resolve => { releaseGeneration = resolve })
const extraLibraryItems = [
  { roadmapId: 2, topic: 'Docker', title: 'The foundations behind Docker', experienceLevel: 'BEGINNER',
    goal: 'BUILD_A_PROJECT', estimatedTotalMinutes: 160, shareToken: 'docker-demo',
    savedAt: '2026-09-14T12:00:00Z', completedNodes: 0, totalNodes: 6 },
  { roadmapId: 3, topic: 'Database indexing', title: 'Make sense of database indexes', experienceLevel: 'INTERMEDIATE',
    goal: 'GENERAL_UNDERSTANDING', estimatedTotalMinutes: 135, shareToken: 'indexing-demo',
    savedAt: '2026-09-13T12:00:00Z', completedNodes: 4, totalNodes: 5 },
]
const progress = () => ({ completedNodeIds: [...completed], completedCount: completed.size,
  totalCount: roadmap.prerequisites.length, percent: Math.round(100 * completed.size / roadmap.prerequisites.length) })
const library = () => [
  ...(saved ? [{ roadmapId: roadmap.id, topic: roadmap.topic, title: roadmap.title,
    experienceLevel: roadmap.experienceLevel, goal: roadmap.goal,
    estimatedTotalMinutes: roadmap.estimatedTotalMinutes, shareToken: roadmap.shareToken,
    savedAt: '2026-09-15T12:00:00Z', completedNodes: completed.size, totalNodes: roadmap.prerequisites.length }] : []),
  ...extraLibraryItems,
]

// Animated PNG uses the browser's lossless PNG frames directly, so it needs no
// video encoder or extra dependency. Static screenshots remain available below it.
function animatedPng(frames) {
  const crcTable = Array.from({ length: 256 }, (_, n) => {
    for (let k = 0; k < 8; k++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1
    return n >>> 0
  })
  const chunk = (type, data) => {
    const bytes = Buffer.concat([Buffer.from(type), data])
    let crc = 0xffffffff
    for (const byte of bytes) crc = crcTable[(crc ^ byte) & 255] ^ (crc >>> 8)
    const size = Buffer.alloc(4), checksum = Buffer.alloc(4)
    size.writeUInt32BE(data.length)
    checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0)
    return Buffer.concat([size, bytes, checksum])
  }
  const parse = png => {
    const chunks = []
    for (let offset = 8; offset < png.length;) {
      const size = png.readUInt32BE(offset)
      chunks.push({ type: png.toString('ascii', offset + 4, offset + 8), data: png.subarray(offset + 8, offset + 8 + size) })
      offset += size + 12
    }
    return chunks
  }
  const decoded = frames.map(frame => ({ ...frame, chunks: parse(frame.png) }))
  const header = decoded[0].chunks.find(c => c.type === 'IHDR').data
  const control = Buffer.alloc(8)
  control.writeUInt32BE(frames.length)
  const output = [frames[0].png.subarray(0, 8), chunk('IHDR', header), chunk('acTL', control)]
  let sequence = 0
  for (const [index, frame] of decoded.entries()) {
    assert.deepEqual(frame.chunks.find(c => c.type === 'IHDR').data, header, 'Animation frames must have the same dimensions and format')
    const frameControl = Buffer.alloc(26)
    frameControl.writeUInt32BE(sequence++, 0)
    header.copy(frameControl, 4, 0, 8)
    frameControl.writeUInt16BE(frame.seconds, 20)
    frameControl.writeUInt16BE(1, 22)
    output.push(chunk('fcTL', frameControl))
    for (const part of frame.chunks.filter(c => c.type === 'IDAT')) {
      if (index === 0) output.push(chunk('IDAT', part.data))
      else {
        const serial = Buffer.alloc(4)
        serial.writeUInt32BE(sequence++)
        output.push(chunk('fdAT', Buffer.concat([serial, part.data])))
      }
    }
  }
  output.push(chunk('IEND', Buffer.alloc(0)))
  return Buffer.concat(output)
}

let server, browser
const errors = []
try {
  await mkdir(output, { recursive: true })
  server = await createServer({
    root: join(root, 'frontend'), envDir: false, logLevel: 'warn',
    define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('') },
    server: { host: '127.0.0.1', port: 5187, strictPort: true },
  })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  const context = await browser.newContext({ viewport: { width: 1000, height: 1080 },
    deviceScaleFactor: 1.5, reducedMotion: 'reduce', colorScheme: 'dark',
    permissions: ['clipboard-read', 'clipboard-write'] })
  await context.route('**/*', async route => {
    const url = new URL(route.request().url())
    // The production API cannot be contacted, including through an ignored .env file.
    if (['fonts.googleapis.com', 'fonts.gstatic.com'].includes(url.hostname)) return route.continue()
    if (url.origin !== origin) { errors.push(`Unexpected external request: ${url.origin}`); return route.abort() }
    const path = url.pathname, method = route.request().method()
    const json = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/actuator/health') return json({ status: 'UP' })
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/roadmaps' && method === 'POST') {
      assert.deepEqual(route.request().postDataJSON(), {
        topic: roadmap.topic, context: 'Public REST APIs', experienceLevel: 'BEGINNER', goal: 'SYSTEM_DESIGN_INTERVIEW',
      })
      await generationGate
      return json(roadmap)
    }
    if (path === `/api/roadmaps/public/${roadmap.shareToken}`) return json(roadmap)
    if (path === '/api/auth/login') return json({ token: 'documentation-access', refreshToken: 'documentation-refresh',
      expiresAt: '2099-01-01T00:00:00Z', refreshExpiresAt: '2099-02-01T00:00:00Z', email })
    if (path === '/api/me/roadmaps' && method === 'GET') return json(library())
    if (path === '/api/me/roadmaps' && method === 'POST') {
      saved = true
      return json({ roadmap, progress: progress() })
    }
    if (path === '/api/me/roadmaps/1') return json({ roadmap, progress: progress() })
    const node = path.match(/^\/api\/me\/roadmaps\/1\/nodes\/(\d+)\/progress$/)
    if (node && method === 'PUT') {
      const id = Number(node[1])
      if (route.request().postDataJSON().completed) completed.add(id)
      else completed.delete(id)
      return json(progress())
    }
    errors.push(`Unexpected API request: ${method} ${path}`)
    return route.fulfill({ status: 404, body: 'No documentation fixture for this request' })
  })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(origin)
  await page.evaluate(async () => { await document.fonts.ready })
  await page.getByLabel('Topic', { exact: true }).fill(roadmap.topic)
  await page.getByLabel(/in the context of/i).fill('Public REST APIs')
  await page.getByLabel('Learning goal').selectOption('SYSTEM_DESIGN_INTERVIEW')
  const frames = []
  async function capture(name, seconds = 3) {
    await page.mouse.move(990, 950)
    await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo({ top: 0, left: 0, behavior: 'instant' }) })
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)
    assert.equal(overflow, false, `Horizontal overflow in ${name}`)
    const png = await page.screenshot({ path: join(output, `${name}.png`), animations: 'disabled', fullPage: name === 'mobile' })
    frames.push({ png, seconds })
    console.log(`Captured ${name}.png`)
  }
  await capture('landing')
  await page.getByRole('button', { name: 'Build my learning path' }).click()
  await expect(page.getByRole('status')).toBeVisible()
  await expect(page.getByRole('status')).toContainText('Asking Claude', { timeout: 10000 })
  await capture('generating', 2)
  await page.getByRole('status').screenshot({ path: join(output, 'generating.png'), animations: 'disabled' })
  releaseGeneration()
  await expect(page.getByRole('heading', { name: roadmap.title })).toBeVisible()
  await page.getByRole('button', { name: /2\. HTTP requests/ }).click()
  await expect(page.getByText('MDN: An overview of HTTP')).toBeVisible()
  await capture('roadmap', 4)

  // Sign in and save through the real UI. Only API responses are fixtures.
  await page.getByRole('link', { name: 'Log in', exact: true }).click()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('documentation-example')
  await page.getByRole('button', { name: 'Log in', exact: true }).last().click()
  await expect(page.getByRole('heading', { name: 'My roadmaps' })).toBeVisible()
  await page.goto(`${origin}${roadmap.shareUrl}`)
  await page.getByRole('button', { name: 'Save to my library' }).click()
  await expect(page.getByLabel('0% complete')).toBeVisible()
  await page.getByRole('checkbox', { name: 'Mark Client-server architecture as done', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: 'Mark Client-server architecture as not done', exact: true })).toBeChecked()
  await page.getByRole('checkbox', { name: 'Mark HTTP requests and responses as done', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: 'Mark HTTP requests and responses as not done', exact: true })).toBeChecked()
  await expect(page.getByLabel('33% complete')).toBeVisible()
  await page.getByRole('button', { name: /3\. API authentication/ }).click()
  await capture('progress')
  await page.getByRole('button', { name: 'Copy sharing link' }).click()
  await expect(page.getByRole('button', { name: 'Copied!' })).toBeVisible()
  assert.equal(await page.evaluate(() => navigator.clipboard.readText()), `${origin}${roadmap.shareUrl}`)
  await capture('sharing', 2)
  await page.locator('.roadmap-header').screenshot({ path: join(output, 'sharing.png'), animations: 'disabled' })

  await page.getByRole('link', { name: 'My roadmaps', exact: true }).click()
  await expect(page.getByRole('link', { name: roadmap.title })).toBeVisible()
  await expect(page.locator('.library-item').first()).toContainText('2/6')
  await capture('library', 3)
  const libraryBounds = await page.locator('.library-list').boundingBox()
  await page.screenshot({ path: join(output, 'library.png'), animations: 'disabled',
    clip: { x: 0, y: 0, width: 1000, height: Math.ceil(libraryBounds.y + libraryBounds.height + 24) } })
  await writeFile(join(output, 'walkthrough.png'), animatedPng(frames))

  await page.setViewportSize({ width: 390, height: 1100 })
  await page.getByRole('link', { name: roadmap.title }).click()
  await expect(page.getByLabel('33% complete')).toBeVisible()
  await page.getByRole('button', { name: /3\. API authentication/ }).click()
  await capture('mobile')
  assert.deepEqual(errors, [], 'Documentation capture should have no page errors or unhandled requests')
  console.log('Verified generation, sign-in, save, progress after navigation, sharing, library and mobile layout.')
} finally {
  releaseGeneration()
  await browser?.close()
  await server?.close()
}
