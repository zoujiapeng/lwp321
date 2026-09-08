<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, session, clearCsrf, notify } from '../api.js'
const oldPassword = ref(''), newPassword = ref(''), confirmPassword = ref(''), busy = ref(false), error = ref(''), router = useRouter()
async function submit() {
  error.value = ''
  if (newPassword.value !== confirmPassword.value) { error.value = '两次新密码不一致'; return }
  busy.value = true
  try { const result = await api('/auth/password', { method: 'POST', body: { oldPassword: oldPassword.value, newPassword: newPassword.value } }); clearCsrf(); session.user = null; notify(result.message); router.push('/login') }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
</script>
<template><div class="page-head"><div><h1>修改密码</h1><p>密码至少12个字符，同时包含字母和数字。修改后所有旧登录状态失效。</p></div></div><section class="panel narrow"><p v-if="session.user?.must_change" class="notice">首次登录或密码已被重置，请先修改初始密码，再使用业务功能。</p><form @submit.prevent="submit"><label for="oldPassword">原密码</label><input id="oldPassword" v-model="oldPassword" type="password" autocomplete="current-password" required><label for="newPassword">新密码</label><input id="newPassword" v-model="newPassword" type="password" autocomplete="new-password" minlength="12" maxlength="72" required><label for="confirmPassword">再次输入新密码</label><input id="confirmPassword" v-model="confirmPassword" type="password" autocomplete="new-password" required><p class="error-text" role="alert">{{ error }}</p><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '修改并重新登录' }}</button></form></section></template>
