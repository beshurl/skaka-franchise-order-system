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
    // api-gateway가 Origin 헤더가 실린 PATCH 요청을 항상 403 처리해서 PUT 사용
    // (PUT은 ProductController가 이미 지원, 실측으로 정상 동작 확인함)
    return api.put(`/api/courses/${id}`, data)
  }
}
