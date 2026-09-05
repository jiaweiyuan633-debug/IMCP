import { expect, test } from '@playwright/test'

// 官网默认演示模式（部署未注入 VITE_LEAD_ENDPOINT / VITE_CONTACT_* / VITE_GA_ID）：
// 预约表单明确提示未启用且按钮禁用，不再假装提交成功；虚构联系方式不再渲染。
test('官网首屏与预约表单（默认演示模式）', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '智能管理平台' })).toBeVisible()
  await page.getByRole('link', { name: '免费试用' }).click()
  await expect(page.getByRole('heading', { name: '30 分钟了解智能管理平台是否适合你' })).toBeVisible()

  // 表单填写能力保留，但提交按钮禁用且展示演示模式提示
  await page.getByPlaceholder('请输入姓名').fill('测试用户')
  await page.getByPlaceholder('请输入企业名称').fill('测试企业')
  await page.getByPlaceholder('请输入手机号').fill('13800000000')
  await expect(page.getByRole('button', { name: '提交需求' })).toBeDisabled()
  await expect(page.getByText('演示模式：表单未启用')).toBeVisible()

  // 不假装成功：成功文案恒不可见
  await expect(page.getByText('已收到需求，顾问将尽快与你联系。')).toHaveCount(0)

  // 虚构联系方式不展示（env 未配置）
  await expect(page.getByText('400-800-0015')).toHaveCount(0)
  await expect(page.getByText('hello@example.com')).toHaveCount(0)
})
