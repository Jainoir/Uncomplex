import { test as base } from '@playwright/test'

// Browser tests mock the backend; the background health probe must also stay local.
export const test = base.extend<{ healthEndpoint: void }>({
  healthEndpoint: [async ({ page }, use) => {
    await page.route('**/actuator/health', route => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'UP' }),
    }))
    await use()
  }, { auto: true }],
})

export { expect, type Page } from '@playwright/test'
