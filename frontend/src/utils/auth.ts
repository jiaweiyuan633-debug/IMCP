const ACCESS_TOKEN_KEY = 'admin_access_token'
const REFRESH_TOKEN_KEY = 'admin_refresh_token'
// R4-1.33：登出广播哨兵键——写时间戳告知其他标签页「本标签页已登出」，
// 配合 storage 事件让所有标签页同步失效会话（此前 tab A 登出后 tab B 的 token 仍存活）
const AUTH_CLEARED_KEY = 'admin_auth_cleared_at'

// 内存缓存：避免每次请求重复读 localStorage，且跨标签页清除后本标签页立即失效
let memoryAccessToken = ''
let memoryRefreshToken = ''

type AuthClearedListener = () => void
const clearedListeners = new Set<AuthClearedListener>()

export function getAccessToken(): string {
  if (!memoryAccessToken) {
    memoryAccessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || ''
  }
  return memoryAccessToken
}

export function getRefreshToken(): string {
  if (!memoryRefreshToken) {
    memoryRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || ''
  }
  return memoryRefreshToken
}

export function setTokens(accessToken: string, refreshToken: string): void {
  memoryAccessToken = accessToken
  memoryRefreshToken = refreshToken
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens(): void {
  memoryAccessToken = ''
  memoryRefreshToken = ''
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  // 向其他标签页广播登出（同标签页不会收到自己的 storage 事件，无自触发）
  localStorage.setItem(AUTH_CLEARED_KEY, String(Date.now()))
}

/** 注册「凭证已在其他标签页被清除」回调（如跳转登录页），返回取消订阅函数。 */
export function subscribeAuthCleared(listener: AuthClearedListener): () => void {
  clearedListeners.add(listener)
  return () => clearedListeners.delete(listener)
}

function notifyAuthCleared() {
  clearedListeners.forEach((listener) => listener())
}

/**
 * 跨标签页凭证同步：storage 事件只在「其他标签页」触发，本标签页不触发，
 * 因此这里能安全地把远端标签页的 token 刷新/清除同步到本标签页内存态。
 */
function onStorageChange(event: StorageEvent) {
  if (event.key === AUTH_CLEARED_KEY) {
    // 其他标签页登出广播：本标签页同步清内存与共享 localStorage。
    // 必须连共享存储一起清——getAccessToken 在内存为空时会从 localStorage 回填，
    // 若只清内存，残留 token 会让本标签页会话死灰复燃。
    memoryAccessToken = ''
    memoryRefreshToken = ''
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    notifyAuthCleared()
    return
  }
  if (event.key === ACCESS_TOKEN_KEY || event.key === REFRESH_TOKEN_KEY) {
    if (localStorage.getItem(ACCESS_TOKEN_KEY)) {
      // 其他标签页登录/续期刷新了 token → 同步内存
      memoryAccessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || ''
      memoryRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || ''
    } else {
      // 其他标签页直接清除了 token
      memoryAccessToken = ''
      memoryRefreshToken = ''
      notifyAuthCleared()
    }
  }
}

let syncStarted = false

/** 启动跨标签页凭证同步，应用入口（main.ts）调用一次；幂等，可重复调用。 */
export function initAuthSync(): void {
  if (syncStarted) {
    return
  }
  syncStarted = true
  window.addEventListener('storage', onStorageChange)
}
