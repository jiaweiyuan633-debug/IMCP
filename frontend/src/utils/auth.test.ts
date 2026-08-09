import { beforeEach, describe, expect, it } from 'vitest'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'

describe('auth token storage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores and reads tokens', () => {
    setTokens('access-1', 'refresh-1')
    expect(getAccessToken()).toBe('access-1')
    expect(getRefreshToken()).toBe('refresh-1')
  })

  it('clears tokens', () => {
    setTokens('access-1', 'refresh-1')
    clearTokens()
    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })
})

