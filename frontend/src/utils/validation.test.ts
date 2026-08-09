import { describe, expect, it } from 'vitest'
import { isStrongPassword } from './validation'

describe('isStrongPassword', () => {
  it('accepts strong password', () => {
    expect(isStrongPassword('Admin123')).toBe(true)
  })

  it('rejects weak password', () => {
    expect(isStrongPassword('12345678')).toBe(false)
    expect(isStrongPassword('password')).toBe(false)
    expect(isStrongPassword('A1')).toBe(false)
  })
})
