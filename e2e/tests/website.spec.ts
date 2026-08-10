import { expect, test } from '@playwright/test'

test('官网首屏与预约表单', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '智能管理平台' })).toBeVisible()
  await page.getByRole('link', { name: '免费试用' }).click()
  await expect(page.getByRole('heading', { name: '30 分钟了解智能管理平台是否适合你' })).toBeVisible()
  await page.getByPlaceholder('请输入姓名').fill('测试用户')
  await page.getByPlaceholder('请输入企业名称').fill('测试企业')
  await page.getByPlaceholder('请输入手机号').fill('13800000000')
  await page.getByRole('button', { name: '提交需求' }).click()
  await expect(page.getByText('已收到需求，顾问将尽快与你联系。')).toBeVisible()
})
