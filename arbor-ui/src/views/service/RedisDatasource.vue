<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NInputNumber, NSelect, useMessage, NPopconfirm, NSwitch, NTooltip } from 'naive-ui'
import { getRedisDsPage, createRedisDs, updateRedisDs, deleteRedisDs, testRedisDs, testRedisDsRaw } from '../../api/redisDatasource'
import { getPrefixUsageSummary, createPrefix, updatePrefix, deletePrefix } from '../../api/redisKeyPrefix'
import { formatDateTime } from '../../utils/format'
import TraceDrawer from '../../components/TraceDrawer.vue'
import RedisPrefixUsageModal from '../../components/RedisPrefixUsageModal.vue'

const traceDrawer = ref<InstanceType<typeof TraceDrawer> | null>(null)

const message = useMessage()
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
const testing = ref(false)
const form = ref<Record<string, any>>({ name: '', host: '', port: 6379, password: '', db: 0, remark: '' })

// ========== 业务前缀管理（D1）==========
// 前缀挂在数据源下：最终 key = {前缀}:{用户填的 Key}，系统不再追加 env/user/flow 前缀，
// 层级管理与溯源全部靠前缀，所以登记入口就放在数据源页。
const showPrefixModal = ref(false)
const prefixDs = ref<any>(null)
const prefixList = ref<any[]>([])
const prefixLoading = ref(false)
const showPrefixForm = ref(false)
const prefixIsEdit = ref(false)
const prefixEditId = ref<number | null>(null)
const prefixForm = ref<Record<string, any>>({ prefix: '', name: '', remark: '' })
const prefixSaving = ref(false)
// 使用明细弹窗（D11）
const showUsageModal = ref(false)
const usagePrefixId = ref<number | null>(null)
const usagePrefix = ref('')

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '名称', key: 'name' },
  { title: '地址', key: 'host' },
  { title: '端口', key: 'port', width: 70 },
  { title: 'DB', key: 'db', width: 60 },
  { title: '备注', key: 'remark' },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 350, render: (row: any) => h(NSpace, null, {
    default: () => [
      h(NButton, { size: 'small', onClick: () => handleTest(row.id) }, { default: () => '测试' }),
      h(NButton, { size: 'small', type: 'primary', tertiary: true, onClick: () => openPrefixManager(row) }, { default: () => '业务前缀' }),
      h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('redis_datasource', row.id, row.name) }, { default: () => '追踪' }),
      h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
      h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除？'
      })
    ]
  })},
]

async function load() { loading.value = true; try { const res: any = await getRedisDsPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value }); data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 })); total.value = res.total } finally { loading.value = false } }
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }
function openCreate() { isEdit.value = false; editId.value = null; modifyPassword.value = false; form.value = { name: '', host: '', port: 6379, password: '', db: 0, remark: '' }; showModal.value = true }
function openEdit(row: any) { isEdit.value = true; editId.value = row.id; modifyPassword.value = false; form.value = { name: row.name, host: row.host, port: row.port, password: '', db: row.db, remark: row.remark || '' }; showModal.value = true }
async function handleSave() {
  try {
    const payload = { ...form.value }; payload.modifyPassword = modifyPassword.value; if (isEdit.value && !modifyPassword.value) delete payload.password
    if (isEdit.value) await updateRedisDs(editId.value!, payload); else await createRedisDs(payload)
    message.success(isEdit.value ? '修改成功' : '添加成功'); showModal.value = false; load()
  } catch { /* */ }
}
/**
 * 测试连接一律以弹窗里当前填写的为准：编辑时改了地址/端口/DB 就按新的测，
 * 不能回落到库里已保存的旧配置（否则会出现「测试通过」但保存后实际连不上）。
 * 只有密码是例外：编辑态未开启「修改」时输入框禁用且不回显，前端手上没有旧密码，
 * 把 id 一起传给后端，由它回取库里已存的那份。
 */
async function handleTestConnection() {
  // 开了「修改」却没填新密码时后端会退回用原密码测，这个结果对新密码没有任何判断力，必须先说清
  if (isEdit.value && modifyPassword.value && !form.value.password) {
    message.warning('已开启「修改密码」但未填写新密码，本次按原密码测试')
  }
  testing.value = true
  try {
    await testRedisDsRaw({ ...form.value, id: isEdit.value ? editId.value : undefined, modifyPassword: modifyPassword.value })
    message.success('连接成功')
  } catch { /* 拦截器已统一提示错误 */ } finally { testing.value = false }
}
async function handleDelete(id: number) { await deleteRedisDs(id); message.success('删除成功'); load() }
async function handleTest(id: number) { await testRedisDs(id); message.success('连接成功') }

// ========== 业务前缀 ==========

function openPrefixManager(row: any) { prefixDs.value = row; showPrefixModal.value = true; loadPrefixes() }

/** 拉前缀列表：走带计数的接口，一次拿齐配置数/流程数/节点数，不逐行请求 */
async function loadPrefixes() {
  if (!prefixDs.value?.id) return
  prefixLoading.value = true
  try {
    const res: any = await getPrefixUsageSummary(prefixDs.value.id)
    prefixList.value = res || []
  } catch { prefixList.value = [] }
  finally { prefixLoading.value = false }
}

function openPrefixCreate() {
  prefixIsEdit.value = false; prefixEditId.value = null
  prefixForm.value = { prefix: '', name: '', remark: '' }
  showPrefixForm.value = true
}
function openPrefixEdit(row: any) {
  prefixIsEdit.value = true; prefixEditId.value = row.id
  prefixForm.value = { prefix: row.prefix, name: row.name, remark: row.remark || '' }
  showPrefixForm.value = true
}

async function handlePrefixSave() {
  if (!prefixForm.value.prefix?.trim()) { message.warning('请输入前缀值'); return }
  if (!prefixForm.value.name?.trim()) { message.warning('请输入前缀名称'); return }
  prefixSaving.value = true
  try {
    const payload = { ...prefixForm.value, datasourceId: prefixDs.value.id }
    if (prefixIsEdit.value) await updatePrefix(prefixEditId.value!, payload); else await createPrefix(payload)
    message.success(prefixIsEdit.value ? '修改成功' : '添加成功')
    showPrefixForm.value = false
    loadPrefixes()
  } catch { /* 拦截器已统一提示错误 */ }
  finally { prefixSaving.value = false }
}

async function handlePrefixDelete(id: number) {
  try { await deletePrefix(id); message.success('删除成功'); loadPrefixes() } catch { /* 拦截器已统一提示错误 */ }
}

/** 不可删时返回原因（供 tooltip），可删时返回空字串（D8 删除保护） */
function prefixDeleteTip(row: any): string {
  // 流程数与节点数语义不同（前者去重、后者不去重），提示里分开说才能反映真实影响面
  if ((row.nodeCount ?? 0) > 0) return `被 ${row.flowCount} 个流程的 ${row.nodeCount} 个节点使用，无法删除`
  if ((row.scriptCount ?? 0) > 0) return `被 ${row.scriptCount} 个 Redis 配置引用，无法删除`
  return ''
}

function renderPrefixDelete(row: any) {
  const tip = prefixDeleteTip(row)
  if (!tip) {
    return h(NPopconfirm, { onPositiveClick: () => handlePrefixDelete(row.id) }, {
      trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
      default: () => '确认删除该前缀？'
    })
  }
  // 禁用的 NButton 不派发鼠标事件，NTooltip 必须包一层 span 才能弹出来
  return h(NTooltip, null, {
    trigger: () => h('span', null, [h(NButton, { size: 'small', type: 'error', disabled: true }, { default: () => '删除' })]),
    default: () => tip
  })
}

function openUsage(row: any) { usagePrefixId.value = row.id; usagePrefix.value = row.prefix; showUsageModal.value = true }

const prefixColumns = [
  { title: '前缀值', key: 'prefix', width: 160, render: (row: any) => h('span', { style: 'font-family:monospace' }, row.prefix) },
  { title: '名称', key: 'name', width: 130 },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  { title: '配置数', key: 'scriptCount', width: 80, align: 'center' as const },
  // 两个计数分开列：同一流程两个节点用同一前缀时是「1 个流程 / 2 个节点」，合并会误导
  { title: '流程数', key: 'flowCount', width: 80, align: 'center' as const },
  { title: '节点数', key: 'nodeCount', width: 80, align: 'center' as const },
  { title: '操作', key: 'actions', width: 210, render: (row: any) => h(NSpace, null, {
    default: () => [
      h(NTooltip, null, {
        trigger: () => h('span', null, [
          h(NButton, { size: 'small', tertiary: true, disabled: !((row.nodeCount ?? 0) > 0 || (row.scriptCount ?? 0) > 0), onClick: () => openUsage(row) }, { default: () => '使用情况' })
        ]),
        default: () => '查看哪些流程/节点在用这个前缀'
      }),
      h(NButton, { size: 'small', onClick: () => openPrefixEdit(row) }, { default: () => '编辑' }),
      renderPrefixDelete(row)
    ]
  })},
]

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>Redis 数据源</h3>
      <NSpace>
        <NInput v-model:value="searchKeyword" placeholder="搜索数据源名称" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
        <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
        <NButton @click="page = 1; load()">搜索</NButton>
        <NButton type="primary" @click="openCreate">新增数据源</NButton>
      </NSpace>
    </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered />
    <NModal v-model:show="showModal" :key="String(isEdit)+String(editId)" preset="card" :title="isEdit ? '编辑Redis数据源' : '新增Redis数据源'" style="width: 500px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="70">
        <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="Redis数据源名称" /></NFormItem>
        <NFormItem label="地址" required><NInput v-model:value="form.host" placeholder="127.0.0.1" /></NFormItem>
        <NFormItem label="端口" required><NInputNumber v-model:value="form.port" :min="1" :max="65535" style="width:100%" /></NFormItem>
        <NFormItem label="密码" :required="!isEdit">
          <template v-if="isEdit">
            <NSpace align="center">
              <NInput v-model:value="form.password" type="password" placeholder="留空不修改" :disabled="!modifyPassword" style="flex:1" />
              <NSwitch v-model:value="modifyPassword" size="small" /><span style="font-size:12px;color:#999">修改</span>
            </NSpace>
          </template>
          <NInput v-else v-model:value="form.password" type="password" placeholder="Redis密码" />
        </NFormItem>
        <NFormItem label="DB"><NInputNumber v-model:value="form.db" :min="0" :max="15" style="width:100%" /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="备注" /></NFormItem>
        <NSpace justify="space-between">
          <NButton @click="handleTestConnection" :loading="testing" ghost type="primary">测试连接</NButton>
          <NSpace><NButton @click="showModal=false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
        </NSpace>
      </NForm>
    </NModal>

    <!-- 业务前缀管理：一个数据源下可登记多个前缀，Redis 配置必须选其中一个 -->
    <NModal v-model:show="showPrefixModal" preset="card" :title="`业务前缀 — ${prefixDs?.name || ''}`" style="width: 900px" :mask-closable="false">
      <NSpace justify="space-between" align="center" style="margin-bottom: 12px">
        <span style="color:#999;font-size:12px">完整 key = 业务前缀 + ":" + 配置的 Key；系统不再追加 env/user/flow 前缀，环境隔离请改用不同数据源</span>
        <NButton type="primary" size="small" @click="openPrefixCreate">+ 新增前缀</NButton>
      </NSpace>
      <NDataTable :columns="prefixColumns" :data="prefixList" :loading="prefixLoading" :single-line="false" size="small" bordered :scroll-x="860" />
      <template #footer>
        <NSpace justify="end"><NButton @click="showPrefixModal=false">关闭</NButton></NSpace>
      </template>
    </NModal>

    <!-- 前缀新建/编辑 -->
    <NModal v-model:show="showPrefixForm" :key="String(prefixIsEdit) + String(prefixEditId)" preset="card" :title="prefixIsEdit ? '编辑业务前缀' : '新增业务前缀'" style="width: 480px" :mask-closable="false">
      <NForm :model="prefixForm" label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem label="前缀值" required>
          <NInput v-model:value="prefixForm.prefix" placeholder="如 arbor:order" />
          <template #feedback>
            <span style="font-size:11px;color:#999">静态值，不支持 ${} 与 * ? [] 通配符，不写首尾冒号；内部可含冒号做层级</span>
          </template>
        </NFormItem>
        <NFormItem label="名称" required><NInput v-model:value="prefixForm.name" placeholder="便于识别，如 订单业务" /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="prefixForm.remark" type="textarea" :rows="2" placeholder="备注" /></NFormItem>
        <div v-if="prefixIsEdit" style="color:#f0a020;font-size:12px;line-height:1.6;margin-bottom:12px">
          修改前缀值等于换了一批 key，已写入的缓存会变成孤儿数据，需重新生成。
        </div>
        <NSpace justify="end">
          <NButton @click="showPrefixForm=false">取消</NButton>
          <NButton type="primary" @click="handlePrefixSave" :loading="prefixSaving">确定</NButton>
        </NSpace>
      </NForm>
    </NModal>

    <!-- 前缀使用明细（D11） -->
    <RedisPrefixUsageModal v-model:show="showUsageModal" :prefix-id="usagePrefixId" :prefix="usagePrefix" />

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
