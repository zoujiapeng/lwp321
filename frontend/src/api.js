import { reactive } from 'vue'

export const session = reactive({ user: null, ready: false })
export const toast = reactive({ text: '', type: 'success' })
let timer, csrf
export function notify(text, type = 'success') {
  toast.text = text; toast.type = type
  clearTimeout(timer); timer = setTimeout(() => { toast.text = '' }, 6000)
}
export function clearCsrf() { csrf = null }
async function token() {
  if (!csrf) {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new Error('无法取得安全令牌，请刷新页面')
    csrf = await response.json()
  }
  return csrf
}
export async function api(path, options = {}) {
  const method = options.method || 'GET'
  const headers = { ...options.headers }
  let body = options.body
  if (body !== undefined && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
    headers['Content-Type'] = 'application/json'; body = JSON.stringify(body)
  }
  if (!['GET', 'HEAD'].includes(method)) {
    const current = await token(); headers[current.headerName] = current.token
  }
  const response = await fetch('/api' + path, { method, headers, body, credentials: 'same-origin' })
  if (!response.ok) {
    let message = '请求失败，请检查服务连接'
    try { message = (await response.json()).message || message } catch (_) { /* no sensitive body logging */ }
    const error = new Error(message); error.status = response.status
    if (response.status === 401 && path !== '/auth/login') { session.user = null; clearCsrf() }
    throw error
  }
  return options.blob ? response.blob() : response.json()
}
export async function refreshSession() {
  try { session.user = await api('/auth/me') } catch (_) { session.user = null }
  session.ready = true
}
export async function login(username, password) {
  await api('/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
  clearCsrf(); await refreshSession()
}
export async function logout() {
  try { await api('/auth/logout', { method: 'POST' }) } finally { session.user = null; clearCsrf() }
}
export async function download(path, name, method = 'GET', body) {
  const blob = await api(path, { method, body, blob: true })
  const url = URL.createObjectURL(blob), link = document.createElement('a')
  link.href = url; link.download = name; document.body.appendChild(link); link.click(); link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export function query(values) {
  return new URLSearchParams(Object.entries(values).filter(([, v]) => v !== '' && v !== null && v !== undefined)).toString()
}
export function datetime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' }
export const categories = ['学业发展', '生活适应', '人际沟通', '就业规划', '其他']
