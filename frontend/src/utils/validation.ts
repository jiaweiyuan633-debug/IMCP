// 与后端 PasswordPolicy.PATTERN 保持同步——8-32 位，含大写/小写/数字/特殊字符
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[\W_]).{8,32}$/

export function isStrongPassword(value: string): boolean {
  return PASSWORD_PATTERN.test(value)
}
