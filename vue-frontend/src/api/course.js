import api from './index.js'

export const courseApi = {
  getCourses(params) {
    return api.get('/api/courses', { params })
  },

  getAll(params) {
    return api.get('/api/courses', { params })
  },

  getById(id) {
    return api.get(`/api/courses/${id}`)
  },

  getByCategory(category) {
    return api.get(`/api/courses/category/${category}`)
  },

  create(data) {
    return api.post('/api/courses', data)
  },

  update(id, data) {
    return api.patch(`/api/courses/${id}`, data)
  }
}
