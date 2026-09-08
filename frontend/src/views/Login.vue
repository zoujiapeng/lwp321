<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, session } from '../api.js'
const username = ref(''), password = ref(''), busy = ref(false), error = ref(''), router = useRouter()
async function submit() {
  busy.value = true; error.value = ''
  try { await login(username.value, password.value); router.push(session.user.must_change ? '/password' : '/') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template><div class="login-page"><section class="login-intro"><span class="brand-mark large">谈</span><p class="eyebrow">学生工作 / 谈话档案</p><h1>把沟通留在心上，<br>把记录落到实处。</h1><p>从要点整理到人工核对，从规范归档到持续跟进。<br>一套简单、可追溯的日常工作流程。</p><div class="login-steps"><span>01 录入要点</span><span>02 核对归档</span><span>03 持续跟进</span></div></section><section class="login-card"><h2>登录工作空间</h2><p class="muted">师生谈心谈话记录生成系统</p><form @submit.prevent="submit"><label for="username">账号</label><input id="username" v-model.trim="username" autocomplete="username" required maxlength="40" autofocus placeholder="输入管理员分配的账号"><label for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="current-password" required maxlength="72" placeholder="输入登录密码"><p v-if="error" class="error-text" role="alert">{{ error }}</p><button class="primary full" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button></form><p class="small muted">首次使用请联系管理员获取账号。初始密码登录后必须修改。请勿在公共设备上保留账号登录状态。</p></section></div></template>
