import { test, expect } from './fixtures'

test('a pending wake-up neither blocks generation nor repeats on navigation or a timer', async ({ page }) => {
  await page.clock.install()
  let probes = 0
  let generations = 0
  let release: () => void = () => {}
  const pending = new Promise<void>(resolve => { release = resolve })
  await page.route('**/actuator/health', async route => {
    probes++
    await pending
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' })
  })
  const roadmap = { id: 1, shareToken: 'alpha', topic: 'alpha', title: 'Learn alpha',
    summary: 'Prerequisites', experienceLevel: 'BEGINNER', goal: 'GENERAL_UNDERSTANDING',
    estimatedTotalMinutes: 30, prerequisites: [] }
  await page.route('**/api/roadmaps', route => {
    generations++
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(roadmap) })
  })
  await page.route('**/api/roadmaps/public/**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roadmap) }))

  try {
    await page.goto('/')
    await expect.poll(() => probes).toBe(1)
    expect(generations).toBe(0)
    await page.getByRole('link', { name: 'Privacy', exact: true }).click()
    await page.getByRole('link', { name: /^Uncomplex/ }).click()
    await page.getByLabel('Topic', { exact: true }).fill('alpha')
    await page.getByRole('button', { name: 'Build my learning path' }).click()
    await expect(page.getByRole('heading', { name: 'Learn alpha' })).toBeVisible()
    expect(generations).toBe(1)
    expect(probes).toBe(1)
    release()
    await page.getByRole('link', { name: /^Uncomplex/ }).click()
    await page.clock.fastForward('20:00')
    expect(probes).toBe(1)
  } finally {
    release()
  }
})

test('a failed wake-up stays silent and leaves a signed-in visitor able to submit', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('uncomplex.access', 'expired')
    localStorage.setItem('uncomplex.refresh', 'original')
    localStorage.setItem('uncomplex.email', 'person@example.com')
  })
  let probes = 0
  let apiRequests = 0
  await page.route('**/api/**', route => { apiRequests++; return route.abort() })
  await page.route('**/actuator/health', route => {
    probes++
    expect(route.request().headers().authorization).toBeUndefined()
    return route.fulfill({ status: 503, body: 'Service is starting' })
  })
  const response = page.waitForResponse('**/actuator/health')
  await page.goto('/')
  await response
  await page.getByRole('link', { name: 'Privacy', exact: true }).click()
  await page.getByRole('link', { name: /^Uncomplex/ }).click()
  await page.getByLabel('Topic', { exact: true }).fill('docker')
  await expect(page.getByRole('button', { name: 'Build my learning path' })).toBeEnabled()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('uncomplex.refresh'))).toBe('original')
  expect(probes).toBe(1)
  expect(apiRequests).toBe(0)
})
