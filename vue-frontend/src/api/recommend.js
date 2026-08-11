import api from './index.js'

export const recommendApi = {
  getForStore(storeId) {
    return api.get(`/api/recommend/${storeId}`)
  }
}
