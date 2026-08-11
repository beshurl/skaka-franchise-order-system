<template>
  <section class="recommendation-panel surface" aria-labelledby="recommendation-title">
    <header class="recommendation-heading">
      <div>
        <span class="section-kicker">발주 추천</span>
        <h2 id="recommendation-title">다음 발주, 이 상품부터 확인해 보세요</h2>
        <p>{{ response?.message || '입고 이력과 현재 상품 정보를 확인하고 있습니다.' }}</p>
      </div>
      <span v-if="!loading && response" class="analysis-badge" :class="{ ai: isAi }">
        <i aria-hidden="true"></i>
        {{ isAi ? 'AI 분석' : '데이터 기준' }}
      </span>
    </header>

    <div v-if="loading" class="recommendation-list" aria-label="추천 상품 로딩 중">
      <div v-for="index in 3" :key="index" class="recommendation-skeleton">
        <span class="skeleton rank-skeleton"></span>
        <div>
          <span class="skeleton title-skeleton"></span>
          <span class="skeleton copy-skeleton"></span>
        </div>
      </div>
    </div>

    <div v-else-if="error" class="recommendation-state" role="status">
      <p>추천 데이터를 불러오지 못했습니다.</p>
      <button type="button" @click="$emit('retry')">다시 불러오기</button>
    </div>

    <ol v-else-if="items.length" class="recommendation-list">
      <li v-for="(item, index) in items" :key="item.product.id">
        <router-link :to="`/courses/${item.product.id}`" class="recommendation-row">
          <span class="recommendation-rank">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="recommendation-copy">
            <div class="product-line">
              <strong>{{ item.product.name }}</strong>
              <span>{{ item.product.categoryLabel }}</span>
            </div>
            <p>{{ item.reason }}</p>
            <div class="signal-list" aria-label="추천 근거">
              <span v-for="signal in item.signals" :key="signal">{{ signal }}</span>
            </div>
          </div>
          <div class="recommendation-meta">
            <strong>{{ formatMoney(item.product.supplyPrice) }}</strong>
            <span>적합도 {{ item.score }}</span>
          </div>
          <span class="row-arrow" aria-hidden="true">→</span>
        </router-link>
      </li>
    </ol>

    <div v-else class="recommendation-state">
      <p>현재 추천할 수 있는 상품이 없습니다.</p>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formatMoney, normalizeProduct } from '@/utils/business.js'

const props = defineProps({
  response: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['retry'])

const isAi = computed(() => props.response?.analysisMode === 'AI')
const items = computed(() => {
  const detailed = props.response?.recommendations
  if (Array.isArray(detailed) && detailed.length) {
    return detailed.map((item, index) => ({
      product: normalizeProduct(item.product),
      score: Number(item.score ?? Math.max(70, 92 - index * 5)),
      reason: item.reason || '현재 상품 데이터와 입고 이력을 기준으로 추천합니다.',
      signals: Array.isArray(item.signals) ? item.signals.slice(0, 3) : []
    }))
  }

  const legacy = props.response?.recommendedCourses
  return Array.isArray(legacy)
    ? legacy.map((product, index) => ({
        product: normalizeProduct(product),
        score: Math.max(70, 92 - index * 5),
        reason: '입고 이력과 발주 가능한 상품 정보를 기준으로 추천합니다.',
        signals: ['현재 발주 가능']
      }))
    : []
})
</script>

<style scoped>
.recommendation-panel {
  margin-bottom: 22px;
  overflow: hidden;
}

.recommendation-heading {
  min-height: 128px;
  padding: 24px 26px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  border-bottom: 1px solid var(--color-border);
  background: #fafbfc;
}

.section-kicker {
  color: var(--color-accent-strong);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.recommendation-heading h2 {
  margin-top: 7px;
  font-size: clamp(20px, 2.2vw, 27px);
  font-weight: 730;
  letter-spacing: -0.035em;
}

.recommendation-heading p {
  margin-top: 7px;
  color: var(--color-muted);
  font-size: 12px;
}

.analysis-badge {
  min-height: 30px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--color-border-strong);
  border-radius: 999px;
  color: var(--color-ink-soft);
  background: var(--color-surface);
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.analysis-badge i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-muted);
}

.analysis-badge.ai {
  color: var(--color-accent-strong);
  border-color: #c9d2f3;
  background: var(--color-accent-soft);
}

.analysis-badge.ai i {
  background: var(--color-accent);
}

.recommendation-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.recommendation-list li + li,
.recommendation-skeleton + .recommendation-skeleton {
  border-top: 1px solid var(--color-border);
}

.recommendation-row,
.recommendation-skeleton {
  min-height: 105px;
  padding: 18px 24px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto 22px;
  align-items: center;
  gap: 16px;
}

.recommendation-row {
  transition: background-color 160ms ease;
}

.recommendation-row:hover {
  background: #f8f9fb;
}

.recommendation-rank {
  align-self: start;
  padding-top: 2px;
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.product-line {
  display: flex;
  align-items: center;
  gap: 9px;
}

.product-line strong {
  font-size: 15px;
  font-weight: 720;
}

.product-line span {
  color: var(--color-muted);
  font-size: 10px;
}

.recommendation-copy > p {
  margin-top: 5px;
  color: var(--color-ink-soft);
  font-size: 12px;
  line-height: 1.55;
}

.signal-list {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.signal-list span {
  padding: 3px 7px;
  border-radius: 4px;
  color: var(--color-muted);
  background: var(--color-surface-muted);
  font-size: 9px;
  font-weight: 650;
}

.recommendation-meta {
  display: grid;
  justify-items: end;
  gap: 5px;
}

.recommendation-meta strong {
  font-size: 13px;
}

.recommendation-meta span {
  color: var(--color-muted);
  font-size: 9px;
}

.row-arrow {
  color: #a0a6b0;
  font-size: 15px;
  transition: transform 160ms ease, color 160ms ease;
}

.recommendation-row:hover .row-arrow {
  color: var(--color-accent);
  transform: translateX(2px);
}

.recommendation-state {
  min-height: 110px;
  padding: 20px 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--color-muted);
  font-size: 12px;
}

.recommendation-state button {
  color: var(--color-accent-strong);
  background: transparent;
  font-size: 11px;
  font-weight: 700;
}

.recommendation-skeleton {
  grid-template-columns: 38px minmax(0, 1fr);
}

.recommendation-skeleton > div {
  display: grid;
  gap: 10px;
}

.rank-skeleton {
  width: 22px;
  height: 12px;
  border-radius: 4px;
}

.title-skeleton,
.copy-skeleton {
  display: block;
  height: 12px;
  border-radius: 4px;
}

.title-skeleton { width: 34%; }
.copy-skeleton { width: 72%; }

@media (max-width: 720px) {
  .recommendation-heading {
    min-height: auto;
    padding: 20px;
  }

  .recommendation-row {
    padding: 17px 18px;
    grid-template-columns: 28px minmax(0, 1fr) 18px;
  }

  .recommendation-meta {
    display: none;
  }

  .signal-list span:nth-child(n + 3) {
    display: none;
  }
}
</style>
