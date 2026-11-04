import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：添加 JWT
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：处理 401
// 注意：只有"需要登录才能用"的请求才跳登录页。
// 可选的请求（如收藏状态）用 silent: true 标记，401 时静默失败，不打断页面。
api.interceptors.response.use(
  response => response,
  error => {
    const silent = error.config?.silent === true
    if (error.response?.status === 401 && !silent) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api

// 认证 API
export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  me: () => api.get('/auth/me')
}

// 电影 API（全部公开，无需登录）
export const movieApi = {
  list: (params) => api.get('/movies', { params }),
  detail: (id) => api.get(`/movies/${id}`),
  analyze: (id) => api.get(`/movies/${id}/analyze`),
  keywords: (id) => api.get(`/movies/${id}/keywords`)
}

// 评论 API
export const commentApi = {
  submit: (data) => api.post('/comments/analyze', data),
  analyzeAsync: (data) => api.post('/comments/analyze-async', data),
  pollResult: (taskId) => api.get(`/comments/result/${taskId}`)
}

// 收藏 API（需要登录；状态查询用 silent，未登录时不跳转）
export const favoriteApi = {
  add: (movieId) => api.post(`/favorites/${movieId}`),
  remove: (movieId) => api.delete(`/favorites/${movieId}`),
  list: () => api.get('/favorites'),
  status: (movieId) => api.get(`/favorites/${movieId}/status`, { silent: true })
}

// 管理 API（需管理员权限）
export const adminApi = {
  setEnabled: (userId, enabled) => api.put(`/admin/users/${userId}/enabled`, null, { params: { enabled } }),
  resetPassword: (userId, newPassword) => api.put(`/admin/users/${userId}/password`, { newPassword }),
  crawlerTasks: () => api.get('/admin/crawler/tasks'),
  triggerCrawler: (data) => api.post('/admin/crawler/trigger', data),
  rerunAll: () => api.post('/admin/analysis/rerun-all'),
  overview: () => api.get('/admin/overview'),
  // 完整版分词模型（可选增强，未下载时自动降级 portable）
  // 下载改为后端异步执行，接口立即返回，因此不需要超长 timeout
  modelStatus: () => api.get('/admin/model/status'),
  downloadModel: () => api.post('/admin/model/download')
}
