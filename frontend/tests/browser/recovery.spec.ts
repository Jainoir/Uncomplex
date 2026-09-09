import { test, expect, type Page } from './fixtures'

const roadmap = (token = 'alpha') => ({
  id: token === 'alpha' ? 1 : 2, shareToken: token, title: `Learn ${token}`,
  topic: token, summary: 'Prerequisites to get started', experienceLevel: 'BEGINNER',
  goal: 'BUILD_A_PROJECT', estimatedTotalMinutes: 30,
  prerequisites: [{ id: 1, name: 'Foundations', description: 'Start here', reason: 'Build understanding',
    difficulty: 'BEGINNER', estimatedMinutes: 30, position: 1, resources: [] }],
})

async function setup(page: Page, state: { saved?: boolean; libraryFail?: boolean; saveFail?: boolean;
  progressFail?: boolean; removeFail?: boolean } = {}) {
  await page.addInitScript(() => {
    localStorage.setItem('uncomplex.access', 'access')
    localStorage.setItem('uncomplex.refresh', 'refresh')
    localStorage.setItem('uncomplex.email', 'person@example.com')
  })
  const progress = { completedNodeIds: [] as number[], completedCount: 0, totalCount: 1, percent: 0 }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    let status = 200
    let body: unknown = {}
    if (path.startsWith('/api/roadmaps/public/')) body = roadmap(path.split('/').at(-1))
    else if (path.endsWith('/progress')) {
      if (state.progressFail) status = 500
      else {
        progress.completedNodeIds = [1]; progress.completedCount = 1; progress.percent = 100
        body = progress
      }
    } else if (method === 'DELETE') {
      if (state.removeFail) status = 500
      else { state.saved = false; status = 204 }
    } else if (method === 'POST') {
      if (state.saveFail) status = 500
      else { state.saved = true; body = { roadmap: roadmap(), progress } }
    } else if (path === '/api/me/roadmaps') {
      if (state.libraryFail) status = 500
      else body = state.saved ? [{ ...roadmap(), roadmapId: 1, completedNodes: 0, totalNodes: 1 }] : []
    } else body = { roadmap: roadmap(), progress }
    await route.fulfill({ status, contentType: 'application/json',
      body: status === 204 ? '' : JSON.stringify(body) })
  })
}

test('library errors are visible and retry recovers the saved list', async ({ page }) => {
  const state = { saved: true, libraryFail: true }
  await setup(page, state)
  await page.goto('/library')
  await expect(page.getByRole('alert')).toContainText('Could not load your library')
  await expect(page.getByText('Nothing saved yet.')).toHaveCount(0)
  state.libraryFail = false
  await page.getByRole('button', { name: 'Retry', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Learn alpha' })).toBeVisible()
})

test('a library outage does not hide a public roadmap', async ({ page }) => {
  await setup(page, { libraryFail: true })
  await page.goto('/r/alpha')
  await expect(page.getByRole('heading', { name: 'Learn alpha' })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('saved progress could not be loaded')
})

test('failed save shows an error and can be retried', async ({ page }) => {
  const state = { saveFail: true }
  await setup(page, state)
  await page.goto('/r/alpha')
  const save = page.getByRole('button', { name: 'Save to my library' })
  await save.click()
  await expect(page.getByRole('alert')).toContainText('Could not save this roadmap')
  state.saveFail = false
  await save.click()
  await expect(page.getByRole('checkbox')).toBeVisible()
})

test('failed progress update keeps the checkbox unchanged and retry succeeds', async ({ page }) => {
  const state = { saved: true, progressFail: true }
  await setup(page, state)
  await page.goto('/r/alpha')
  const checkbox = page.getByRole('checkbox')
  await checkbox.click()
  await expect(page.getByRole('alert')).toContainText('Could not update your progress')
  await expect(checkbox).not.toBeChecked()
  state.progressFail = false
  await checkbox.click()
  await expect(checkbox).toBeChecked()
  await expect(page.getByText('1/1 done (100%)', { exact: true })).toBeVisible()
})

test('failed removal preserves the library item and permits retry', async ({ page }) => {
  const state = { saved: true, removeFail: true }
  await setup(page, state)
  await page.goto('/library')
  await page.getByRole('button', { name: 'Remove', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Could not remove this roadmap')
  await expect(page.getByRole('link', { name: 'Learn alpha' })).toBeVisible()
  state.removeFail = false
  await page.getByRole('button', { name: 'Remove', exact: true }).click()
  await expect(page.getByText('Nothing saved yet.', { exact: false })).toBeVisible()
})

test('changing share routes resets membership and progress state', async ({ page }) => {
  await setup(page, { saved: true })
  await page.goto('/r/alpha')
  await expect(page.getByRole('checkbox')).toBeVisible()
  await page.evaluate(() => {
    window.history.pushState({}, '', '/r/beta')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.getByRole('heading', { name: 'Learn beta' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Save to my library' })).toBeEnabled()
  await expect(page.getByRole('checkbox')).toHaveCount(0)
})
