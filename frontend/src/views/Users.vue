<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api, session, notify, query } from '../api.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
const rows = ref([]), total = ref(0), page = ref(1), q = ref(''), busy = ref(false), show = ref(false), resetShow = ref(false), editId = ref(null), error = ref(''), resetUser = ref(null), resetPassword = ref(''), form = reactive({})
async function load(p = page.value) {
  page.value = p
  try { const result = await api('/users?' + query({ q: q.value, page: page.value })); rows.value = result.items; total.value = result.total } catch (e) { notify(e.message, 'error') }
}
function edit(row) {
  editId.value = row?.id || null; error.value = ''
  Object.assign(form, row ? { username: row.username, displayName: row.display_name, role: row.role, enabled: !!row.enabled, password: '' } : { username: '', displayName: '', role: 'TEACHER', enabled: true, password: '' })
  show.value = true
}
async function save() {
  busy.value = true; error.value = ''
  try { await api('/users' + (editId.value ? '/' + editId.value : ''), { method: editId.value ? 'PUT' : 'POST', body: form }); show.value = false; notify(editId.value ? '账号已更新，旧登录状态失效' : '账号已创建，请单独告知使用者初始密码'); await load(1) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function reset() {
  busy.value = true; error.value = ''
  try { const result = await api('/users/' + resetUser.value.id + '/reset-password', { method: 'POST', body: { password: resetPassword.value } }); resetPassword.value = ''; resetShow.value = false; notify(result.message); await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(() => load())
</script>
<template><div class="page-head"><div><h1>账号管理</h1><p>教师只能访问负责的学生和本人记录；管理员负责账号、模板和全局档案管理。</p></div><button class="primary" @click="edit(null)">新增账号</button></div><section class="panel"><form class="filters" @submit.prevent="load(1)"><div class="field search"><label for="user-search">搜索账号</label><input id="user-search" v-model.trim="q" maxlength="100" placeholder="账号或姓名"></div><button>查询</button><button type="button" @click="q = ''; load(1)">重置</button></form><div v-if="!rows.length" class="empty">暂无匹配账号</div><div v-else class="table-wrap"><table><thead><tr><th>账号</th><th>姓名</th><th>角色</th><th>状态</th><th>初始密码</th><th>操作</th></tr></thead><tbody><tr v-for="user in rows" :key="user.id"><td>{{ user.username }}</td><td>{{ user.display_name }}</td><td>{{ user.role === 'ADMIN' ? '管理员' : '教师' }}</td><td><span class="badge" :class="user.enabled ? 'success' : 'muted'">{{ user.enabled ? '启用' : '停用' }}</span></td><td>{{ user.must_change ? '待修改' : '已修改' }}</td><td><div class="compact-actions"><button class="text-link" @click="edit(user)">编辑</button><button v-if="user.id !== session.user.id" class="text-link" @click="resetUser = user; resetPassword = ''; error = ''; resetShow = true">重置密码</button></div></td></tr></tbody></table></div><Pagination :total="total" :page="page" @change="load" /></section><Modal :open="show" :title="editId ? '编辑账号' : '新增账号'" @close="!busy && (show = false)"><form @submit.prevent="save"><label for="account-name">登录账号</label><input id="account-name" v-model.trim="form.username" required pattern="[A-Za-z0-9_.\-]{3,40}" maxlength="40" :readonly="!!editId" autocomplete="off"><p class="help">3至40位字母、数字、下划线、点或短横线；创建后不可改名。</p><label for="account-display">姓名 / 显示名称</label><input id="account-display" v-model.trim="form.displayName" required maxlength="60"><template v-if="!editId"><label for="account-password">初始密码</label><input id="account-password" v-model="form.password" type="password" required minlength="12" maxlength="72" autocomplete="new-password"><p class="help">至少12个字符，包含字母和数字。请通过可靠方式单独告知使用者，不在备注中记录密码。</p></template><label for="account-role">角色</label><select id="account-role" v-model="form.role"><option value="TEACHER">教师</option><option value="ADMIN">管理员</option></select><label class="checkbox"><input v-model="form.enabled" type="checkbox">启用账号</label><p class="help">账号停用或角色变更后，旧会话立即失效；学生档案和历史记录不会被删除。</p><p v-if="error" class="error-text" role="alert">{{ error }}</p><div class="form-actions"><button type="button" :disabled="busy" @click="show = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '保存中…' : '保存账号' }}</button></div></form></Modal><Modal :open="resetShow" title="重置用户密码" @close="!busy && (resetShow = false)"><form @submit.prevent="reset"><p class="notice">即将重置 {{ resetUser?.display_name }}（{{ resetUser?.username }}）的密码。该用户所有旧会话失效，下次登录须再次修改密码。</p><label for="reset-password">新的初始密码</label><input id="reset-password" v-model="resetPassword" type="password" required minlength="12" maxlength="72" autocomplete="new-password"><p v-if="error" class="error-text" role="alert">{{ error }}</p><div class="form-actions"><button type="button" :disabled="busy" @click="resetShow = false">取消</button><button class="primary" :disabled="busy">确认重置</button></div></form></Modal></template>
