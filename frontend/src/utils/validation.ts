export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/

export function isStrongPassword(value: string): boolean {
  return PASSWORD_PATTERN.test(value)
}
