import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { courseApi } from '@/api/course.js'
import { normalizeProduct } from '@/utils/business.js'

export const useCourseStore = defineStore('course', () => {
  const courses = ref([])
  const selectedCourse = ref(null)
  const loading = ref(false)
  const error = ref('')
  const selectedCategory = ref('전체')

  const categories = ['전체', '간편식', '음료', '생활용품', '신선식품', '스낵', '위생용품', '냉장식품', '기타']
  const products = computed(() => courses.value)
  const selectedProduct = computed(() => selectedCourse.value)

  async function fetchCourses() {
    loading.value = true
    error.value = ''

    try {
      const response = await courseApi.getAll()
      const payload = response.data?.data ?? response.data
      courses.value = Array.isArray(payload) ? payload.map(normalizeProduct) : []
    } catch (requestError) {
      error.value = requestError.response?.data?.message || '상품 목록을 불러오지 못했습니다.'
      courses.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchCourse(id) {
    loading.value = true
    error.value = ''

    try {
      const response = await courseApi.getById(id)
      const payload = response.data?.data ?? response.data
      selectedCourse.value = payload && typeof payload === 'object' ? normalizeProduct(payload) : null
    } catch (requestError) {
      error.value = requestError.response?.data?.message || '상품 정보를 불러오지 못했습니다.'
      selectedCourse.value = null
    } finally {
      loading.value = false
    }
  }

  function setCategory(category) {
    selectedCategory.value = category
  }

  return {
    courses,
    products,
    selectedCourse,
    selectedProduct,
    loading,
    error,
    categories,
    selectedCategory,
    fetchCourses,
    fetchCourse,
    setCategory
  }
})
