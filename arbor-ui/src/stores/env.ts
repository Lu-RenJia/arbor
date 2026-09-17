import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 环境状态 Store（P2-12）— 统一管理流程设计器/列表页的环境选择。
 * 选择偏好属于纯 UX 记忆（非安全数据），持久化到 localStorage 以便刷新后保留。
 */
export const useEnvStore = defineStore('env', () => {
  const ENV_KEY = 'flow_designer_env_id'

  /** 流程设计器当前选中的环境（null 表示未选择/默认） */
  const flowDesignerEnvId = ref<number | null>(
    Number(localStorage.getItem(ENV_KEY) || 0) || null
  )

  function setFlowDesignerEnvId(id: number) {
    flowDesignerEnvId.value = id
    localStorage.setItem(ENV_KEY, String(id))
  }

  return { flowDesignerEnvId, setFlowDesignerEnvId }
})
