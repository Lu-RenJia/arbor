<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NInputNumber, useMessage, NPopconfirm, NSwitch, NCollapse, NCollapseItem } from 'naive-ui'
import { getDsPage, createDs, updateDs, deleteDs, testDs, testDsRaw } from '../../api/datasource'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import TraceDrawer from '../../components/TraceDrawer.vue'

const traceDrawer = ref<InstanceType<typeof TraceDrawer> | null>(null)

const message = useMessage()
const auth = useAuthStore()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
// 全站统一：默认按修改时间倒序
const sortField = ref('update_time')
const sortOrder = ref('DESC')
const sortOptions = [
  { label: '修改时间↓', value: 'update_time:DESC' },
  { label: '修改时间↑', value: 'update_time:ASC' },
  { label: '创建时间↓', value: 'create_time:DESC' },
  { label: '创建时间↑', value: 'create_time:ASC' },
]
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const modifyPassword = ref(false)
const passwordHint = ref('')
const testing = ref(false)
const form = ref<Record<string, any>>({ name: '', dbType: 'MYSQL', host: '', port: '', databaseName: '', username: '', password: '', remark: '' })
const poolExpanded = ref(false)

// 连接池默认值（提示用）
const poolDefaults = { minIdle: 1, maxSize: 5, idleTimeout: 180, maxLifetime: 300, connTimeout: 30 }
// NCollapse expanded-names 需要绑定变量，不能是表达式
const expandedNames = computed(() => poolExpanded.value ? ['pool'] : [])

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'dbType', width: 80 },
  { title: '地址', key: 'host' },
  { title: '端口', key: 'port', width: 80 },
  { title: '数据库', key: 'databaseName' },
  { title: '用户名', key: 'username' },
  { title: '备注', key: 'remark' },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 310, render: (row: any) => {
    return h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => handleTest(row.id) }, { default: () => '测试' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('datasource', row.id, row.name) }, { default: () => '追踪' }),
        h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确认删除？'
        })
      ]
    })
  }},
]

async function load() {
  loading.value = true
  try { const res: any = await getDsPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value }); data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 })); total.value = res.total } finally { loading.value = false }
}
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }

function openCreate() {
  isEdit.value = false; editId.value = null
  modifyPassword.value = false
  passwordHint.value = ''
  poolExpanded.value = false
  form.value = { name: '', dbType: 'MYSQL', host: '', port: '', databaseName: '', username: '', password: '', remark: '',
    poolMinIdle: null, poolMaxSize: null, poolIdleTimeoutSec: null, poolMaxLifetimeSec: null, poolConnTimeoutSec: null }
  showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  modifyPassword.value = false
  passwordHint.value = row.passwordHint || ''
  poolExpanded.value = !!(row.poolMinIdle || row.poolMaxSize)
  form.value = { name: row.name, dbType: row.dbType, host: row.host, port: String(row.port || ''), databaseName: row.databaseName, username: row.username, password: '', remark: row.remark || '',
    poolMinIdle: row.poolMinIdle ?? null, poolMaxSize: row.poolMaxSize ?? null, poolIdleTimeoutSec: row.poolIdleTimeoutSec ?? null, poolMaxLifetimeSec: row.poolMaxLifetimeSec ?? null, poolConnTimeoutSec: row.poolConnTimeoutSec ?? null }
  showModal.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) {
      const payload: Record<string, any> = { ...form.value }
      payload.modifyPassword = modifyPassword.value
      if (!modifyPassword.value) {
        delete payload.password
      }
      await updateDs(editId.value!, payload)
    } else {
      await createDs(form.value)
    }
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch { /* 拦截器已统一提示错误 */ }
}

async function handleTestConnection() {
  testing.value = true
  try {
    if (isEdit.value && !(modifyPassword.value && form.value.password)) {
      // 编辑模式且未输入新密码：用已保存的 id 测试，复用数据库中的真实密码
      await testDs(editId.value!)
    } else {
      // 新增模式 或 编辑时已输入新密码：直接传表单数据（含新密码）测试
      await testDsRaw(form.value)
    }
    message.success('连接成功')
  } catch { /* 拦截器已统一提示错误 */ }
  finally { testing.value = false }
}

async function handleDelete(id: number) { await deleteDs(id); message.success('删除成功'); load() }
async function handleTest(id: number) { await testDs(id); message.success('连接成功') }

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
          <h3>数据源配置</h3>
          <NSpace>
            <NInput v-model:value="searchKeyword" placeholder="搜索数据源名称" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
            <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
            <NButton @click="page = 1; load()">搜索</NButton>
            <NButton type="primary" @click="openCreate">新增数据源</NButton>
          </NSpace>
        </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered>
    </NDataTable>

    <NModal v-model:show="showModal" :key="String(isEdit) + String(editId)" preset="card" :title="isEdit ? '编辑数据源' : '新增数据源'" style="width: 560px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="请输入数据源名称" /></NFormItem>
        <NFormItem label="类型" required><NSelect v-model:value="form.dbType" :options="[{ label: 'MySQL', value: 'MYSQL' }, { label: 'Oracle', value: 'ORACLE' }, { label: '达梦', value: 'DAMENG' }, { label: 'PostgreSQL', value: 'POSTGRESQL' }]" /></NFormItem>
        <NFormItem label="地址" required><NInput v-model:value="form.host" placeholder="请输入主机地址" /></NFormItem>
        <NFormItem label="端口" required><NInput v-model:value="form.port" placeholder="请输入端口号" /></NFormItem>
        <NFormItem label="数据库" required><NInput v-model:value="form.databaseName" placeholder="请输入数据库名" /></NFormItem>
        <NFormItem label="用户名" required><NInput v-model:value="form.username" placeholder="请输入用户名" /></NFormItem>
        <NFormItem label="密码" :required="!isEdit">
          <template v-if="isEdit">
            <NSpace align="center">
              <NInput
                v-model:value="form.password"
                type="password"
                :placeholder="passwordHint ? '已设置密码（' + passwordHint + '）' : '未设置密码'"
                :disabled="!modifyPassword"
                style="flex: 1"
              />
              <NSwitch v-model:value="modifyPassword" size="small" />
              <span style="font-size: 12px; color: #999; white-space: nowrap">修改密码</span>
            </NSpace>
          </template>
          <NInput v-else v-model:value="form.password" type="password" placeholder="请输入密码" />
        </NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" /></NFormItem>
        <!-- 连接池配置：仅管理员可见 -->
        <template v-if="auth.isAdmin">
          <NCollapse v-model:expanded-names="expandedNames" style="margin-bottom: 12px" @update:expanded-names="(ns: string[]) => poolExpanded = ns.length > 0">
            <NCollapseItem name="pool" title="连接池配置（管理员）">
              <NFormItem label="最小空闲" :label-style="{ fontSize: '12px' }">
                <NInputNumber v-model:value="form.poolMinIdle" :min="1" :max="50" :placeholder="String(poolDefaults.minIdle)" style="width: 100%" />
              </NFormItem>
              <NFormItem label="最大连接数" :label-style="{ fontSize: '12px' }">
                <NInputNumber v-model:value="form.poolMaxSize" :min="1" :max="200" :placeholder="String(poolDefaults.maxSize)" style="width: 100%" />
              </NFormItem>
              <NFormItem label="空闲超时(秒)" :label-style="{ fontSize: '12px' }">
                <NInputNumber v-model:value="form.poolIdleTimeoutSec" :min="10" :max="3600" :placeholder="String(poolDefaults.idleTimeout)" style="width: 100%" />
              </NFormItem>
              <NFormItem label="最大存活(秒)" :label-style="{ fontSize: '12px' }">
                <NInputNumber v-model:value="form.poolMaxLifetimeSec" :min="30" :max="86400" :placeholder="String(poolDefaults.maxLifetime)" style="width: 100%" />
              </NFormItem>
              <NFormItem label="连接超时(秒)" :label-style="{ fontSize: '12px' }">
                <NInputNumber v-model:value="form.poolConnTimeoutSec" :min="1" :max="300" :placeholder="String(poolDefaults.connTimeout)" style="width: 100%" />
              </NFormItem>
            </NCollapseItem>
          </NCollapse>
        </template>
        <NSpace justify="space-between">
          <NButton @click="handleTestConnection" :loading="testing" ghost type="primary">测试连接</NButton>
          <NSpace>
            <NButton @click="showModal = false">取消</NButton>
            <NButton type="primary" @click="handleSave">确定</NButton>
          </NSpace>
        </NSpace>
      </NForm>
    </NModal>

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
