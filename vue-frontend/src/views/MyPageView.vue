<template>
  <WorkspaceShell
    active="inventory"
    :title="auth.isHeadquarters ? '운영 현황' : '재고 관리'"
    :description="auth.isHeadquarters
      ? '등록 상품과 공급 재고를 기존 Course 데이터로 확인합니다.'
      : '입고가 완료된 상품 재고를 한눈에 확인합니다.'"
  >
    <section class="account-strip surface">
      <div class="account-avatar">{{ auth.user?.name?.charAt(0) || 'S' }}</div>
      <div class="account-info">
        <span>{{ auth.businessRoleLabel }}</span>
        <h2>{{ auth.user?.name || '사용자' }}</h2>
        <p>{{ auth.user?.email || '이메일 정보 없음' }}</p>
      </div>
      <dl>
        <div><dt>사용자 ID</dt><dd>#{{ auth.user?.id || '-' }}</dd></div>
        <div><dt>권한 코드</dt><dd>{{ auth.businessRole }}</dd></div>
      </dl>
    </section>

    <div class="inventory-summary" aria-label="재고 요약">
      <article>
        <span>운영 상품</span>
        <strong>{{ activeProducts.length }}</strong>
        <small>현재 발주 가능</small>
      </article>
      <article>
        <span>총 재고 수량</span>
        <strong>{{ totalInventory.toLocaleString() }}</strong>
        <small>입고 반영 기준</small>
      </article>
      <article>
        <span>재고 부족</span>
        <strong>{{ lowStockProducts.length }}</strong>
        <small>10개 미만 상품</small>
      </article>
    </div>

    <section class="inventory-section">
      <div class="section-heading">
        <div>
          <span>상품별 수량과 상태</span>
          <h2>{{ auth.isHeadquarters ? '상품별 공급 재고' : '가맹점 보유 재고' }}</h2>
        </div>
        <label class="inventory-search">
          <span class="sr-only">재고 상품 검색</span>
          <input v-model.trim="search" type="search" placeholder="상품 검색" />
        </label>
      </div>

      <div v-if="courseStore.loading" class="inventory-loading">
        <div class="spinner" aria-label="재고 로딩 중"></div>
      </div>

      <div v-else-if="courseStore.error" class="inventory-state surface" role="alert">
        <h3>재고 데이터를 불러오지 못했습니다.</h3>
        <p>{{ courseStore.error }}</p>
        <button type="button" class="btn btn-secondary" @click="courseStore.fetchCourses">다시 시도</button>
      </div>

      <div v-else-if="filteredProducts.length" class="inventory-table surface enter">
        <div class="inventory-header" aria-hidden="true">
          <span>상품</span><span>카테고리</span><span>공급가</span><span>재고</span><span>상태</span>
        </div>
        <router-link v-for="product in filteredProducts" :key="product.id" :to="`/courses/${product.id}`" class="inventory-row">
          <div class="product-cell">
            <span :class="`tone-${product.categoryTone}`">{{ product.categoryShort }}</span>
            <div><strong>{{ product.name }}</strong><small>#{{ product.id }}</small></div>
          </div>
          <span>{{ product.categoryLabel }}</span>
          <span>{{ formatMoney(product.supplyPrice) }}</span>
          <strong :class="{ low: product.inventory < 10 }">{{ product.inventory.toLocaleString() }}</strong>
          <span class="stock-status" :class="{ low: product.inventory < 10 }">{{ product.inventory < 10 ? '보충 필요' : '정상' }}</span>
        </router-link>
      </div>

      <div v-else class="inventory-state surface">
        <h3>표시할 재고가 없습니다.</h3>
        <p>입고가 완료되거나 상품이 등록되면 이곳에 표시됩니다.</p>
      </div>
    </section>
  </WorkspaceShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import WorkspaceShell from '@/components/WorkspaceShell.vue'
import { useAuthStore } from '@/store/auth.js'
import { useCourseStore } from '@/store/course.js'
import { formatMoney } from '@/utils/business.js'

const auth = useAuthStore()
const courseStore = useCourseStore()
const search = ref('')

const activeProducts = computed(() => courseStore.products.filter((product) => product.status === 'ACTIVE'))
const totalInventory = computed(() => courseStore.products.reduce((sum, product) => sum + product.inventory, 0))
const lowStockProducts = computed(() => courseStore.products.filter((product) => product.inventory < 10))
const filteredProducts = computed(() => {
  const query = search.value.toLocaleLowerCase('ko-KR')
  return courseStore.products.filter((product) => !query || `${product.name} ${product.categoryLabel}`.toLocaleLowerCase('ko-KR').includes(query))
})

onMounted(() => courseStore.fetchCourses())
</script>

<style scoped>
.account-strip {
  min-height: 144px;
  margin-bottom: 22px;
  padding: 24px 28px;
  display: grid;
  grid-template-columns: 70px 1fr auto;
  align-items: center;
  gap: 20px;
}

.account-avatar {
  display: flex;
  width: 70px;
  height: 70px;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  color: var(--color-accent-strong);
  background: var(--color-accent-soft);
  font-size: 24px;
  font-weight: 750;
}

.account-info span {
  color: var(--color-accent-strong);
  font-size: 10px;
  font-weight: 700;
}

.account-info h2 {
  margin-top: 4px;
  font-size: 19px;
}

.account-info p {
  margin-top: 2px;
  color: var(--color-muted);
  font-size: 11px;
}

.account-strip dl {
  margin: 0;
  display: grid;
  gap: 8px;
}

.account-strip dl div {
  display: flex;
  justify-content: space-between;
  gap: 28px;
}

.account-strip dt {
  color: var(--color-muted);
  font-size: 10px;
}

.account-strip dd {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 10px;
}

.inventory-summary {
  margin-bottom: 40px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
}

.inventory-summary article {
  min-height: 126px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
}

.inventory-summary article:last-child {
  border-right: 0;
}

.inventory-summary span,
.inventory-summary small {
  color: var(--color-muted);
  font-size: 10px;
}

.inventory-summary strong {
  margin-top: auto;
  font-size: 36px;
  font-weight: 740;
  letter-spacing: -0.04em;
  line-height: 1;
}

.inventory-summary small {
  margin-top: 7px;
}

.section-heading {
  margin-bottom: 18px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.section-heading > div > span {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
}

.section-heading h2 {
  margin-top: 5px;
  font-size: 24px;
  font-weight: 720;
  letter-spacing: -0.03em;
}

.inventory-search input {
  min-height: 40px;
  padding: 0 13px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  color: var(--color-ink);
  background: var(--color-surface);
  font-size: 11px;
  outline: none;
}

.inventory-search input:focus {
  border-color: var(--color-accent);
}

.inventory-table {
  overflow: hidden;
}

.inventory-header,
.inventory-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) 0.7fr 0.7fr 0.45fr 0.5fr;
  align-items: center;
  gap: 18px;
}

.inventory-header {
  min-height: 46px;
  padding: 0 20px;
  color: var(--color-muted);
  background: var(--color-surface-muted);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.inventory-row {
  min-height: 76px;
  padding: 10px 20px;
  border-top: 1px solid var(--color-border);
  font-size: 11px;
  transition: background-color 180ms ease;
}

.inventory-row:hover {
  background: color-mix(in srgb, var(--color-accent-soft) 42%, transparent);
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.product-cell > span {
  display: flex;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  color: #13221e;
  background: #b7d8d0;
  font-size: 14px;
  font-weight: 750;
}

.tone-coral { background: #e6b7a6 !important; }
.tone-aqua { background: #a9d6d0 !important; }
.tone-olive { background: #cad09e !important; }
.tone-leaf { background: #a9cfad !important; }
.tone-amber { background: #e4c78e !important; }
.tone-blue { background: #aecbd9 !important; }
.tone-violet { background: #c5bdd7 !important; }
.tone-stone { background: #d4cec3 !important; }

.product-cell strong,
.product-cell small {
  display: block;
}

.product-cell strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-cell small {
  margin-top: 3px;
  color: var(--color-muted);
  font-size: 9px;
}

.inventory-row > strong.low {
  color: var(--color-danger);
}

.stock-status {
  width: fit-content;
  padding: 5px 8px;
  border-radius: 999px;
  color: var(--color-success);
  background: var(--color-success-soft);
  font-size: 9px;
  font-weight: 700;
}

.stock-status.low {
  color: var(--color-danger);
  background: var(--color-danger-soft);
}

.inventory-loading,
.inventory-state {
  min-height: 250px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.inventory-state {
  padding: 30px;
  flex-direction: column;
  text-align: center;
}

.inventory-state h3 {
  font-size: 22px;
  font-weight: 720;
  letter-spacing: -0.03em;
}

.inventory-state p {
  margin: 7px 0 18px;
  color: var(--color-muted);
  font-size: 11px;
}

@media (max-width: 840px) {
  .account-strip {
    grid-template-columns: 60px 1fr;
  }

  .account-avatar {
    width: 60px;
    height: 60px;
  }

  .account-strip dl {
    display: none;
  }

  .inventory-header,
  .inventory-row {
    grid-template-columns: minmax(180px, 1fr) 0.7fr 0.5fr;
  }

  .inventory-header span:nth-child(2),
  .inventory-header span:nth-child(3),
  .inventory-row > span:nth-child(2),
  .inventory-row > span:nth-child(3) {
    display: none;
  }
}

@media (max-width: 620px) {
  .account-strip {
    padding: 18px;
  }

  .inventory-summary {
    grid-template-columns: 1fr;
  }

  .inventory-summary article {
    min-height: 105px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .inventory-summary article:last-child {
    border-bottom: 0;
  }

  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .inventory-search input {
    width: 100%;
  }

  .inventory-header {
    display: none;
  }

  .inventory-row {
    grid-template-columns: 1fr auto;
    gap: 12px;
  }

  .inventory-row > strong {
    display: none;
  }
}
</style>
