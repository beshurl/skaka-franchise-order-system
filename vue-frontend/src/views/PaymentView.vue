<template>
  <WorkspaceShell
    active="payments"
    :title="auth.isHeadquarters ? '전체 정산 내역' : '내 정산 내역'"
    :description="auth.isHeadquarters
      ? '가맹점 발주 승인 시 발생한 정산 내역을 확인합니다.'
      : '발주가 승인될 때 자동으로 발생한 정산 내역을 확인합니다.'"
  >
    <section class="monthly-summary surface" aria-label="이번 달 정산 요약">
      <div>
        <span class="section-kicker">{{ monthLabel }}</span>
        <h2>{{ auth.isHeadquarters ? '이번 달 전체 가맹점 발주 정산 총액' : '이번 달 발주로 정산될 금액' }}</h2>
        <p>발주가 본사 승인을 받는 즉시 정산이 처리되어 바로 합산됩니다 (실패/취소 건 제외).</p>
      </div>
      <strong class="monthly-amount">{{ formatMoney(monthlyTotal) }}</strong>
    </section>

    <div class="summary-strip" aria-label="정산 요약">
      <div><span>전체</span><strong>{{ payments.length }}</strong></div>
      <div><span>정산 완료</span><strong>{{ countByStatus.COMPLETED }}</strong></div>
      <div><span>정산 대기</span><strong>{{ countByStatus.PENDING }}</strong></div>
      <div><span>정산 실패</span><strong>{{ countByStatus.FAILED }}</strong></div>
    </div>

    <div class="payment-toolbar">
      <button type="button" class="refresh-button" :disabled="loading" @click="loadPayments">새로고침</button>
    </div>

    <div v-if="loading" class="payment-list" aria-label="정산 내역 로딩 중">
      <div v-for="index in 4" :key="index" class="payment-skeleton surface">
        <div class="skeleton skeleton-text"></div>
        <div class="skeleton skeleton-text short"></div>
      </div>
    </div>

    <section v-else-if="loadError" class="payment-state surface" role="alert">
      <h2>정산 데이터를 불러오지 못했습니다.</h2>
      <p>{{ loadError }}</p>
      <button type="button" class="btn btn-secondary" @click="loadPayments">다시 시도</button>
    </section>

    <div v-else-if="payments.length" class="payment-list enter">
      <article v-for="payment in payments" :key="payment.id" class="payment-card surface">
        <div class="payment-main">
          <div class="payment-heading">
            <span>정산 #{{ payment.id }}</span>
            <StatusBadge :status="payment.status" :meta-map="paymentStatusMeta" />
          </div>
          <h2>{{ payment.productName }}</h2>
          <div class="payment-meta">
            <span>정산일 {{ formatDate(payment.createdAt) }}</span>
            <span v-if="auth.isHeadquarters">가맹점 ID {{ payment.userId }}</span>
            <span v-if="payment.transactionId">거래ID {{ payment.transactionId }}</span>
          </div>
        </div>

        <dl class="amount-block">
          <div><dt>정산 금액</dt><dd>{{ formatMoney(payment.amount) }}</dd></div>
        </dl>
      </article>
    </div>

    <section v-else class="payment-state surface">
      <h2>표시할 정산 내역이 없습니다.</h2>
      <p>발주가 승인되면 이곳에 정산 내역이 표시됩니다.</p>
    </section>
  </WorkspaceShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import WorkspaceShell from '@/components/WorkspaceShell.vue'
import { paymentApi } from '@/api/payment.js'
import { useAuthStore } from '@/store/auth.js'
import { useCourseStore } from '@/store/course.js'
import { formatDate, formatMoney, normalizePayment, paymentStatusMeta } from '@/utils/business.js'

const auth = useAuthStore()
const courseStore = useCourseStore()
const payments = ref([])
const loading = ref(true)
const loadError = ref('')

const countByStatus = computed(() => ({
  COMPLETED: payments.value.filter((payment) => payment.status === 'COMPLETED').length,
  PENDING: payments.value.filter((payment) => payment.status === 'PENDING').length,
  FAILED: payments.value.filter((payment) => payment.status === 'FAILED').length
}))

const now = new Date()
const monthLabel = `${now.getFullYear()}년 ${now.getMonth() + 1}월`

const monthlyTotal = computed(() => payments.value
  .filter((payment) => {
    const createdAt = new Date(payment.createdAt)
    return createdAt.getFullYear() === now.getFullYear()
      && createdAt.getMonth() === now.getMonth()
      && ['PENDING', 'COMPLETED'].includes(payment.status)
  })
  .reduce((sum, payment) => sum + payment.amount, 0))

async function loadPayments() {
  loading.value = true
  loadError.value = ''
  try {
    if (!courseStore.products.length) await courseStore.fetchCourses()
    const productMap = Object.fromEntries(courseStore.products.map((product) => [product.id, product]))

    const response = auth.isHeadquarters
      ? await paymentApi.getAllPayments()
      : await paymentApi.getMyPayments(auth.user.id)
    const payload = response.data?.data ?? response.data
    payments.value = Array.isArray(payload) ? payload.map((item) => normalizePayment(item, productMap)) : []
  } catch (error) {
    payments.value = []
    loadError.value = error.response?.data?.message || 'Gateway 또는 Payment Service 연결을 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(loadPayments)
</script>

<style scoped>
.monthly-summary {
  margin-bottom: 18px;
  padding: 22px 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: #fafbfc;
}

.monthly-summary .section-kicker {
  color: var(--color-accent-strong);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.monthly-summary h2 {
  margin-top: 7px;
  font-size: 17px;
}

.monthly-summary p {
  margin-top: 6px;
  color: var(--color-muted);
  font-size: 11px;
}

.monthly-amount {
  font-size: 28px;
  font-weight: 750;
  letter-spacing: -0.03em;
  white-space: nowrap;
}

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

.payment-toolbar {
  margin-bottom: 18px;
  display: flex;
  justify-content: flex-end;
}

.refresh-button {
  color: var(--color-muted);
  background: transparent;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  transition: color 180ms ease;
}

.refresh-button:hover {
  color: var(--color-accent-strong);
}

.payment-list {
  display: grid;
  gap: 12px;
}

.payment-card {
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(210px, 1fr) minmax(170px, 0.5fr);
  align-items: center;
  gap: 18px;
}

.payment-heading {
  display: flex;
  align-items: center;
  gap: 9px;
}

.payment-heading > span {
  color: var(--color-muted);
  font-size: 10px;
}

.payment-main h2 {
  margin-top: 7px;
  font-size: 16px;
}

.payment-meta {
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

.payment-skeleton {
  min-height: 80px;
  padding: 18px;
  display: grid;
  gap: 10px;
}

.skeleton-text {
  height: 15px;
  border-radius: 8px;
}

.skeleton-text.short {
  width: 40%;
}

.payment-state {
  min-height: 300px;
  padding: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.payment-state h2 {
  font-size: 23px;
  font-weight: 720;
  letter-spacing: -0.03em;
}

.payment-state p {
  margin: 8px 0 20px;
  color: var(--color-muted);
  font-size: 12px;
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

  .payment-card {
    grid-template-columns: 1fr;
  }

  .amount-block {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid var(--color-border);
    padding-top: 10px;
  }
}
</style>
