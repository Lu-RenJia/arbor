import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getUserInfo, logoutApi } from '../api/auth'

/**
 * 认证状态 Store（P2-11/12）
 * - token 持久化于 localStorage（请求凭证）
 * - 用户信息/角色/权限仅保存在内存中，不落 localStorage（防篡改）；
 *   页面刷新后由路由守卫调用 fetchUser() 从后端 /api/auth/info 重新拉取可信数据
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<{ id: number; username: string; nickname: string; role: string; permissions: string[] } | null>(null)

  // 管理员（role=ADMIN 拥有全部权限）
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  // 当前用户ID（用于按 sharedBy 门控「移除共享」等按钮）
  const userId = computed(() => user.value?.id ?? null)

  // 权限码列表
  const permissions = computed(() => user.value?.permissions ?? [])

  // 检查是否拥有某权限
  function hasPermission(code: string): boolean {
    if (!user.value) return false
    if (isAdmin.value) return true
    return permissions.value.includes(code)
  }

  // 检查是否拥有任一权限
  function hasAnyPermission(codes: string[]): boolean {
    if (!user.value) return false
    if (isAdmin.value) return true
    return codes.some(c => permissions.value.includes(c))
  }

  // 检查是否拥有某角色
  function hasRole(role: string): boolean {
    if (!user.value) return false
    if (isAdmin.value) return true
    return user.value.role === role
  }

  async function login(username: string, password: string) {
    const res: any = await loginApi({ username, password })
    localStorage.setItem('token', res.token)
    user.value = {
      id: res.id,
      username: res.username,
      nickname: res.nickname,
      role: res.role || 'USER',
      permissions: res.permissions || []
    }
  }

  /** 从后端拉取最新用户信息与权限（刷新页面/权限变更后调用） */
  async function fetchUser() {
    try {
      const res: any = await getUserInfo()
      user.value = {
        id: res.id,
        username: res.username,
        nickname: res.nickname,
        role: res.role || 'USER',
        permissions: res.permissions || []
      }
    } catch {
      clearSession()
    }
  }

  /** 清除本地会话（不调用后端，用于 token 失效场景） */
  function clearSession() {
    localStorage.removeItem('token')
    user.value = null
  }

  async function logout() {
    try { await logoutApi() } catch { /* 即使服务端调用失败也清前端 */ }
    clearSession()
  }

  return { user, userId, isAdmin, permissions, hasPermission, hasAnyPermission, hasRole, login, fetchUser, clearSession, logout }
})
