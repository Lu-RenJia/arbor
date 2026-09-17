/**
 * v-hasRole 角色指令：角色不匹配时移除元素
 * 用法: <n-button v-has-role="['ADMIN']">删除</n-button>
 */
import type { Directive } from 'vue'
import { useAuthStore } from '../stores/auth'

export const hasRole: Directive = {
  mounted(el: HTMLElement, binding) {
    const { value } = binding
    if (value && Array.isArray(value) && value.length > 0) {
      const auth = useAuthStore()
      if (!value.includes(auth.user?.role || '')) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}
