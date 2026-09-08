<script setup>
import { reactive, ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, notify, query, download, datetime, categories } from '../api.js'
import Pagination from '../components/Pagination.vue'
const route = useRoute(), filters = reactive({ q: '', state: '', category: '', from: '', to: '', followup: '' })
const rows = ref([]), total = ref(0), page = ref(1), selected = ref([]), loading = ref(false), exporting = ref(false)
function fromRoute() { for (const field of Object.keys(filters)) filters[field] = String(route.query[field] || '') }
async function load(p = page.value) {
  page.value = p; loading.value = true; selected.value = []
  try { const result = await api('/records?' + query({ ...filters, page: page.value, studentId: route.query.studentId })); rows.value = result.items; total.value = result.total }
  catch (e) { notify(e.message, 'error') } finally { loading.value = false }
}
function reset() { Object.keys(filters).forEach(k => filters[k] = ''); load(1) }
async function batch() {
  exporting.value = true
  try { await download('/records/export-batch', '谈话记录批量导出.zip', 'POST', { ids: selected.value }); notify('已生成所选记录的Word压缩包') }
  catch (e) { notify(e.message, 'error') } finally { exporting.value = false }
}
function status(row) { if (row.followup_status === 'DONE') return '已完成'; if (row.followup_status === 'OPEN') return row.followup_date; return row.state === 'DRAFT' && row.followup_date ? '归档后生效' : '未设置' }
watch(() => route.query, () => { fromRoute(); load(1) })
onMounted(() => { fromRoute(); load(1) })
</script>
<template><div class="page-head"><div><h1>{{ filters.followup ? '跟进事项' : '谈话记录' }}</h1><p>{{ route.query.studentId ? '当前按学生筛选。' : '' }}草稿可修改；已归档正文固定，后续情况通过跟进追加。</p></div><RouterLink class="button primary" to="/records/new">新建谈话记录</RouterLink></div><section class="panel"><form class="filters" @submit.prevent="load(1)"><div class="field search"><label for="record-search">检索记录</label><input id="record-search" v-model.trim="filters.q" maxlength="100" placeholder="姓名、学号、主题或记录编号"></div><div class="field"><label for="record-state">记录状态</label><select id="record-state" v-model="filters.state"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="ARCHIVED">已归档</option></select></div><div class="field"><label for="record-category">谈话类型</label><select id="record-category" v-model="filters.category"><option value="">全部类型</option><option v-for="category in categories" :key="category">{{ category }}</option></select></div><div class="field"><label for="record-from">开始日期</label><input id="record-from" v-model="filters.from" type="date"></div><div class="field"><label for="record-to">结束日期</label><input id="record-to" v-model="filters.to" type="date"></div><div class="field"><label for="followup-filter">跟进状态</label><select id="followup-filter" v-model="filters.followup"><option value="">全部</option><option value="open">待跟进</option><option value="overdue">已逾期</option><option value="today">今日待跟进</option><option value="done">已完成</option></select></div><button :disabled="loading">查询</button><button type="button" @click="reset">重置</button></form><div class="section-head"><span class="small muted">已选 {{ selected.length }} 条（每次最多50条）</span><button :disabled="!selected.length || exporting" @click="batch">{{ exporting ? '生成中…' : '批量导出Word' }}</button></div><p v-if="loading" class="muted">正在加载记录…</p><div v-else-if="!rows.length" class="empty"><strong>暂无符合条件的谈话记录</strong><p>调整筛选条件，或从实际谈话要点开始新增记录。</p></div><div v-else class="table-wrap"><table><thead><tr><th><input type="checkbox" aria-label="选择本页全部记录" :checked="selected.length === rows.length" @change="selected = $event.target.checked ? rows.map(r => r.id) : []"></th><th>学生</th><th>主题 / 类型</th><th>谈话时间</th><th>记录状态</th><th>跟进安排</th><th>操作</th></tr></thead><tbody><tr v-for="record in rows" :key="record.id"><td><input v-model="selected" type="checkbox" :value="record.id" :aria-label="'选择' + record.topic"></td><td>{{ record.student_name }}<small class="subtext">{{ record.student_no }}</small></td><td><RouterLink class="record-title" :to="'/records/' + record.id">{{ record.topic }}</RouterLink><small class="subtext">{{ record.category }} · {{ record.teacher_name }}</small></td><td>{{ datetime(record.occurred_at) }}</td><td><span class="badge" :class="record.state === 'ARCHIVED' ? 'success' : ''">{{ record.state === 'ARCHIVED' ? '已归档' : '草稿' }}</span></td><td>{{ status(record) }}</td><td><RouterLink :to="'/records/' + record.id">查看</RouterLink><RouterLink v-if="record.state === 'DRAFT'" class="subtext" :to="'/records/' + record.id + '/edit'">编辑草稿</RouterLink></td></tr></tbody></table></div><Pagination :total="total" :page="page" @change="load" /></section></template>
