<script setup>
import { ref, onMounted, computed } from 'vue'
import { api, notify, datetime } from '../api.js'
const stats = ref(null), recent = ref([]), loading = ref(true)
const maxMonth = computed(() => Math.max(1, ...(stats.value?.months || []).map(m => m.total)))
async function load() {
  loading.value = true
  try { const [s, r] = await Promise.all([api('/dashboard'), api('/records?size=5')]); stats.value = s; recent.value = r.items }
  catch (e) { notify(e.message, 'error') } finally { loading.value = false }
}
onMounted(load)
</script>
<template><div class="page-head"><div><p class="eyebrow">日常工作</p><h1>工作台</h1><p>关注谈话进展，也关注谈话之后的行动。</p></div><RouterLink class="button primary" to="/records/new">新建谈话记录</RouterLink></div>
  <p v-if="loading" class="panel">正在加载工作数据…</p>
  <template v-else-if="stats"><div class="stat-grid"><RouterLink to="/students" class="stat-card"><span>在用学生档案</span><strong>{{ stats.students }}</strong><small>按当前账号权限统计</small></RouterLink><RouterLink to="/records?state=ARCHIVED" class="stat-card"><span>已归档记录</span><strong>{{ stats.archived }}</strong><small>人工核对后固定正文</small></RouterLink><RouterLink to="/records?state=DRAFT" class="stat-card"><span>待整理草稿</span><strong>{{ stats.drafts }}</strong><small>尚未归档，不作为正式记录</small></RouterLink><RouterLink to="/records?followup=open" class="stat-card"><span>待跟进事项</span><strong>{{ stats.openFollowups }}</strong><small>逾期 {{ stats.overdue }} · 今日 {{ stats.today }}</small></RouterLink></div>
  <div class="dashboard-grid"><section class="panel"><div class="section-head"><h2>跟进提醒</h2><span class="small muted">{{ stats.todayDate }}</span></div><div class="followup-cards"><RouterLink to="/records?followup=overdue"><strong>{{ stats.overdue }}</strong><span>已逾期</span><small>优先检查未完成事项</small></RouterLink><RouterLink to="/records?followup=today"><strong>{{ stats.today }}</strong><span>今日待跟进</span><small>按约定及时联系学生</small></RouterLink></div><p class="muted small">提醒只依据人工设置的日期和完成状态，不代表系统对学生作出的风险判断。</p></section><section class="panel"><div class="section-head"><h2>近6个月谈话数量</h2><span class="small muted">仅统计已归档记录</span></div><div class="month-chart"><div v-for="month in stats.months" :key="month.month" class="month-column"><span>{{ month.total }}</span><div class="month-bar" :style="{ height: Math.max(3, month.total / maxMonth * 84) + 'px' }"></div><small>{{ month.month.slice(5) }}月</small></div></div></section></div>
  <section class="panel"><div class="section-head"><h2>最近谈话</h2><RouterLink to="/records">查看全部</RouterLink></div><div v-if="!recent.length" class="empty"><strong>还没有谈话记录</strong><p>先建立学生档案，再录入实际谈话要点，生成并核对记录。</p><RouterLink class="button" to="/students">前往学生档案</RouterLink></div><div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>谈话主题</th><th>谈话时间</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="record in recent" :key="record.id"><td>{{ record.student_name }}<small class="subtext">{{ record.class_name }}</small></td><td>{{ record.topic }}</td><td>{{ datetime(record.occurred_at) }}</td><td><span class="badge" :class="record.state === 'ARCHIVED' ? 'success' : ''">{{ record.state === 'ARCHIVED' ? '已归档' : '草稿' }}</span></td><td><RouterLink :to="'/records/' + record.id">查看</RouterLink></td></tr></tbody></table></div></section>
  <section class="workflow-note"><strong>建档 → 录入要点 → 生成草稿 → 人工核对 → 归档导出 → 跟进</strong><p>系统使用可编辑模板整理已录入的事实，不补写没有发生的谈话，不自动作出心理判断。</p></section></template>
  <section v-else class="panel empty"><p>暂时无法加载工作台</p><button @click="load">重新加载</button></section>
</template>
