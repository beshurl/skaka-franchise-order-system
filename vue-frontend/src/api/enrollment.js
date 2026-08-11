import api from './index.js'

export const enrollmentApi = {
  getMyOrders() {
    return api.get('/api/enrollments/my')
  },

  getHeadquartersOrders() {
    return api.get('/api/enrollments/admin')
  },

  getOrdersByStore(storeId) {
    return api.get('/api/enrollments/admin', { params: { storeId } })
  },

  createOrder(productId, quantity = 1) {
    return api.post('/api/enrollments', { courseId: productId, quantity })
  },

  // api-gateway가 Origin 헤더가 실린 PATCH 요청을 항상 403 처리해서 POST로 통일
  // (enrollment-service 컨트롤러도 동일하게 POST로 맞춰둠, 실측으로 정상 동작 확인함)
  approve(orderId) {
    return api.post(`/api/enrollments/admin/${orderId}/approve`)
  },

  reject(orderId, reason) {
    return api.post(`/api/enrollments/admin/${orderId}/reject`, { reason })
  },

  receive(orderId) {
    return api.post(`/api/enrollments/${orderId}/receive`)
  },

  getMyEnrollments() {
    return this.getMyOrders()
  },

  enroll(courseId, quantity = 1) {
    return this.createOrder(courseId, quantity)
  }
}
