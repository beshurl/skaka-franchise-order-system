import api from './index.js'

export const enrollmentApi = {
  getMyOrders() {
    return api.get('/api/enrollments/my')
  },

  getHeadquartersOrders() {
    return api.get('/api/enrollments')
  },

  getOrdersByStore(storeId) {
    return api.get(`/api/enrollments/user/${storeId}`)
  },

  createOrder(productId) {
    return api.post('/api/enrollments', { courseId: productId })
  },

  approve(orderId) {
    return api.patch(`/api/enrollments/${orderId}/status`, { status: 'APPROVED' })
  },

  reject(orderId) {
    return api.patch(`/api/enrollments/${orderId}/status`, { status: 'REJECTED' })
  },

  receive(orderId) {
    return api.patch(`/api/enrollments/${orderId}/status`, { status: 'RECEIVED' })
  },

  getMyEnrollments() {
    return this.getMyOrders()
  },

  enroll(courseId) {
    return this.createOrder(courseId)
  }
}
