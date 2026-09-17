/**
 * v-hasPermi 权限指令：无权限时移除元素
 * 用法: <n-button v-has-permi="['system:user:add']">新增</n-button>
 *        <n-button v-has-permi="['system:user:add','system:user:edit']">修改</n-button> (任一)
 */
import type { Directive } from 'vue'
import { useAuthStore } from '../stores/auth'

export const hasPermi: Directive = {
  mounted(el: HTMLElement, binding) {
    const { value } = binding
    if (value && Array.isArray(value) && value.length > 0) {
      const auth = useAuthStore()
      if (!auth.hasAnyPermission(value)) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}
