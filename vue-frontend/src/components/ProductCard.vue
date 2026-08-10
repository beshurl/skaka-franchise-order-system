<template>
  <component
    :is="linkable ? RouterLink : 'article'"
    :to="linkable ? `/courses/${product.id}` : undefined"
    class="product-card"
    :aria-label="linkable ? `${product.name} 상세 보기` : `${product.name} 미리보기`"
  >
    <div class="product-topline">
      <span class="category-mark" :class="`tone-${product.categoryTone}`">{{ product.categoryShort }}</span>
      <span class="product-category">{{ product.categoryLabel }}</span>
      <span class="availability">{{ product.status === 'ACTIVE' ? '발주 가능' : '중지' }}</span>
    </div>

    <div class="product-copy">
      <h3>{{ product.name }}</h3>
      <p>{{ product.description || '가맹점 공급 상품입니다.' }}</p>
    </div>

    <div class="product-meta">
      <div>
        <span>공급가</span>
        <strong>{{ formatMoney(product.supplyPrice) }}</strong>
      </div>
      <div>
        <span>재고</span>
        <strong>{{ product.inventory.toLocaleString() }}</strong>
      </div>
    </div>
  </component>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import { formatMoney } from '@/utils/business.js'

defineProps({
  product: { type: Object, required: true },
  linkable: { type: Boolean, default: true }
})
</script>

<style scoped>
.product-card {
  min-height: 228px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

a.product-card:hover {
  border-color: #b7bdc7;
  box-shadow: 0 8px 22px rgba(20, 24, 32, 0.07);
}

.product-topline {
  display: flex;
  align-items: center;
  gap: 9px;
}

.category-mark {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  color: #ffffff;
  background: #69707d;
  font-size: 9px;
  font-weight: 750;
}

.tone-aqua,
.tone-blue {
  background: #3f67c8;
}

.tone-coral,
.tone-amber {
  background: #ad6245;
}

.tone-olive,
.tone-leaf {
  background: #57745d;
}

.tone-violet {
  background: #6d62a8;
}

.product-category {
  color: var(--color-muted);
  font-size: 12px;
}

.availability {
  margin-left: auto;
  color: var(--color-success);
  font-size: 11px;
  font-weight: 650;
}

.product-copy {
  margin-top: 24px;
}

.product-copy h3 {
  font-size: 20px;
  font-weight: 720;
  line-height: 1.25;
  letter-spacing: -0.025em;
}

.product-copy p {
  display: -webkit-box;
  margin-top: 8px;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-meta {
  margin-top: auto;
  padding-top: 18px;
  display: flex;
  gap: 28px;
  border-top: 1px solid var(--color-border);
}

.product-meta div {
  display: grid;
  gap: 2px;
}

.product-meta span {
  color: var(--color-muted);
  font-size: 10px;
}

.product-meta strong {
  font-size: 14px;
  font-weight: 700;
}
</style>
