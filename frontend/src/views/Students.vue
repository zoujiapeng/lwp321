<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { api, session, notify, query, download } from '../api.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
const admin = computed(() => session.user?.role === 'ADMIN')
const rows = ref([]), total = ref(0), page = ref(1), q = ref(''), loading = ref(false), busy = ref(false)
const teachers = ref([]), show = ref(false), editId = ref(null), error = ref(''), importShow = ref(false), file = ref(null), importTeacher = ref(null)
const form = reactive({})
async function load(p = page.value) {
  loading.value = true; page.value = p
  try { const result = await api('/students?' + query({ q: q.value, page: page.value })); rows.value = result.items; total.value = result.total }
  catch (e) { notify(e.message, 'error') } finally { loading.value = false }
}
function edit(row) {
  editId.value = row?.id || null; error.value = ''
  Object.assign(form, row ? { studentNo: row.student_no, name: row.name, college: row.college, major: row.major, className: row.class_name, grade: row.grade, phone: row.phone, teacherId: row.teacher_id, active: !!row.active, version: row.version } : { studentNo: '', name: '', college: '', major: '', className: '', grade: '', phone: '', teacherId: session.user.id, active: true, version: 0 })
  show.value = true
}
async function save() {
  busy.value = true; error.value = ''
  try { await api('/students' + (editId.value ? '/' + editId.value : ''), { method: editId.value ? 'PUT' : 'POST', body: form }); show.value = false; notify('学生档案已保存'); await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function remove(row) {
  if (!window.confirm('确定删除“' + row.name + '”的档案？已有谈话记录的档案不能删除，应改为停用。')) return
  try { await api('/students/' + row.id + '?version=' + row.version, { method: 'DELETE' }); notify('档案已删除'); await load(1) } catch (e) { notify(e.message, 'error') }
}
async function exportFile(blank = false) {
  try { await download('/students/' + (blank ? 'import-template' : 'export'), blank ? '学生导入模板.csv' : '学生档案.csv') } catch (e) { notify(e.message, 'error') }
}
async function importFile() {
  if (!file.value) { error.value = '请选择CSV文件'; return }
  busy.value = true; error.value = ''
  const body = new FormData(); body.append('file', file.value); body.append('teacherId', String(importTeacher.value || session.user.id))
  try { const result = await api('/students/import', { method: 'POST', body }); importShow.value = false; notify(result.message); await load(1) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(async () => { await load(); try { teachers.value = await api('/users/teachers') } catch (e) { notify(e.message, 'error') } })
</script>
<template><div class="page-head"><div><h1>学生档案</h1><p>维护必要的基本信息，并明确每位学生的负责教师。</p></div><button class="primary" @click="edit(null)">新增学生</button></div><section class="panel"><form class="filters" @submit.prevent="load(1)"><div class="field search"><label for="student-search">搜索学生</label><input id="student-search" v-model.trim="q" maxlength="100" placeholder="姓名、学号或班级"></div><button :disabled="loading">查询</button><button type="button" @click="q = ''; load(1)">重置</button><button type="button" @click="error = ''; file = null; importTeacher = session.user.id; importShow = true">批量导入</button><button type="button" @click="exportFile(false)">导出CSV</button></form><p v-if="loading" class="muted">正在加载…</p><div v-else-if="!rows.length" class="empty"><strong>暂无符合条件的学生档案</strong><p>新增学生，或下载模板后批量导入。只填写必要信息，不导入身份证号等无关资料。</p><button @click="exportFile(true)">下载导入模板</button></div><div v-else class="table-wrap"><table><thead><tr><th>学号 / 姓名</th><th>学院 / 专业</th><th>班级</th><th>负责教师</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="student in rows" :key="student.id"><td><strong>{{ student.name }}</strong><small class="subtext">{{ student.student_no }}</small></td><td>{{ student.college || '—' }}<small class="subtext">{{ student.major || '—' }}</small></td><td>{{ student.class_name }}<small class="subtext">{{ student.grade }}</small></td><td>{{ student.teacher_name }}</td><td><span class="badge" :class="student.active ? 'success' : 'muted'">{{ student.active ? '在用' : '停用' }}</span></td><td><div class="compact-actions"><button class="text-link" @click="edit(student)">编辑</button><RouterLink :to="'/records?studentId=' + student.id">记录</RouterLink><RouterLink v-if="student.active" :to="'/records/new?studentId=' + student.id">新建谈话</RouterLink><button class="text-link danger" @click="remove(student)">删除</button></div></td></tr></tbody></table></div><Pagination :total="total" :page="page" @change="load" /></section>
  <Modal :open="show" :title="editId ? '编辑学生档案' : '新增学生档案'" @close="!busy && (show = false)"><form @submit.prevent="save"><div class="form-grid"><div><label for="student-no">学号 <span class="required">*</span></label><input id="student-no" v-model.trim="form.studentNo" required maxlength="40" pattern="[A-Za-z0-9_-]+"></div><div><label for="student-name">姓名 <span class="required">*</span></label><input id="student-name" v-model.trim="form.name" required maxlength="60"></div><div><label for="student-college">学院</label><input id="student-college" v-model.trim="form.college" maxlength="100"></div><div><label for="student-major">专业</label><input id="student-major" v-model.trim="form.major" maxlength="100"></div><div><label for="student-class">班级 <span class="required">*</span></label><input id="student-class" v-model.trim="form.className" required maxlength="80"></div><div><label for="student-grade">年级</label><input id="student-grade" v-model.trim="form.grade" maxlength="20" placeholder="如：2024级"></div><div><label for="student-phone">联系电话（选填）</label><input id="student-phone" v-model.trim="form.phone" maxlength="30"></div><div v-if="admin"><label for="student-teacher">负责教师 <span class="required">*</span></label><select id="student-teacher" v-model="form.teacherId" required><option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">{{ teacher.display_name }}</option></select></div></div><label class="checkbox"><input v-model="form.active" type="checkbox">档案在用（停用后不能新增谈话，历史记录保留）</label><p v-if="admin && editId" class="help">调整负责教师只转移学生档案，不改变历史记录的原作者和访问权限。</p><p v-if="error" class="error-text" role="alert">{{ error }}</p><div class="form-actions"><button type="button" :disabled="busy" @click="show = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '保存中…' : '保存档案' }}</button></div></form></Modal>
  <Modal :open="importShow" title="批量导入学生" @close="!busy && (importShow = false)"><p class="notice">仅接收UTF-8编码的CSV文件，一次最多500条、2MB。任何一行有误时全部不导入，不会覆盖已有学生。</p><button @click="exportFile(true)">下载空白CSV模板</button><form @submit.prevent="importFile"><label for="import-file">选择CSV文件</label><input id="import-file" type="file" accept=".csv,text/csv" required @change="file = $event.target.files[0]"><template v-if="admin"><label for="import-teacher">将本批学生分配给</label><select id="import-teacher" v-model="importTeacher"><option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">{{ teacher.display_name }}</option></select></template><p class="help">WPS中填写后，请另存为UTF-8 CSV。学号包含前导零时应按文本输入，避免表格软件自动改写。</p><p v-if="error" class="error-text" role="alert">{{ error }}</p><div class="form-actions"><button type="button" :disabled="busy" @click="importShow = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在校验并导入…' : '校验并导入' }}</button></div></form></Modal>
</template>
