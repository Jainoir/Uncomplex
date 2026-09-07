import { test, expect, type Page } from '@playwright/test'

const roadmap = {
  id: 1, shareToken: 'alpha', title: 'Learn alpha', topic: 'alpha',
  summary: 'Prerequisites', experienceLevel: 'BEGINNER', goal: 'GENERAL_UNDERSTANDING',
  estimatedTotalMinutes: 30,
  prerequisites: [{ id: 1, name: 'Foundations', description: 'Start here', reason: 'Build understanding',
    difficulty: 'BEGINNER', estimatedMinutes: 30, position: 1, resources: [] }],
}

const LONG_EMAIL = 'browser-audit-1788751806416@example.com'

async function signedIn(page: Page, email = LONG_EMAIL) {
  await page.addInitScript(e => {
    localStorage.setItem('uncomplex.access', 'a')
    localStorage.setItem('uncomplex.refresh', 'r')
    localStorage.setItem('uncomplex.email', e as string)
  }, email)
}

test('leaving the page during generation does not drag the user back to the roadmap', async ({ page }) => {
  let release: () => void = () => {}
  const gate = new Promise<void>(r => { release = r })
  await page.route('**/api/roadmaps', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    await gate
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(roadmap) })
  })

  await page.goto('/')
  await page.getByLabel('Topic', { exact: true }).fill('kubernetes')
  await page.getByRole('button', { name: 'Build my learning path' }).click()
  await expect(page.getByRole('status')).toBeVisible()

  await page.getByRole('link', { name: 'Privacy' }).click()
  await expect(page.getByRole('heading', { name: 'Privacy policy' })).toBeVisible()

  release()
  await page.waitForTimeout(1200)

  // Still on Privacy: the late response must not navigate for us.
  await expect(page.getByRole('heading', { name: 'Privacy policy' })).toBeVisible()
  expect(new URL(page.url()).pathname).toBe('/privacy')
})

test('a failed account deletion keeps the session so the retry can work', async ({ page }) => {
  await signedIn(page)
  await page.route('**/api/me', route =>
    route.request().method() === 'DELETE'
      ? route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"detail":"busy"}' })
      : route.fallback())
  await page.route('**/api/me/roadmaps', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))

  await page.goto('/library')
  await page.getByRole('button', { name: 'Delete my account' }).click()
  await page.getByRole('button', { name: 'Yes, delete everything' }).click()

  await expect(page.getByRole('alert')).toContainText('Could not delete your account')

  const tokens = await page.evaluate(() => ({
    access: localStorage.getItem('uncomplex.access'),
    refresh: localStorage.getItem('uncomplex.refresh'),
  }))
  expect(tokens.access).not.toBeNull()
  expect(tokens.refresh).not.toBeNull()
})

test('a logout elsewhere clears the previous account library from this tab', async ({ page }) => {
  await signedIn(page)
  await page.route('**/api/me/roadmaps', route =>
    route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify([{ ...roadmap, roadmapId: 1, completedNodes: 0, totalNodes: 1 }]),
    }))

  await page.goto('/library')
  await expect(page.getByRole('link', { name: 'Learn alpha' })).toBeVisible()

  // Another tab logs out: same-origin storage clears and the auth event fires.
  await page.evaluate(() => {
    localStorage.removeItem('uncomplex.access')
    localStorage.removeItem('uncomplex.refresh')
    localStorage.removeItem('uncomplex.email')
    window.dispatchEvent(new Event('uncomplex:auth'))
  })

  await expect(page.getByRole('link', { name: 'Learn alpha' })).toHaveCount(0)
})

for (const width of [320, 390]) {
  test(`a long signed-in email does not overflow a ${width}px viewport`, async ({ page }) => {
    await page.setViewportSize({ width, height: 780 })
    await signedIn(page)
    await page.route('**/api/roadmaps/public/**', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roadmap) }))
    await page.route('**/api/me/**', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))

    await page.goto('/r/alpha')
    await expect(page.getByRole('heading', { name: 'Learn alpha' })).toBeVisible()

    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    expect(scrollWidth).toBeLessThanOrEqual(width)
  })
}
