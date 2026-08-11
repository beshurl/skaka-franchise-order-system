import api from './index.js'

export const paymentApi = {
  getMyPayments(userId) {
    return api.get(`/api/payments/user/${userId}`)
  },

  getAllPayments() {
    return api.get('/api/payments/admin')
  }
}
