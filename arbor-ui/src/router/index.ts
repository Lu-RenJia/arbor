import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
    {
      path: '/',
      component: () => import('../components/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { permission: 'dashboard:view' } },
        { path: 'flow/list', name: 'FlowList', component: () => import('../views/flow/FlowList.vue'), meta: { permission: 'flow:list' } },
        { path: 'flow/designer/:id', name: 'FlowDesigner', component: () => import('../views/flow/flow-designer-vueflow/FlowDesignerVueFlow.vue'), meta: { permission: 'flow:list' } },
        { path: 'flow/log', name: 'ExecutionLog', component: () => import('../views/log/ExecutionLogList.vue'), meta: { permission: 'log:view' } },
        { path: 'service/param', name: 'GlobalParam', component: () => import('../views/service/GlobalParam.vue'), meta: { permission: 'param:list' } },
        { path: 'service/datasource', name: 'Datasource', component: () => import('../views/service/Datasource.vue'), meta: { permission: 'datasource:list' } },
        { path: 'service/db-script', name: 'DbScript', component: () => import('../views/service/DbScript.vue'), meta: { permission: 'db-script:list' } },
        { path: 'service/program-script', name: 'ProgramScript', component: () => import('../views/service/ProgramScript.vue'), meta: { permission: 'program-script:list' } },
        { path: 'service/tool-class', name: 'ToolClass', component: () => import('../views/service/ToolClass.vue'), meta: { permission: 'program-script:list' } },
        { path: 'service/api-config', name: 'ApiConfig', component: () => import('../views/service/ApiConfig.vue'), meta: { permission: 'api-config:list' } },
        { path: 'service/redis-datasource', name: 'RedisDatasource', component: () => import('../views/service/RedisDatasource.vue'), meta: { permission: 'redis:list' } },
        { path: 'service/redis-script', name: 'RedisScript', component: () => import('../views/service/RedisScript.vue'), meta: { permission: 'redis:list' } },
        { path: 'service/environment', name: 'Environment', component: () => import('../views/service/Environment.vue'), meta: { permission: 'environment:list' } },
        { path: 'service/file', name: 'FileManagement', component: () => import('../views/service/FileManagement.vue'), meta: { permission: 'file:list' } },
        { path: 'service/department', name: 'Department', component: () => import('../views/service/Department.vue'), meta: { permission: 'department:list' } },
        { path: 'service/user', name: 'UserManagement', component: () => import('../views/service/UserManagement.vue'), meta: { permission: 'user:list' } },
        { path: 'service/role', name: 'RoleManagement', component: () => import('../views/service/RoleManagement.vue'), meta: { permission: 'role:list' } },
        { path: 'service/menu', name: 'MenuManagement', component: () => import('../views/service/MenuManagement.vue'), meta: { permission: 'menu:list' } },
        { path: 'app/my', name: 'MyApps', component: () => import('../views/application/MyApps.vue'), meta: { permission: 'app:my:list' } },
        { path: 'app/market', name: 'AppMarket', component: () => import('../views/application/AppMarket.vue'), meta: { permission: 'app:market:list' } },
        { path: 'profile', name: 'Profile', component: () => import('../views/profile/Profile.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('token')
  // 已登录状态下访问登录页 → 直接跳首页（多标签页场景：其他页面登录后本页刷新不应停留在登录页）
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }
  // 权限数据仅存内存（防 localStorage 篡改）：刷新后从后端 /api/auth/info 重新拉取
  const auth = useAuthStore()
  if (token && !auth.user) {
    await auth.fetchUser()
  }
  if (to.path === '/login') {
    next()
    return
  }
  if (!auth.user) {
    // fetchUser 失败（token 失效）→ 回登录页（fetchUser 内部已清理会话）
    next('/login')
    return
  }
  // 权限检查（admin 拥有全部权限，hasPermission 内部已处理）
  const perm = to.meta.permission as string | undefined
  if (perm && !auth.hasPermission(perm)) {
    // 避免死循环：跳转目标和当前位置相同时，改为清除会话重新登录刷新权限
    if (to.path === '/dashboard') {
      auth.clearSession()
      next('/login')
    } else {
      next('/dashboard')
    }
    return
  }
  next()
})

export default router
