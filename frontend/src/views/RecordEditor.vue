<script setup>
import { reactive, ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, notify, query, categories } from '../api.js'
const route = useRoute(), router = useRouter(), id = route.params.id
const students = ref([]), templates = ref([]), studentSearch = ref(''), busy = ref(false), error = ref(''), dirty = ref(false), loading = ref(true)
let removeNavigationGuard = () => {}
const beijing = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date()).replace(' ', 'T')
const form = reactive({ studentId: route.query.studentId ? Number(route.query.studentId) : '', templateId: '', topic: '', category: '学业发展', occurredAt: beijing, durationMinutes: 30, place: '', mode: '面谈', background: '', studentStatement: '', teacherAdvice: '', agreement: '', followupDate: '', content: '', version: 0 })
watch(form, () => { if (!loading.value) dirty.value = true }, { deep: true, flush: 'sync' })
async function searchStudents() {
  try {
    const selected = students.value.find(s => s.id === form.studentId)
    const result = await api('/students?' + query({ q: studentSearch.value, size: 100, activeOnly: true }))
    students.value = result.items
    if (selected && !students.value.some(s => s.id === selected.id)) students.value.unshift(selected)
  } catch (e) { notify(e.message, 'error') }
}
async function generate() {
  if (form.content.trim() && !window.confirm('重新生成将覆盖右侧正文中的人工修改，左侧原始要点保留。是否继续？')) return
  error.value = ''; busy.value = true
  try { const result = await api('/records/generate' + (id ? '?recordId=' + id : ''), { method: 'POST', body: form }); form.content = result.content; notify(result.message) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function save() {
  busy.value = true; error.value = ''
  try { const result = await api('/records' + (id ? '/' + id : ''), { method: id ? 'PUT' : 'POST', body: form }); dirty.value = false; notify('草稿已保存。核对无误后可在详情页归档。'); router.push('/records/' + result.id) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
function warn(event) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeUnmount(() => { window.removeEventListener('beforeunload', warn); removeNavigationGuard() })
onMounted(async () => {
  window.addEventListener('beforeunload', warn)
  removeNavigationGuard = router.beforeEach((to, from) => {
    if (to.fullPath !== from.fullPath && dirty.value) return window.confirm('当前修改尚未保存，确定离开吗？')
    return true
  })
  try {
    templates.value = (await api('/templates')).filter(t => t.active); form.templateId = templates.value[0]?.id || ''
    if (id) {
      const row = await api('/records/' + id)
      if (row.state !== 'DRAFT') { router.replace('/records/' + id); return }
      Object.assign(form, { studentId: row.student_id, templateId: row.template_id, topic: row.topic, category: row.category, occurredAt: row.occurred_at.slice(0, 16), durationMinutes: row.duration_minutes, place: row.place, mode: row.mode, background: row.background, studentStatement: row.student_statement, teacherAdvice: row.teacher_advice, agreement: row.agreement, followupDate: row.followup_date || '', content: row.content, version: row.version })
      students.value = [{ id: row.student_id, name: row.student_name, student_no: row.student_no, class_name: row.class_name }]
    }
    await searchStudents()
    if (form.studentId && !students.value.some(s => s.id === form.studentId)) {
      const selected = await api('/students/' + form.studentId); students.value.unshift(selected)
    }
    await nextTick(); dirty.value = false
  } catch (e) { error.value = e.message } finally { loading.value = false }
})
</script>
<template>
  <div class="page-head"><div><h1>{{ id ? '编辑谈话草稿' : '新建谈话记录' }}</h1><p>如实填写要点，生成后可人工调整正文。日期与时间统一按北京时间填写。</p></div><RouterLink class="button" to="/records">返回列表</RouterLink></div>
  <p v-if="loading" class="panel">正在加载档案和模板，请稍候…</p>
  <form v-else @submit.prevent="save">
    <div class="editor-layout"><section class="panel">
      <div class="section-head"><h2><span class="step-tag">01</span>基本信息与原始要点</h2></div>
      <label for="student-keyword">查找学生</label><div class="actions" style="flex-wrap:nowrap"><input id="student-keyword" v-model="studentSearch" placeholder="输入姓名或学号，点击查找" maxlength="100" @keydown.enter.prevent="searchStudents"><button type="button" @click="searchStudents">查找</button></div>
      <label for="talk-student">选择学生 <span class="required">*</span></label><select id="talk-student" v-model="form.studentId" required><option value="" disabled>请选择学生</option><option v-for="student in students" :key="student.id" :value="student.id">{{ student.name }} · {{ student.student_no }} · {{ student.class_name }}</option></select><p class="help">列表最多显示100条；找不到时请输入完整学号查找。</p>
      <label for="talk-topic">谈话主题 <span class="required">*</span></label><input id="talk-topic" v-model.trim="form.topic" required maxlength="120" placeholder="如：课程学习计划沟通">
      <div class="form-grid"><div><label for="talk-category">谈话类型</label><select id="talk-category" v-model="form.category"><option v-for="category in categories" :key="category">{{ category }}</option></select></div><div><label for="talk-mode">谈话方式</label><select id="talk-mode" v-model="form.mode"><option>面谈</option><option>电话</option><option>视频</option><option>其他</option></select></div><div><label for="talk-time">实际谈话时间 <span class="required">*</span></label><input id="talk-time" v-model="form.occurredAt" type="datetime-local" required></div><div><label for="talk-duration">时长（分钟）</label><input id="talk-duration" v-model.number="form.durationMinutes" type="number" min="1" max="480" required></div></div>
      <label for="talk-place">谈话地点 / 联系场景 <span class="required">*</span></label><input id="talk-place" v-model.trim="form.place" required maxlength="120" placeholder="如：辅导员办公室；电话联系">
      <label for="talk-background">谈话背景（选填）</label><textarea id="talk-background" v-model="form.background" rows="3" maxlength="2000" placeholder="此次沟通的由来，不填写推测性的判断"></textarea>
      <label for="talk-statement">学生陈述 <span class="required">生成与归档必填</span></label><textarea id="talk-statement" v-model="form.studentStatement" rows="4" maxlength="4000" placeholder="记录学生实际表达的情况、需求和困惑"></textarea>
      <label for="talk-advice">教师建议 <span class="required">生成与归档必填</span></label><textarea id="talk-advice" v-model="form.teacherAdvice" rows="4" maxlength="4000" placeholder="只填写本次谈话中实际提出的建议"></textarea>
      <label for="talk-agreement">双方约定 <span class="required">生成与归档必填</span></label><textarea id="talk-agreement" v-model="form.agreement" rows="4" maxlength="4000" placeholder="写清谁在什么时间做什么；若未形成约定，请如实说明"></textarea>
      <label for="talk-followup">计划跟进日期（选填）</label><input id="talk-followup" v-model="form.followupDate" type="date"><p class="help">提醒在归档后生效。系统不会依据文字自动给学生贴标签。</p>
    </section><section class="panel">
      <div class="section-head"><h2><span class="step-tag">02</span>生成与人工核对</h2></div><label for="talk-template">记录模板</label><select id="talk-template" v-model="form.templateId" required><option value="" disabled>请选择模板</option><option v-for="template in templates" :key="template.id" :value="template.id">{{ template.name }}</option></select>
      <p class="notice">模板生成只整理左侧已经填写的事实，不调用外部模型。更改要点后，需重新生成或同步修改正文；不会自动覆盖人工修改。</p>
      <button type="button" class="primary full" :disabled="busy" @click="generate">{{ busy ? '正在处理…' : '根据要点生成正文' }}</button>
      <label for="talk-content">记录正文（可编辑）</label><textarea id="talk-content" v-model="form.content" class="body-editor" maxlength="20000" placeholder="点击上方生成正文，或根据实际谈话手动整理。空正文可暂存草稿，但不能归档。"></textarea><p class="help">{{ form.content.length }} / 20000 字符。Word导出使用独立信息表和自然分页的正文。</p>
    </section></div>
    <div class="sticky-actions"><span class="small muted">{{ dirty ? '有尚未保存的修改' : '修改后请及时保存' }}</span><div class="actions"><RouterLink class="button" to="/records">取消</RouterLink><button class="primary" :disabled="busy">{{ busy ? '保存中…' : '保存草稿并查看' }}</button></div><p v-if="error" class="error-text full" role="alert">{{ error }}</p></div>
  </form>
</template>
