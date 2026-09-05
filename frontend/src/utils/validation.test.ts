import { describe, expect, it } from 'vitest'
import { isStrongPassword } from './validation'

describe('isStrongPassword', () => {
  it('accepts strong password', () => {
    expect(isStrongPassword('Admin@123')).toBe(true)
    expect(isStrongPassword('Abc@12345')).toBe(true)
  })

  it('rejects weak password', () => {
    expect(isStrongPassword('12345678')).toBe(false)
    expect(isStrongPassword('password')).toBe(false)
    expect(isStrongPassword('A1')).toBe(false)
  })

  it('rejects password missing one of the four classes', () => {
    // 大写/小写/数字/特殊字符四类缺一不可
    expect(isStrongPassword('Admin123')).toBe(false) // 缺特殊字符
    expect(isStrongPassword('admin@123')).toBe(false) // 缺大写
    expect(isStrongPassword('ADMIN@123')).toBe(false) // 缺小写
    expect(isStrongPassword('Admin@abc')).toBe(false) // 缺数字
  })
})
