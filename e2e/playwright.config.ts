import { defineConfig } from '@playwright/test'

const adminUrl = process.env.ADMIN_URL || 'http://localhost:5173'
const websiteUrl = process.env.WEBSITE_URL || 'http://localhost:5174'

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  use: {
    channel: process.env.CI ? undefined : 'msedge',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'admin',
      use: { baseURL: adminUrl },
      testMatch: /admin\.spec\.ts/,
    },
    {
      name: 'website',
      use: { baseURL: websiteUrl },
      testMatch: /website\.spec\.ts/,
    },
  ],
})
