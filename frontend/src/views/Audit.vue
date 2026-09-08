<script setup>
import { ref, onMounted } from 'vue'
import { api, notify, query, datetime } from '../api.js'
import Pagination from '../components/Pagination.vue'
const rows = ref([]), total = ref(0), page = ref(1), q = ref('')
const labels = { LOGIN: '登录', LOGIN_FAILED: '登录失败', CHANGE_PASSWORD: '修改密码', CREATE_USER: '创建账号', UPDATE_USER: '更新账号', RESET_PASSWORD: '重置密码', SAVE_STUDENT: '保存学生', DELETE_STUDENT: '删除学生', IMPORT_STUDENTS: '导入学生', EXPORT_STUDENTS: '导出学生', CREATE_TEMPLATE: '创建模板', UPDATE_TEMPLATE: '更新模板', SAVE_DRAFT: '保存草稿', DELETE_DRAFT: '删除草稿', VIEW_RECORD: '查看记录', ARCHIVE_RECORD: '归档记录', ADD_FOLLOWUP: '追加跟进', EXPORT_RECORD: '导出记录' }
async function load(p = page.value) {
  page.value = p
  try { const result = await api('/audit?' + query({ q: q.value, page: page.value })); rows.value = result.items; total.value = result.total } catch (e) { notify(e.message, 'error') }
}
onMounted(() => load())
</script>
<template><div class="page-head"><div><h1>操作日志</h1><p>记录谁在什么时间进行了哪些操作，不记录密码和谈话正文。</p></div></div><section class="panel"><form class="filters" @submit.prevent="load(1)"><div class="field search"><label for="audit-search">查找日志</label><input id="audit-search" v-model.trim="q" maxlength="100" placeholder="姓名或操作代码，如 ARCHIVE、EXPORT"></div><button>查询</button><button type="button" @click="q = ''; load(1)">重置</button></form><div v-if="!rows.length" class="empty">暂无匹配日志</div><div v-else class="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>说明</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ datetime(row.created_at) }}</td><td>{{ row.actor_name }}</td><td>{{ labels[row.action] || row.action }}<small class="subtext">{{ row.action }}</small></td><td>{{ row.target_type }} {{ row.target_id ? '#' + row.target_id : '' }}</td><td>{{ row.detail || '—' }}</td></tr></tbody></table></div><Pagination :total="total" :page="page" @change="load" /></section></template>
