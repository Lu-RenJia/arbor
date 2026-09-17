<script setup lang="ts">
import { h, ref, watch, onMounted, type Component } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NLayout, NLayoutSider, NLayoutContent, NLayoutHeader, NMenu, NSpace, NIcon, NAvatar, NDropdown, NSpin } from 'naive-ui'
import {
  DashboardOutlined, ApartmentOutlined, FileTextOutlined, SettingOutlined,
  DatabaseOutlined, CodeOutlined, ApiOutlined, HomeOutlined,
  AppstoreOutlined, UserOutlined, TeamOutlined, SafetyOutlined, MenuOutlined,
  EnvironmentOutlined, CloudServerOutlined, RocketOutlined
} from '@vicons/antd'
import { useAuthStore } from '../stores/auth'
import { getMyMenus } from '../api/menu'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// 图标名 → 组件映射
const iconMap: Record<string, Component> = {
  DashboardOutlined, HomeOutlined, ApartmentOutlined, FileTextOutlined, SettingOutlined,
  DatabaseOutlined, CodeOutlined, ApiOutlined, AppstoreOutlined,
  UserOutlined, TeamOutlined, SafetyOutlined, MenuOutlined,
  EnvironmentOutlined, CloudServerOutlined, RocketOutlined,
}

function renderIcon(iconName: string | null) {
  const icon = iconName ? iconMap[iconName] : null
  if (!icon) return undefined
  return () => h(NIcon, null, { default: () => h(icon) })
}

// 将 API 菜单树转为 Naive UI menu options
interface MenuItem {
  id: number
  parentId: number
  name: string
  path: string | null
  icon: string | null
  permissionCode: string | null
  sortOrder: number
  visible: number
  children: MenuItem[]
}

function toMenuOptions(menus: MenuItem[]): any[] {
  return menus.map(m => {
    const hasChildren = m.children && m.children.length > 0
    const option: any = {
      label: m.name,
      key: m.path || `menu_${m.id}`,
      icon: renderIcon(m.icon),
    }
    if (hasChildren) {
      option.children = toMenuOptions(m.children)
    }
    return option
  })
}

const menuOptions = ref<any[]>([])
const menuLoading = ref(true)
const activeKey = ref(route.path)

watch(() => route.path, (newPath) => {
  activeKey.value = newPath
})

const MENU_CACHE_KEY = 'menus'

async function loadMenus() {
  try {
    // 每次从后端拉取（菜单按权限过滤，不缓存到 localStorage，防篡改且变更即时生效）
    const menus: any = await getMyMenus()
    menuOptions.value = toMenuOptions(menus || [])
  } catch {
    // fallback: empty menu
  } finally {
    menuLoading.value = false
  }
}

onMounted(() => {
  loadMenus()
  auth.fetchUser() // 每次进入刷新权限，配合后端 Redis 实时生效
})

function handleMenuChange(key: string) {
  if (key.startsWith('/')) {
    router.push(key)
  }
}

async function handleLogout() {
  localStorage.removeItem(MENU_CACHE_KEY) // 清理历史版本遗留的菜单缓存
  await auth.logout()
  router.push('/login')
}

function handleDropdownSelect(key: string) {
  if (key === 'logout') handleLogout()
  else if (key === 'profile') router.push('/profile')
}
</script>

<template>
  <NLayout style="height: 100vh" has-sider>
    <NLayoutSider bordered width="220">
      <div style="padding: 16px; font-size: 18px; font-weight: bold; color: var(--primary-color)">
        ⚡ Arbor
      </div>
      <NSpin :show="menuLoading">
        <NMenu
          :value="activeKey"
          :options="menuOptions"
          @update:value="handleMenuChange"
        />
      </NSpin>
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader bordered style="height: 48px; display: flex; align-items: center; justify-content: flex-end; padding: 0 16px">
        <NDropdown trigger="click" :options="[{ label: '个人中心', key: 'profile' }, { type: 'divider', key: 'd1' }, { label: '退出登录', key: 'logout' }]" @select="handleDropdownSelect">
          <NSpace align="center" style="cursor: pointer">
            <span>{{ auth.user?.nickname || auth.user?.username || '管理员' }}</span>
            <NAvatar size="small" style="background: var(--primary-color)">{{ (auth.user?.nickname || auth.user?.username || 'A').charAt(0) }}</NAvatar>
          </NSpace>
        </NDropdown>
      </NLayoutHeader>
      <NLayoutContent style="padding: 16px">
        <router-view />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>
