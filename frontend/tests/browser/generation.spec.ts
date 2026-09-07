import { test, expect, type Page } from '@playwright/test'

const roadmap = {
  id: 1, shareToken: 'alpha', title: 'Learn alpha', topic: 'alpha',
  summary: 'Prerequisites', experienceLevel: 'BEGINNER', goal: 'GENERAL_UNDERSTANDING',
  estimatedTotalMinutes: 30,
  prerequisites: [{ id: 1, name: 'Foundations', description: 'Start here', reason: 'Build understanding',
    difficulty: 'BEGINNER', estimatedMinutes: 30, position: 1, resources: [] }],
}

/** Holds the generate call open until `release()` is called, so the pending UI can be inspected. */
async function slowGeneration(page: Page) {
  let release: () => void = () => {}
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/roadmaps', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    await gate
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(roadmap) })
  })
  await page.route('**/api/roadmaps/public/**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roadmap) }))
  return () => release()
}

async function submit(page: Page, topic: string) {
  await page.goto('/')
  await page.getByLabel(/topic/i).fill(topic)
  await page.getByRole('button', { name: 'Build my learning path' }).click()
}

test('a pending generation shows live progress, not a frozen button', async ({ page }) => {
  const release = await slowGeneration(page)
  await submit(page, 'kubernetes')

  const status = page.getByRole('status')
  await expect(status).toBeVisible()
  await expect(status).toContainText('kubernetes')
  await expect(status).toContainText(/elapsed/)
  await expect(page.getByRole('button', { name: /Building your path/ })).toBeDisabled()

  release()
  await expect(page.getByRole('heading', { name: 'Learn alpha' })).toBeVisible()
})

test('the elapsed counter advances while waiting', async ({ page }) => {
  const release = await slowGeneration(page)
  await submit(page, 'docker')

  const status = page.getByRole('status')
  await expect(status).toContainText('0s elapsed')
  await expect(status).toContainText('2s elapsed', { timeout: 5000 })

  release()
})

test('progress disappears and the error shows when generation fails', async ({ page }) => {
  await page.route('**/api/roadmaps', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    await route.fulfill({
      status: 503, contentType: 'application/problem+json',
      body: JSON.stringify({ detail: 'A matching operation is still running. Please retry shortly.' }),
    })
  })
  await submit(page, 'redis')

  await expect(page.getByText(/still running/i)).toBeVisible()
  await expect(page.getByRole('status')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Build my learning path' })).toBeEnabled()
})

test('the context field is sent so ambiguous topics can be disambiguated', async ({ page }) => {
  const sent: Array<Record<string, unknown>> = []
  await page.route('**/api/roadmaps', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    sent.push(route.request().postDataJSON())
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(roadmap) })
  })
  await page.route('**/api/roadmaps/public/**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roadmap) }))

  await page.goto('/')
  await page.getByLabel('Topic', { exact: true }).fill('integration')
  await page.getByLabel(/in the context of/i).fill('CI/CD pipelines')
  await page.getByRole('button', { name: 'Build my learning path' }).click()
  await expect(page.getByRole('heading', { name: 'Learn alpha' })).toBeVisible()

  expect(sent).toHaveLength(1)
  expect(sent[0].topic).toBe('integration')
  expect(sent[0].context).toBe('CI/CD pipelines')
})

test('context is optional and submits as empty when untouched', async ({ page }) => {
  const sent: Array<Record<string, unknown>> = []
  await page.route('**/api/roadmaps', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    sent.push(route.request().postDataJSON())
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(roadmap) })
  })
  await page.route('**/api/roadmaps/public/**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roadmap) }))

  await page.goto('/')
  await page.getByLabel('Topic', { exact: true }).fill('docker')
  await page.getByRole('button', { name: 'Build my learning path' }).click()
  await expect(page.getByRole('heading', { name: 'Learn alpha' })).toBeVisible()

  expect(sent[0].context).toBe('')
})
