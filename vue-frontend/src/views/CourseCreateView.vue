<template>
  <WorkspaceShell
    active="products"
    title="상품 등록"
    description="가맹점에 공급할 상품의 기본 정보와 공급가를 등록합니다."
  >
    <div class="create-layout">
      <form class="product-form surface" novalidate @submit.prevent="handleSubmit">
        <div class="form-intro">
          <span>상품 기본 정보</span>
          <p>가맹점이 상품을 구분할 수 있도록 정확하게 입력해 주세요.</p>
        </div>

        <div class="field-group">
          <label for="product-name" class="field-label">상품명</label>
          <input
            id="product-name"
            v-model.trim="form.title"
            class="field-input"
            type="text"
            maxlength="255"
            placeholder="예: 생수 500ml"
            autocomplete="off"
          />
        </div>

        <div class="field-group">
          <label for="product-description" class="field-label">상품 설명</label>
          <textarea
            id="product-description"
            v-model.trim="form.description"
            class="field-textarea"
            placeholder="가맹점이 발주할 때 확인할 상품 설명을 입력하세요."
          ></textarea>
        </div>

        <div class="field-row">
          <div class="field-group">
            <label for="product-category" class="field-label">카테고리</label>
            <select id="product-category" v-model="form.category" class="field-select">
              <option value="" disabled>카테고리 선택</option>
              <option v-for="option in categoryOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div class="field-group">
            <label for="supply-price" class="field-label">공급가</label>
            <div class="price-input">
              <input
                id="supply-price"
                v-model.number="form.price"
                class="field-input"
                type="number"
                min="0"
                step="100"
                placeholder="500"
              />
              <span>원</span>
            </div>
          </div>
        </div>

        <p v-if="validationError" class="notice notice-error" role="alert">{{ validationError }}</p>
        <p v-if="submitError" class="notice notice-error" role="alert">{{ submitError }}</p>
        <p v-if="submitSuccess" class="notice notice-success">{{ submitSuccess }}</p>

        <div class="form-actions">
          <router-link to="/courses" class="btn btn-ghost">취소</router-link>
          <button type="submit" class="btn btn-primary" :disabled="submitting">
            {{ submitting ? '등록 중' : '상품 등록' }}
          </button>
        </div>
      </form>

      <aside class="preview-column">
        <span class="preview-label">등록 화면 미리보기</span>
        <ProductCard :product="previewProduct" :linkable="false" />
        <div class="mapping-note">
          <strong>데이터 저장 방식</strong>
          <dl>
            <div><dt>title</dt><dd>상품명</dd></div>
            <div><dt>price</dt><dd>공급가</dd></div>
            <div><dt>instructor_id</dt><dd>본사 관리자</dd></div>
            <div><dt>enrollment_count</dt><dd>재고 수량</dd></div>
          </dl>
        </div>
      </aside>
    </div>
  </WorkspaceShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import WorkspaceShell from '@/components/WorkspaceShell.vue'
import { courseApi } from '@/api/course.js'
import { useAuthStore } from '@/store/auth.js'
import { normalizeProduct } from '@/utils/business.js'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ title: '', description: '', category: '', price: null })
const submitting = ref(false)
const validationError = ref('')
const submitError = ref('')
const submitSuccess = ref('')

// 기존 Course.Category enum 값을 유지하면서 화면에서는 상품 카테고리로 표현한다.
const categoryOptions = [
  { label: '간편식', value: 'BACKEND' },
  { label: '음료', value: 'FRONTEND' },
  { label: '생활용품', value: 'DEVOPS' },
  { label: '신선식품', value: 'DATA_SCIENCE' },
  { label: '스낵', value: 'MOBILE' },
  { label: '위생용품', value: 'SECURITY' },
  { label: '냉장식품', value: 'DATABASE' },
  { label: '기타', value: 'OTHER' }
]

const previewProduct = computed(() => normalizeProduct({
  id: 'preview',
  title: form.title || '상품명이 표시됩니다',
  description: form.description || '상품 설명을 입력하면 여기에 미리 표시됩니다.',
  category: form.category || 'OTHER',
  price: Number(form.price || 0),
  enrollmentCount: 0,
  status: 'ACTIVE'
}))

function validate() {
  validationError.value = ''
  if (!auth.isHeadquarters) return '본사 관리자만 상품을 등록할 수 있습니다.'
  if (!form.title) return '상품명을 입력해 주세요.'
  if (!form.description) return '상품 설명을 입력해 주세요.'
  if (!form.category) return '카테고리를 선택해 주세요.'
  if (form.price === null || Number(form.price) < 0 || Number.isNaN(Number(form.price))) return '공급가는 0 이상의 숫자로 입력해 주세요.'
  return ''
}

async function handleSubmit() {
  submitError.value = ''
  submitSuccess.value = ''
  validationError.value = validate()
  if (validationError.value) return

  submitting.value = true
  try {
    const response = await courseApi.create({
      title: form.title,
      description: form.description,
      category: form.category,
      price: Number(form.price)
    })
    const created = response.data?.data ?? response.data
    submitSuccess.value = '상품이 등록되었습니다.'
    window.setTimeout(() => router.push(created?.id ? `/courses/${created.id}` : '/courses'), 450)
  } catch (error) {
    submitError.value = error.response?.data?.message || '상품 등록에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  align-items: start;
}

.product-form {
  padding: 28px;
  display: grid;
  gap: 23px;
}

.form-intro {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}

.form-intro span {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.025em;
}

.form-intro p {
  margin-top: 5px;
  color: var(--color-muted);
  font-size: 11px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.price-input {
  position: relative;
}

.price-input input {
  padding-right: 44px;
}

.price-input span {
  position: absolute;
  top: 50%;
  right: 14px;
  color: var(--color-muted);
  font-size: 12px;
  transform: translateY(-50%);
}

.form-actions {
  padding-top: 4px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.preview-column {
  position: sticky;
  top: 108px;
}

.preview-label {
  display: block;
  margin-bottom: 10px;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
}

.preview-column :deep(.product-card) {
  pointer-events: none;
}

.mapping-note {
  margin-top: 18px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-ink-soft);
  background: var(--color-surface);
}

.mapping-note strong {
  font-size: 12px;
}

.mapping-note dl {
  margin: 14px 0 0;
  display: grid;
  gap: 8px;
}

.mapping-note dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 11px;
}

.mapping-note dt {
  font-family: var(--font-sans);
}

.mapping-note dd {
  margin: 0;
}

@media (max-width: 1080px) {
  .create-layout {
    grid-template-columns: 1fr;
  }

  .preview-column {
    position: static;
    max-width: 360px;
  }
}

@media (max-width: 620px) {
  .product-form {
    padding: 22px;
  }

  .field-row {
    grid-template-columns: 1fr;
  }

  .form-actions,
  .form-actions .btn {
    width: 100%;
  }
}
</style>
