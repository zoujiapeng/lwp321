import { createRouter, createWebHashHistory } from 'vue-router'
import { session, refreshSession } from './api.js'
import Login from './views/Login.vue'
import Dashboard from './views/Dashboard.vue'
import Students from './views/Students.vue'
import Records from './views/Records.vue'
import RecordEditor from './views/RecordEditor.vue'
import RecordDetail from './views/RecordDetail.vue'
import Templates from './views/Templates.vue'
import Users from './views/Users.vue'
import Audit from './views/Audit.vue'
import Password from './views/Password.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: Login, meta: { title: '登录' } },
    { path: '/', component: Dashboard, meta: { title: '工作台' } },
    { path: '/students', component: Students, meta: { title: '学生档案' } },
    { path: '/records', component: Records, meta: { title: '谈话记录' } },
    { path: '/records/new', component: RecordEditor, meta: { title: '新建谈话' } },
    { path: '/records/:id/edit', component: RecordEditor, meta: { title: '编辑草稿' } },
    { path: '/records/:id', component: RecordDetail, meta: { title: '记录详情' } },
    { path: '/templates', component: Templates, meta: { title: '记录模板', admin: true } },
    { path: '/users', component: Users, meta: { title: '账号管理', admin: true } },
    { path: '/audit', component: Audit, meta: { title: '操作日志', admin: true } },
    { path: '/password', component: Password, meta: { title: '修改密码' } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior: () => ({ top: 0 })
})
router.beforeEach(async (to) => {
  if (!session.ready) await refreshSession()
  if (!session.user) return to.path === '/login' ? true : '/login'
  if (session.user.must_change && to.path !== '/password') return '/password'
  if (to.path === '/login') return '/'
  if (to.meta.admin && session.user.role !== 'ADMIN') return '/'
})
router.afterEach(to => { document.title = to.meta.title + ' · 师生谈心谈话记录' })
export default router
