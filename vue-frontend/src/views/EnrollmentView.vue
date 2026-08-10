<template>
  <WorkspaceShell
    active="orders"
    :title="auth.isHeadquarters ? '발주 요청 관리' : '내 발주 내역'"
    :description="auth.isHeadquarters
      ? '가맹점의 발주 요청을 확인하고 승인 또는 반려합니다.'
      : '발주 진행 상태를 확인하고 승인된 상품의 입고를 완료합니다.'"
  >
    <div class="summary-strip" aria-label="발주 요약">
      <div><span>전체</span><strong>{{ orders.length }}</strong></div>
      <div><span>승인 대기</span><strong>{{ countByStatus.REQUESTED }}</strong></div>
      <div><span>입고 대기</span><strong>{{ countByStatus.APPROVED }}</strong></div>
      <div><span>입고 완료</span><strong>{{ countByStatus.RECEIVED }}</strong></div>
    </div>

    <p v-if="apiNotice" class="api-notice">{{ apiNotice }}</p>

    <div class="order-toolbar">
      <div class="status-tabs" role="group" aria-label="발주 상태 필터">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          type="button"
          :class="{ active: selectedStatus === option.value }"
          @click="selectedStatus = option.value"
        >
          {{ option.label }}
        </button>
      </div>
      <button type="button" class="refresh-button" :disabled="loading" @click="loadOrders">새로고침</button>
    </div>

    <div v-if="loading" class="order-list" aria-label="발주 로딩 중">
      <div v-for="index in 4" :key="index" class="order-skeleton surface">
        <div class="skeleton skeleton-block"></div>
        <div class="skeleton skeleton-text"></div>
        <div class="skeleton skeleton-text short"></div>
      </div>
    </div>

    <section v-else-if="loadError" class="order-state surface" role="alert">
      <h2>발주 데이터를 불러오지 못했습니다.</h2>
      <p>{{ loadError }}</p>
      <button type="button" class="btn btn-secondary" @click="loadOrders">다시 시도</button>
    </section>

    <div v-else-if="filteredOrders.length" class="order-list enter">
      <article v-for="order in filteredOrders" :key="order.id" class="order-card surface">
        <div class="order-symbol" :class="`tone-${order.product.categoryTone}`">
          {{ order.product.categoryShort }}
        </div>

        <div class="order-main">
          <div class="order-heading">
            <span>발주 #{{ order.id }}</span>
            <StatusBadge :status="order.status" />
          </div>
          <h2>{{ order.productName }}</h2>
          <div class="order-meta">
            <span>요청일 {{ formatDate(order.requestedAt) }}</span>
            <span v-if="auth.isHeadquarters">가맹점 ID {{ order.storeId || '-' }}</span>
            <span>상품 ID {{ order.productId }}</span>
          </div>
        </div>

        <dl class="amount-block">
          <div><dt>발주 수량</dt><dd>{{ order.quantity.toLocaleString() }}</dd></div>
          <div><dt>발주 금액</dt><dd>{{ formatMoney(order.amount) }}</dd></div>
        </dl>

        <div class="order-actions">
          <template v-if="auth.isHeadquarters && order.status === 'REQUESTED'">
            <button type="button" class="btn btn-primary" :disabled="updatingId === order.id" @click="updateOrder(order, 'approve')">승인</button>
            <button type="button" class="btn btn-danger" :disabled="updatingId === order.id" @click="updateOrder(order, 'reject')">반려</button>
          </template>
          <button
            v-else-if="auth.isStore && order.status === 'APPROVED'"
            type="button"
            class="btn btn-primary"
            :disabled="updatingId === order.id"
            @click="updateOrder(order, 'receive')"
          >
            입고 확인
          </button>
          <router-link v-else :to="`/courses/${order.productId}`" class="btn btn-ghost">상품 보기</router-link>
        </div>
      </article>
    </div>

    <section v-else class="order-state surface">
      <h2>표시할 발주가 없습니다.</h2>
      <p>{{ selectedStatus === 'ALL' ? '아직 등록된 발주가 없습니다.' : '선택한 상태에 해당하는 발주가 없습니다.' }}</p>
      <router-link v-if="auth.isStore" to="/courses" class="btn btn-primary">상품 발주하기</router-link>
    </section>

    <p v-if="actionError" class="notice notice-error page-error" role="alert">{{ actionError }}</p>
  </WorkspaceShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import WorkspaceShell from '@/components/WorkspaceShell.vue'
import { enrollmentApi } from '@/api/enrollment.js'
import { useAuthStore } from '@/store/auth.js'
import { useCourseStore } from '@/store/course.js'
import { formatDate, formatMoney, normalizeOrder } from '@/utils/business.js'

const auth = useAuthStore()
const courseStore = useCourseStore()
const orders = ref([])
const selectedStatus = ref('ALL')
const loading = ref(true)
const loadError = ref('')
const actionError = ref('')
const apiNotice = ref('')
const updatingId = ref(null)

const statusOptions = [
  { label: '전체', value: 'ALL' },
  { label: '승인 대기', value: 'REQUESTED' },
  { label: '입고 대기', value: 'APPROVED' },
  { label: '입고 완료', value: 'RECEIVED' },
  { label: '반려', value: 'REJECTED' }
]

const filteredOrders = computed(() => selectedStatus.value === 'ALL'
  ? orders.value
  : orders.value.filter((order) => order.status === selectedStatus.value))

const countByStatus = computed(() => ({
  REQUESTED: orders.value.filter((order) => order.status === 'REQUESTED').length,
  APPROVED: orders.value.filter((order) => order.status === 'APPROVED').length,
  RECEIVED: orders.value.filter((order) => order.status === 'RECEIVED').length
}))

function attachProduct(order) {
  const product = courseStore.products.find((item) => Number(item.id) === Number(order.productId))
  if (!product || (order.product?.id && order.productName !== '이름 없는 상품')) return order
  return { ...order, product, productName: product.name, unitPrice: product.supplyPrice, amount: product.supplyPrice * order.quantity }
}

async function requestOrderList() {
  if (!auth.isHeadquarters) return enrollmentApi.getMyOrders()
  try {
    return await enrollmentApi.getHeadquartersOrders()
  } catch (error) {
    if (![404, 405].includes(error.response?.status) || !auth.user?.id) throw error
    apiNotice.value = '전체 발주 조회 API가 아직 없어 현재 계정 범위의 발주만 표시합니다.'
    return enrollmentApi.getOrdersByStore(auth.user.id)
  }
}

async function loadOrders() {
  loading.value = true
  loadError.value = ''
  apiNotice.value = ''
  try {
    if (!courseStore.products.length) await courseStore.fetchCourses()
    const response = await requestOrderList()
    const payload = response.data?.data ?? response.data
    orders.value = Array.isArray(payload) ? payload.map(normalizeOrder).map(attachProduct) : []
  } catch (error) {
    orders.value = []
    loadError.value = error.response?.data?.message || 'Gateway 또는 Order Service 연결을 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

async function updateOrder(order, action) {
  actionError.value = ''
  updatingId.value = order.id
  try {
    if (action === 'approve') await enrollmentApi.approve(order.id)
    if (action === 'reject') await enrollmentApi.reject(order.id)
    if (action === 'receive') await enrollmentApi.receive(order.id)
    const nextStatus = { approve: 'APPROVED', reject: 'REJECTED', receive: 'RECEIVED' }[action]
    orders.value = orders.value.map((item) => item.id === order.id ? normalizeOrder({ ...item, status: nextStatus }) : item)
  } catch (error) {
    actionError.value = error.response?.data?.message || '상태 변경 API를 처리하지 못했습니다. Enrollment Service 구현 상태를 확인해 주세요.'
  } finally {
    updatingId.value = null
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.summary-strip {
  margin-bottom: 26px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
}

.summary-strip > div {
  min-height: 88px;
  padding: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-right: 1px solid var(--color-border);
}

.summary-strip > div:last-child {
  border-right: 0;
}

.summary-strip span {
  color: var(--color-muted);
  font-size: 11px;
}

.summary-strip strong {
  font-size: 28px;
  font-weight: 740;
  letter-spacing: -0.03em;
}

.api-notice {
  margin: -8px 0 20px;
  color: var(--color-warning);
  font-size: 11px;
}

.order-toolbar {
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.status-tabs {
  display: flex;
  gap: 7px;
  overflow-x: auto;
  scrollbar-width: none;
}

.status-tabs::-webkit-scrollbar {
  display: none;
}

.status-tabs button,
.refresh-button {
  color: var(--color-muted);
  background: transparent;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  transition: color 180ms ease, background-color 180ms ease;
}

.status-tabs button {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
}

.status-tabs button:hover,
.status-tabs button.active {
  color: var(--color-accent-strong);
  background: var(--color-accent-soft);
}

.refresh-button:hover {
  color: var(--color-accent-strong);
}

.order-list {
  display: grid;
  gap: 12px;
}

.order-card {
  padding: 18px;
  display: grid;
  grid-template-columns: 76px minmax(210px, 1fr) minmax(170px, 0.5fr) auto;
  align-items: center;
  gap: 18px;
}

.order-symbol {
  display: flex;
  width: 76px;
  height: 76px;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  color: #25303b;
  background: #b7d8d0;
  font-size: 22px;
  font-weight: 750;
}

.tone-coral { background: #e6b7a6; }
.tone-aqua { background: #a9d6d0; }
.tone-olive { background: #cad09e; }
.tone-leaf { background: #a9cfad; }
.tone-amber { background: #e4c78e; }
.tone-blue { background: #aecbd9; }
.tone-violet { background: #c5bdd7; }
.tone-stone { background: #d4cec3; }

.order-heading {
  display: flex;
  align-items: center;
  gap: 9px;
}

.order-heading > span {
  color: var(--color-muted);
  font-size: 10px;
}

.order-main h2 {
  margin-top: 7px;
  font-size: 16px;
}

.order-meta {
  margin-top: 7px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--color-muted);
  font-size: 10px;
}

.amount-block {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 7px;
  border-left: 1px solid var(--color-border);
}

.amount-block div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.amount-block dt {
  color: var(--color-muted);
  font-size: 10px;
}

.amount-block dd {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}

.order-actions {
  display: flex;
  gap: 7px;
}

.order-actions .btn {
  min-height: 38px;
  padding-inline: 14px;
  font-size: 11px;
}

.order-skeleton {
  min-height: 112px;
  padding: 18px;
  display: grid;
  grid-template-columns: 76px 1fr;
  gap: 14px;
}

.skeleton-block {
  grid-row: 1 / 3;
  border-radius: var(--radius-md);
}

.skeleton-text {
  height: 15px;
  align-self: end;
  border-radius: 8px;
}

.skeleton-text.short {
  width: 56%;
  align-self: start;
}

.order-state {
  min-height: 300px;
  padding: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.order-state h2 {
  font-size: 23px;
  font-weight: 720;
  letter-spacing: -0.03em;
}

.order-state p {
  margin: 8px 0 20px;
  color: var(--color-muted);
  font-size: 12px;
}

.page-error {
  margin-top: 16px;
}

@media (max-width: 1120px) {
  .order-card {
    grid-template-columns: 68px 1fr auto;
  }

  .order-symbol {
    width: 68px;
    height: 68px;
  }

  .amount-block {
    display: none;
  }
}

@media (max-width: 720px) {
  .summary-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .summary-strip > div:nth-child(2) {
    border-right: 0;
  }

  .summary-strip > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--color-border);
  }

  .order-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .order-card {
    grid-template-columns: 56px 1fr;
  }

  .order-symbol {
    width: 56px;
    height: 56px;
    font-size: 24px;
  }

  .order-actions {
    grid-column: 1 / -1;
  }

  .order-actions .btn {
    flex: 1;
  }
}
</style>
