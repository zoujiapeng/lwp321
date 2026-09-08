<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api, notify } from '../api.js'
import Modal from '../components/Modal.vue'
const rows = ref([]), variables = ref([]), show = ref(false), editId = ref(null), busy = ref(false), error = ref(''), form = reactive({})
async function load() { try { rows.value = await api('/templates'); variables.value = await api('/templates/variables') } catch (e) { notify(e.message, 'error') } }
function edit(row, copy = false) {
  editId.value = copy ? null : row?.id || null; error.value = ''
  Object.assign(form, row ? { name: row.name + (copy ? '（副本）' : ''), title: row.title, body: row.body, active: !!row.active, version: row.version } : { name: '', title: '师生谈心谈话记录表', body: '一、谈话背景\n{background}\n\n二、学生陈述\n{studentStatement}\n\n三、教师建议\n{teacherAdvice}\n\n四、双方约定\n{agreement}\n\n五、跟进安排\n{followupDate}', active: true, version: 0 })
  show.value = true
}
async function save() {
  busy.value = true; error.value = ''
  try { await api('/templates' + (editId.value ? '/' + editId.value : ''), { method: editId.value ? 'PUT' : 'POST', body: form }); show.value = false; notify('模板已保存；已归档记录不会随模板改变'); await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(load)
</script>
<template><div class="page-head"><div><h1>记录模板</h1><p>用简单的占位符安排内容顺序，无须修改代码。</p></div><button class="primary" @click="edit(null)">新增模板</button></div><section class="panel"><p class="notice">模板只是排版和文字组织规则，不是自动写作模型。必须保留学生陈述、教师建议、双方约定。停用模板不影响历史归档文件。</p><div class="table-wrap"><table><thead><tr><th>模板名称</th><th>导出标题</th><th>状态</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><strong>{{ row.name }}</strong></td><td>{{ row.title }}</td><td><span class="badge" :class="row.active ? 'success' : 'muted'">{{ row.active ? '启用' : '停用' }}</span></td><td>{{ row.version + 1 }}</td><td><div class="compact-actions"><button class="text-link" @click="edit(row)">编辑</button><button class="text-link" @click="edit(row, true)">复制</button></div></td></tr></tbody></table></div></section><Modal :open="show" :title="editId ? '编辑记录模板' : '新增记录模板'" wide @close="!busy && (show = false)"><form @submit.prevent="save"><div class="form-grid"><div><label for="template-name">模板名称</label><input id="template-name" v-model.trim="form.name" maxlength="80" required></div><div><label for="template-title">Word文档标题</label><input id="template-title" v-model.trim="form.title" maxlength="120" required></div></div><label for="template-body">正文模板</label><textarea id="template-body" v-model="form.body" rows="15" maxlength="10000" required></textarea><p class="help">变量原样保留大括号。点击下方按钮可将变量追加到正文末尾。</p><div class="template-tokens"><button v-for="variable in variables" :key="variable" type="button" @click="form.body += '{' + variable + '}'">{{ '{' + variable + '}' }}</button></div><p class="help">studentStatement：学生陈述；teacherAdvice：教师建议；agreement：双方约定；background：背景；followupDate：跟进日期。其他变量对应基本信息。</p><label class="checkbox"><input v-model="form.active" type="checkbox">启用此模板</label><p v-if="error" class="error-text" role="alert">{{ error }}</p><div class="form-actions"><button type="button" :disabled="busy" @click="show = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '保存中…' : '保存模板' }}</button></div></form></Modal></template>
