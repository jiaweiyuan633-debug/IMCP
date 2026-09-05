import { expect, test } from '@playwright/test'

test('管理员登录并进入看板', async ({ page }) => {
  await page.goto('/login')
  const inputs = page.locator('input')
  await inputs.nth(0).fill('admin')
  await inputs.nth(1).fill('admin123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/dashboard/, { timeout: 15_000 })
  await expect(page.getByText('AI 任务状态分布')).toBeVisible()
})
