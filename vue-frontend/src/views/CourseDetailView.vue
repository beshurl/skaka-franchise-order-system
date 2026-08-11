<template>
  <WorkspaceShell
    active="products"
    :title="product?.name || '상품 상세'"
    description="상품 정보와 현재 발주 상태를 확인합니다."
  >
    <div v-if="courseStore.loading" class="detail-loading">
      <div class="spinner" aria-label="상품 정보 로딩 중"></div>
    </div>

    <section v-else-if="courseStore.error || !product" class="detail-state surface" role="alert">
      <span>상품 정보를 찾을 수 없습니다.</span>
      <p>{{ courseStore.error }}</p>
      <router-link to="/courses" class="btn btn-secondary">상품 목록으로</router-link>
    </section>

    <div v-else class="detail-layout enter">
      <section class="product-stage surface">
        <div class="category-tile" :class="`tone-${product.categoryTone}`" aria-hidden="true">
          {{ product.categoryShort }}
        </div>
        <div class="stage-content">
          <span class="stage-category">{{ product.categoryLabel }}</span>
          <h2>{{ product.name }}</h2>
          <p>{{ product.description || '등록된 상품 설명이 없습니다.' }}</p>
        </div>
        <div class="stage-caption">
          <span>{{ product.status === 'ACTIVE' ? '발주 가능' : '발주 중지' }}</span>
          <small>상품 #{{ product.id }}</small>
        </div>
      </section>

      <aside class="order-panel surface">
        <div class="price-row">
          <span>공급가</span>
          <strong>{{ formatMoney(product.supplyPrice) }}</strong>
        </div>

        <dl class="product-specs">
          <div><dt>상품 ID</dt><dd>#{{ product.id }}</dd></div>
          <div><dt>최소 발주 수량</dt><dd>{{ product.orderUnit }}개</dd></div>
          <div><dt>현재 재고</dt><dd>{{ product.inventory.toLocaleString() }}</dd></div>
          <div><dt>상품 상태</dt><dd>{{ product.status === 'ACTIVE' ? '활성' : '중지' }}</dd></div>
        </dl>

        <template v-if="auth.isStore">
          <div v-if="currentOrder" class="current-order-summary">
            <div class="current-order">
              <span>{{ canCreateOrder ? '최근 발주 상태' : '현재 발주 상태' }}</span>
              <StatusBadge :status="currentOrder.status" />
            </div>
            <div class="current-order">
              <span>발주 수량</span>
              <strong>{{ currentOrder.quantity.toLocaleString() }}개</strong>
            </div>
          </div>
          <div v-if="canCreateOrder" class="quantity-section">
            <label for="order-quantity">발주 수량</label>
            <div class="quantity-stepper">
              <button
                type="button"
                aria-label="발주 수량 1개 줄이기"
                :disabled="orderQuantity <= MIN_ORDER_QUANTITY"
                @click="changeQuantity(-1)"
              >−</button>
              <input
                id="order-quantity"
                v-model.number="orderQuantity"
                type="number"
                :min="MIN_ORDER_QUANTITY"
                :max="MAX_ORDER_QUANTITY"
                step="1"
                inputmode="numeric"
                aria-describedby="order-quantity-help"
                @blur="sanitizeQuantity"
              />
              <button
                type="button"
                aria-label="발주 수량 1개 늘리기"
                :disabled="orderQuantity >= MAX_ORDER_QUANTITY"
                @click="changeQuantity(1)"
              >+</button>
            </div>
            <div id="order-quantity-help" class="quantity-total">
              <span>예상 정산 금액</span>
              <strong>{{ formatMoney(estimatedAmount) }}</strong>
            </div>
          </div>
          <button
            type="button"
            class="btn btn-primary order-button"
            :disabled="actionDisabled"
            @click="handleStoreAction"
          >
            {{ actionLoading ? '처리 중' : actionLabel }}
          </button>
          <p v-if="canCreateOrder && currentOrder" class="panel-helper">
            완료된 발주는 이력으로 유지되며 같은 상품을 추가 발주할 수 있습니다.
          </p>
          <p v-else class="panel-helper">입고 완료 전까지 재고 수량은 증가하지 않습니다.</p>
        </template>

        <template v-else>
          <router-link to="/enrollments" class="btn btn-primary order-button">발주 요청 관리</router-link>
          <p class="panel-helper">본사 관리자는 발주 관리 화면에서 승인과 반려를 처리합니다.</p>
        </template>

        <p v-if="actionError" class="notice notice-error" role="alert">{{ actionError }}</p>
        <p v-if="actionSuccess" class="notice notice-success">{{ actionSuccess }}</p>
      </aside>

      <section class="flow-panel surface">
        <div class="flow-heading">
          <span>발주 처리 순서</span>
          <h2>발주 상태 흐름</h2>
        </div>
        <ol class="status-flow">
          <li :class="{ reached: isReached('REQUESTED') }"><i></i><strong>발주 요청</strong><small>REQUESTED</small></li>
          <li :class="{ reached: isReached('APPROVED') }"><i></i><strong>본사 승인</strong><small>APPROVED</small></li>
          <li :class="{ reached: isReached('RECEIVED') }"><i></i><strong>입고 확인</strong><small>RECEIVED</small></li>
        </ol>
        <p class="reject-note">반려는 발주 요청 상태에서만 가능하며 REJECTED로 종료됩니다.</p>
      </section>
    </div>
  </WorkspaceShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import WorkspaceShell from '@/components/WorkspaceShell.vue'
import { enrollmentApi } from '@/api/enrollment.js'
import { useAuthStore } from '@/store/auth.js'
import { useCourseStore } from '@/store/course.js'
import { formatMoney, normalizeOrder } from '@/utils/business.js'

const route = useRoute()
const auth = useAuthStore()
const courseStore = useCourseStore()
const currentOrder = ref(null)
const MIN_ORDER_QUANTITY = 1
const MAX_ORDER_QUANTITY = 999
const orderQuantity = ref(MIN_ORDER_QUANTITY)
const actionLoading = ref(false)
const actionError = ref('')
const actionSuccess = ref('')

const product = computed(() => courseStore.selectedProduct)
const isQuantityValid = computed(() => Number.isInteger(Number(orderQuantity.value))
  && Number(orderQuantity.value) >= MIN_ORDER_QUANTITY
  && Number(orderQuantity.value) <= MAX_ORDER_QUANTITY)
const estimatedAmount = computed(() => (product.value?.supplyPrice || 0) * (Number(orderQuantity.value) || 0))
const canCreateOrder = computed(() => !currentOrder.value
  || ['RECEIVED', 'REJECTED'].includes(currentOrder.value.status))
const actionLabel = computed(() => {
  if (!currentOrder.value) return '발주 요청'
  if (currentOrder.value.status === 'REQUESTED') return '본사 승인 대기 중'
  if (currentOrder.value.status === 'APPROVED') return '입고 확인'
  return '추가 발주 요청'
})
const actionDisabled = computed(() => {
  if (actionLoading.value || product.value?.status !== 'ACTIVE') return true
  if (canCreateOrder.value) return !isQuantityValid.value
  return currentOrder.value?.status !== 'APPROVED'
})

function sanitizeQuantity() {
  const quantity = Math.trunc(Number(orderQuantity.value))
  orderQuantity.value = Number.isFinite(quantity)
    ? Math.min(MAX_ORDER_QUANTITY, Math.max(MIN_ORDER_QUANTITY, quantity))
    : MIN_ORDER_QUANTITY
}

function changeQuantity(delta) {
  sanitizeQuantity()
  orderQuantity.value = Math.min(
    MAX_ORDER_QUANTITY,
    Math.max(MIN_ORDER_QUANTITY, orderQuantity.value + delta)
  )
}

async function loadOrderStatus() {
  if (!auth.isStore || !product.value?.id) return
  try {
    const response = await enrollmentApi.getMyOrders()
    const payload = response.data?.data ?? response.data
    const orders = Array.isArray(payload) ? payload.map(normalizeOrder) : []
    currentOrder.value = orders.find((order) => Number(order.productId) === Number(product.value.id)) || null
  } catch {
    currentOrder.value = null
  }
}

async function handleStoreAction() {
  actionError.value = ''
  actionSuccess.value = ''
  actionLoading.value = true

  try {
    if (canCreateOrder.value) {
      sanitizeQuantity()
      const response = await enrollmentApi.createOrder(product.value.id, orderQuantity.value)
      currentOrder.value = normalizeOrder(response.data?.data ?? response.data)
      actionSuccess.value = `${orderQuantity.value.toLocaleString()}개 발주 요청이 접수되었습니다.`
    } else if (currentOrder.value.status === 'APPROVED') {
      await enrollmentApi.receive(currentOrder.value.id)
      currentOrder.value = { ...currentOrder.value, status: 'RECEIVED' }
      actionSuccess.value = '입고가 완료되어 재고 반영을 요청했습니다.'
    }
  } catch (error) {
    actionError.value = error.response?.data?.message || '요청을 처리하지 못했습니다. 백엔드 API 구현 상태를 확인해 주세요.'
  } finally {
    actionLoading.value = false
  }
}

function isReached(status) {
  const order = ['REQUESTED', 'APPROVED', 'RECEIVED']
  const currentIndex = order.indexOf(currentOrder.value?.status)
  const targetIndex = order.indexOf(status)
  return currentIndex >= targetIndex && targetIndex >= 0
}

onMounted(async () => {
  await courseStore.fetchCourse(route.params.id)
  await loadOrderStatus()
})
</script>

<style scoped>
.detail-loading,
.detail-state {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-state {
  padding: 36px;
  flex-direction: column;
  text-align: center;
}

.detail-state span {
  font-family: var(--font-display);
  font-size: 30px;
}

.detail-state p {
  margin: 8px 0 20px;
  color: var(--color-muted);
  font-size: 13px;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: 16px;
}

.product-stage {
  min-height: 300px;
  padding: 34px;
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
}

.product-stage::before,
.product-stage::after {
  display: none;
}

.tone-coral { background: #e6b7a6; }
.tone-aqua { background: #a9d6d0; }
.tone-olive { background: #cad09e; }
.tone-leaf { background: #a9cfad; }
.tone-amber { background: #e4c78e; }
.tone-blue { background: #aecbd9; }
.tone-violet { background: #c5bdd7; }
.tone-stone { background: #d4cec3; }

.category-tile {
  width: 90px;
  height: 90px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-lg);
  color: #25303b;
  font-size: 27px;
  font-weight: 750;
}

.stage-category {
  color: var(--color-accent-strong);
  font-size: 12px;
  font-weight: 700;
}

.stage-content h2 {
  margin-top: 8px;
  font-size: clamp(25px, 3vw, 36px);
  font-weight: 740;
  letter-spacing: -0.04em;
}

.stage-content p {
  max-width: 560px;
  margin-top: 12px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.7;
}

.stage-caption {
  text-align: right;
}

.stage-caption span,
.stage-caption small {
  display: block;
}

.stage-caption span {
  width: fit-content;
  margin-left: auto;
  padding: 5px 8px;
  border-radius: 999px;
  color: var(--color-success);
  background: var(--color-success-soft);
  font-size: 10px;
  font-weight: 700;
}

.stage-caption small {
  margin-top: 9px;
  color: var(--color-muted);
  font-size: 10px;
}

.order-panel {
  padding: 26px;
}

.price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.price-row span {
  color: var(--color-muted);
  font-size: 11px;
}

.price-row strong {
  font-size: 28px;
  font-weight: 750;
  letter-spacing: -0.035em;
}

.product-specs {
  margin: 24px 0;
  padding: 18px 0;
  display: grid;
  gap: 11px;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.product-specs div,
.current-order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.product-specs dt,
.current-order > span {
  color: var(--color-muted);
  font-size: 11px;
}

.product-specs dd {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}

.current-order {
  margin-bottom: 14px;
}

.current-order-summary {
  margin-bottom: 14px;
  display: grid;
  gap: 10px;
}

.current-order-summary .current-order {
  margin-bottom: 0;
}

.current-order strong {
  font-size: 12px;
}

.quantity-section {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.quantity-section > label {
  display: block;
  margin-bottom: 9px;
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
}

.quantity-stepper {
  height: 42px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  overflow: hidden;
  border: 1px solid var(--color-border-strong);
  border-radius: 10px;
  background: var(--color-surface);
}

.quantity-stepper button,
.quantity-stepper input {
  border: 0;
  background: transparent;
  color: var(--color-ink);
}

.quantity-stepper button {
  cursor: pointer;
  font-size: 18px;
  font-weight: 650;
}

.quantity-stepper button:disabled {
  cursor: not-allowed;
  color: var(--color-muted);
  opacity: 0.45;
}

.quantity-stepper input {
  min-width: 0;
  border-right: 1px solid var(--color-border);
  border-left: 1px solid var(--color-border);
  outline: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 750;
  text-align: center;
  appearance: textfield;
}

.quantity-stepper input::-webkit-inner-spin-button,
.quantity-stepper input::-webkit-outer-spin-button {
  margin: 0;
  appearance: none;
}

.quantity-stepper:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgb(44 101 230 / 10%);
}

.quantity-total {
  margin-top: 11px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quantity-total span {
  color: var(--color-muted);
  font-size: 10px;
}

.quantity-total strong {
  color: var(--color-accent-strong);
  font-size: 13px;
}

.order-button {
  width: 100%;
}

.panel-helper {
  margin-top: 12px;
  color: var(--color-muted);
  font-size: 10px;
  line-height: 1.55;
  text-align: center;
}

.order-panel .notice {
  margin-top: 14px;
}

.flow-panel {
  padding: 28px;
}

.flow-heading > span {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
}

.flow-heading h2 {
  margin-top: 7px;
  font-size: 24px;
  font-weight: 720;
  letter-spacing: -0.03em;
}

.flow-panel {
  grid-column: 1 / -1;
}

.status-flow {
  margin: 28px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  list-style: none;
}

.status-flow li {
  position: relative;
  padding-top: 20px;
  color: var(--color-muted);
  border-top: 1px solid var(--color-border-strong);
}

.status-flow li i {
  position: absolute;
  top: -5px;
  left: 0;
  width: 9px;
  height: 9px;
  border: 2px solid var(--color-surface);
  border-radius: 50%;
  background: var(--color-surface-muted);
}

.status-flow li.reached {
  color: var(--color-accent-strong);
  border-color: var(--color-accent);
}

.status-flow li.reached i {
  background: var(--color-accent);
}

.status-flow strong,
.status-flow small {
  display: block;
}

.status-flow strong {
  font-size: 12px;
}

.status-flow small {
  margin-top: 4px;
  font-size: 9px;
}

.reject-note {
  margin-top: 22px;
  color: var(--color-muted);
  font-size: 10px;
}

@media (max-width: 1080px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .product-stage {
    min-height: 0;
    padding: 24px;
    grid-template-columns: 64px 1fr;
    align-items: start;
  }

  .category-tile {
    width: 64px;
    height: 64px;
    font-size: 21px;
  }

  .stage-caption {
    grid-column: 2;
    text-align: left;
  }

  .stage-caption span {
    margin-left: 0;
  }

  .status-flow {
    grid-template-columns: 1fr;
    gap: 18px;
  }
}
</style>
