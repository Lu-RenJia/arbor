<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NTabs, NTabPane, NForm, NFormItem, NInput, NRadioGroup, NRadio, NButton, NSpace, useMessage, NDescriptions, NDescriptionsItem } from 'naive-ui'
import { getProfile, updateProfile, changePassword } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const auth = useAuthStore()

// ====== 基本资料 ======
const profile = ref<any>({})
const profileForm = ref({ nickname: '', sex: '2' })
const profileLoading = ref(false)

async function loadProfile() {
  try {
    const res: any = await getProfile()
    profile.value = res
    profileForm.value = { nickname: res.nickname || '', sex: res.sex || '2' }
  } catch { /* ignore */ }
}

async function handleUpdateProfile() {
  if (!profileForm.value.nickname?.trim()) {
    message.warning('昵称不能为空')
    return
  }
  profileLoading.value = true
  try {
    await updateProfile(profileForm.value)
    message.success('资料修改成功')
    await loadProfile()
    // 同步更新顶部导航栏显示的昵称
    auth.fetchUser()
  } catch (e: any) {
    message.error(e?.message || '修改失败')
  } finally {
    profileLoading.value = false
  }
}

// ====== 修改密码 ======
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdLoading = ref(false)

/** 密码规则：6-15位，可由大小写字母、数字、常见特殊符号组成 */
const PWD_REGEX = /^[A-Za-z0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?~`]{6,15}$/

function validatePwd(): string | null {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword) return '请输入旧密码'
  if (!newPassword) return '请输入新密码'
  if (newPassword.length < 6) return '新密码长度不能少于6位'
  if (newPassword.length > 15) return '新密码长度不能超过15位'
  if (!PWD_REGEX.test(newPassword)) return '新密码包含非法字符，仅支持大小写字母、数字及常见特殊符号'
  if (newPassword !== confirmPassword) return '两次输入的密码不一致'
  return null
}

async function handleChangePassword() {
  const err = validatePwd()
  if (err) { message.warning(err); return }
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
    message.success('密码修改成功，请重新登录')
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    // 修改密码后强制重新登录
    setTimeout(() => { auth.logout(); window.location.hash = '#/login' }, 1500)
  } catch (e: any) {
    message.error(e?.message || '密码修改失败')
  } finally {
    pwdLoading.value = false
  }
}

onMounted(() => { loadProfile() })
</script>

<template>
  <div style="max-width: 700px; margin: 0 auto">
    <NCard title="个人中心">
      <NTabs type="line" animated>
        <!-- 基本资料 -->
        <NTabPane name="profile" tab="基本资料">
          <NDescriptions :column="1" label-placement="left" bordered size="small" style="margin-bottom: 20px">
            <NDescriptionsItem label="用户名">{{ profile.username }}</NDescriptionsItem>
            <NDescriptionsItem label="手机号">{{ profile.phone || '-' }}</NDescriptionsItem>
            <NDescriptionsItem label="邮箱">{{ profile.email || '-' }}</NDescriptionsItem>
            <NDescriptionsItem label="创建时间">{{ formatDateTime(profile.createTime) }}</NDescriptionsItem>
          </NDescriptions>

          <NForm :model="profileForm" label-placement="left" label-width="80" style="max-width: 400px">
            <NFormItem label="用户昵称" required>
              <NInput v-model:value="profileForm.nickname" placeholder="请输入昵称" :maxlength="30" show-count />
            </NFormItem>
            <NFormItem label="性别">
              <NRadioGroup v-model:value="profileForm.sex">
                <NRadio value="0">男</NRadio>
                <NRadio value="1">女</NRadio>
                <NRadio value="2">未知</NRadio>
              </NRadioGroup>
            </NFormItem>
            <NFormItem>
              <NButton type="primary" :loading="profileLoading" @click="handleUpdateProfile">保存修改</NButton>
            </NFormItem>
          </NForm>
        </NTabPane>

        <!-- 修改密码 -->
        <NTabPane name="password" tab="修改密码">
          <NForm :model="pwdForm" label-placement="left" label-width="90" style="max-width: 420px">
            <NFormItem label="旧密码" required>
              <NInput v-model:value="pwdForm.oldPassword" type="password" show-password-on="click" placeholder="请输入旧密码" />
            </NFormItem>
            <NFormItem label="新密码" required>
              <NInput v-model:value="pwdForm.newPassword" type="password" show-password-on="click" placeholder="请输入新密码" />
            </NFormItem>
            <NFormItem label="确认密码" required>
              <NInput v-model:value="pwdForm.confirmPassword" type="password" show-password-on="click" placeholder="请再次输入新密码" />
            </NFormItem>
            <NFormItem>
              <NSpace>
                <NButton type="primary" :loading="pwdLoading" @click="handleChangePassword">确认修改</NButton>
                <NButton @click="pwdForm = { oldPassword: '', newPassword: '', confirmPassword: '' }">重置</NButton>
              </NSpace>
            </NFormItem>
          </NForm>
          <div style="color: #999; font-size: 12px; margin-top: 8px; line-height: 1.8">
            <p style="margin: 0">密码要求：</p>
            <ul style="margin: 4px 0 0 0; padding-left: 18px">
              <li>长度 6 ~ 15 位</li>
              <li>可包含：大小写英文字母（A-Z, a-z）、数字（0-9）</li>
              <li>可包含特殊符号：!@#$%^&*()_+-=[]{};':"\\|,.&lt;&gt;/?~`</li>
            </ul>
          </div>
        </NTabPane>
      </NTabs>
    </NCard>
  </div>
</template>
