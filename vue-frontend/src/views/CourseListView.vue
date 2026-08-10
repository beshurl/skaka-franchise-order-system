<template>
  <WorkspaceShell
    active="products"
    title="상품 카탈로그"
    :description="auth.isHeadquarters
      ? '가맹점에 공급할 상품을 확인하고 새로운 상품을 등록합니다.'
      : '본사 공급 상품을 확인하고 필요한 상품을 발주합니다.'"
  >
    <template #actions>
      <router-link v-if="auth.isHeadquarters" to="/courses/new" class="btn btn-primary">상품 등록</router-link>
    </template>

    <section class="catalog-toolbar" aria-label="상품 검색과 필터">
      <label class="search-field">
        <span class="sr-only">상품 검색</span>
        <input v-model.trim="search" type="search" placeholder="상품명 또는 설명 검색" />
        <small>{{ filteredProducts.length }}개 상품</small>
      </label>

      <div class="category-tabs" role="group" aria-label="상품 카테고리">
        <button
          v-for="category in courseStore.categories"
          :key="category"
          type="button"
          :class="{ active: selectedCategory === category }"
          @click="courseStore.setCategory(category)"
        >
          {{ category }}
        </button>
      </div>
    </section>

    <div v-if="courseStore.loading" class="product-grid" aria-label="상품 로딩 중">
      <div v-for="index in 6" :key="index" class="product-skeleton surface">
        <div class="skeleton skeleton-visual"></div>
        <div class="skeleton-lines">
          <div class="skeleton line-short"></div>
          <div class="skeleton line-wide"></div>
          <div class="skeleton line-mid"></div>
        </div>
      </div>
    </div>

    <section v-else-if="courseStore.error" class="catalog-state surface" role="alert">
      <span>상품 데이터를 불러오지 못했습니다.</span>
      <p>{{ courseStore.error }}</p>
      <button type="button" class="btn btn-secondary" @click="courseStore.fetchCourses">다시 시도</button>
    </section>

    <div v-else-if="filteredProducts.length" class="product-grid enter">
      <ProductCard v-for="product in filteredProducts" :key="product.id" :product="product" />
    </div>

    <section v-else class="catalog-state surface">
      <span>조건에 맞는 상품이 없습니다.</span>
      <p>검색어나 카테고리를 바꾸거나 새 상품을 등록해 보세요.</p>
      <router-link v-if="auth.isHeadquarters" to="/courses/new" class="btn btn-primary">첫 상품 등록</router-link>
    </section>
  </WorkspaceShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import ProductCard from '@/components/ProductCard.vue'
import WorkspaceShell from '@/components/WorkspaceShell.vue'
import { useAuthStore } from '@/store/auth.js'
import { useCourseStore } from '@/store/course.js'

const auth = useAuthStore()
const courseStore = useCourseStore()
const search = ref('')

const selectedCategory = computed(() => courseStore.selectedCategory)
const filteredProducts = computed(() => {
  const query = search.value.toLocaleLowerCase('ko-KR')
  return courseStore.products.filter((product) => {
    const matchesCategory = selectedCategory.value === '전체' || product.categoryLabel === selectedCategory.value
    const matchesSearch = !query || `${product.name} ${product.description}`.toLocaleLowerCase('ko-KR').includes(query)
    return matchesCategory && matchesSearch
  })
})

onMounted(() => courseStore.fetchCourses())
</script>

<style scoped>
.catalog-toolbar {
  margin-bottom: 28px;
  display: grid;
  gap: 18px;
}

.search-field {
  position: relative;
  display: block;
}

.search-field input {
  width: 100%;
  min-height: 54px;
  padding: 0 104px 0 18px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  color: var(--color-ink);
  background: var(--color-surface);
  outline: none;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.search-field input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-accent) 12%, transparent);
}

.search-field input::placeholder {
  color: var(--color-muted);
}

.search-field small {
  position: absolute;
  top: 50%;
  right: 18px;
  color: var(--color-muted);
  font-size: 11px;
  transform: translateY(-50%);
}

.category-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.category-tabs::-webkit-scrollbar {
  display: none;
}

.category-tabs button {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-ink-soft);
  background: var(--color-surface);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease;
}

.category-tabs button:hover,
.category-tabs button.active {
  color: #ffffff;
  border-color: var(--color-accent);
  background: var(--color-accent);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.product-skeleton {
  overflow: hidden;
}

.skeleton-visual {
  height: 172px;
  border-radius: 0;
}

.skeleton-lines {
  padding: 22px;
  display: grid;
  gap: 11px;
}

.skeleton-lines .skeleton {
  height: 12px;
  border-radius: 6px;
}

.line-short { width: 32%; }
.line-wide { width: 86%; }
.line-mid { width: 58%; }

.catalog-state {
  min-height: 300px;
  padding: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.catalog-state span {
  font-size: 23px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.catalog-state p {
  margin: 10px 0 22px;
  color: var(--color-muted);
  font-size: 13px;
}

@media (max-width: 760px) {
  .product-grid {
    grid-template-columns: 1fr;
  }

  .catalog-state {
    min-height: 260px;
    padding: 28px;
  }
}
</style>
