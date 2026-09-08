<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, toast, logout, notify } from './api.js'
const route = useRoute(), router = useRouter()
const admin = computed(() => session.user?.role === 'ADMIN')
watch(() => session.user, value => { if (!value && session.ready && route.path !== '/login') router.push('/login') })
async function exit() { try { await logout(); router.push('/login') } catch (e) { notify(e.message, 'error') } }
</script>
<template>
  <div v-if="toast.text" class="toast no-print" :class="toast.type" role="status">{{ toast.text }}<button @click="toast.text = ''" aria-label="关闭提示">×</button></div>
  <RouterView v-if="route.path === '/login'" />
  <div v-else class="app-shell">
    <aside class="sidebar no-print">
      <RouterLink to="/" class="brand"><span class="brand-mark">谈</span><span>谈话记录<small>师生沟通 · 有据可循</small></span></RouterLink>
      <div class="nav-label">日常工作</div>
      <nav aria-label="主导航">
        <RouterLink to="/" exact-active-class="active">工作台</RouterLink>
        <RouterLink to="/students" active-class="active">学生档案</RouterLink>
        <RouterLink to="/records" active-class="active">谈话记录</RouterLink>
        <RouterLink to="/records?followup=open">待跟进事项</RouterLink>
      </nav>
      <template v-if="admin"><div class="nav-label">系统管理</div><nav aria-label="管理导航"><RouterLink to="/templates" active-class="active">记录模板</RouterLink><RouterLink to="/users" active-class="active">账号管理</RouterLink><RouterLink to="/audit" active-class="active">操作日志</RouterLink></nav></template>
      <p class="sidebar-note">只记录实际发生的谈话。<br>生成不替代核对，归档不代替签字。</p>
    </aside>
    <div class="workspace"><header class="topbar no-print"><span>{{ route.meta.title }}</span><div class="actions"><span>{{ session.user?.display_name }} <small class="muted">{{ admin ? '管理员' : '教师' }}</small></span><RouterLink to="/password">修改密码</RouterLink><button class="plain" @click="exit">退出</button></div></header>
      <main><RouterView :key="route.path" /></main>
      <footer class="app-footer no-print">师生谈心谈话记录生成系统 · 数据仅用于授权的学生工作</footer>
    </div>
  </div>
</template>
