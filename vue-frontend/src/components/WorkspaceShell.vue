<template>
  <div class="workspace-page">
    <AppHeader />

    <div class="workspace-grid">
      <aside class="workspace-sidebar" aria-label="업무 메뉴">
        <div class="workspace-label">
          <span>{{ auth.isHeadquarters ? '본사 업무' : '가맹점 업무' }}</span>
          <strong>{{ auth.user?.name || '사용자' }}</strong>
        </div>

        <nav class="side-nav">
          <router-link to="/courses" :class="{ active: active === 'products' }">
            <span>상품</span>
          </router-link>
          <router-link to="/enrollments" :class="{ active: active === 'orders' }">
            <span>{{ auth.isHeadquarters ? '발주 관리' : '내 발주' }}</span>
          </router-link>
          <router-link to="/payments" :class="{ active: active === 'payments' }">
            <span>{{ auth.isHeadquarters ? '전체 정산' : '내 정산' }}</span>
          </router-link>
          <router-link to="/mypage" :class="{ active: active === 'inventory' }">
            <span>{{ auth.isHeadquarters ? '운영 현황' : '재고' }}</span>
          </router-link>
        </nav>

        <p class="gateway-note">기존 Gateway 경로 안에서 상품과 발주 데이터를 관리합니다.</p>
      </aside>

      <main class="workspace-main">
        <header class="page-heading">
          <div>
            <span class="page-context">{{ auth.isHeadquarters ? '본사 관리자' : '가맹점 관리자' }}</span>
            <h1>{{ title }}</h1>
            <p v-if="description">{{ description }}</p>
          </div>
          <div v-if="$slots.actions" class="page-actions">
            <slot name="actions" />
          </div>
        </header>

        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import { useAuthStore } from '@/store/auth.js'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  kicker: { type: String, default: '' },
  active: { type: String, required: true }
})

const auth = useAuthStore()
</script>

<style scoped>
.workspace-page {
  min-height: 100dvh;
}

.workspace-grid {
  width: min(var(--content-width), calc(100% - 64px));
  margin-inline: auto;
  padding: 40px 0 80px;
  display: grid;
  grid-template-columns: 176px minmax(0, 1fr);
  gap: 56px;
}

.workspace-sidebar {
  position: sticky;
  top: 108px;
  height: fit-content;
}

.workspace-label {
  padding: 0 10px 20px;
  border-bottom: 1px solid var(--color-border);
}

.workspace-label span,
.workspace-label strong {
  display: block;
}

.workspace-label span {
  color: var(--color-muted);
  font-size: 11px;
}

.workspace-label strong {
  margin-top: 5px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-nav {
  display: grid;
  gap: 3px;
  margin-top: 14px;
}

.side-nav a {
  min-height: 42px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  border-left: 2px solid transparent;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  color: var(--color-muted);
  font-size: 14px;
  font-weight: 600;
}

.side-nav a:hover {
  color: var(--color-ink);
  background: rgba(255, 255, 255, 0.62);
}

.side-nav a.active {
  color: var(--color-accent-strong);
  border-left-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.gateway-note {
  margin-top: 28px;
  padding: 16px 10px 0;
  border-top: 1px solid var(--color-border);
  color: var(--color-muted);
  font-size: 11px;
  line-height: 1.6;
}

.workspace-main {
  min-width: 0;
}

.page-heading {
  min-height: 116px;
  margin-bottom: 30px;
  padding-bottom: 24px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  border-bottom: 1px solid var(--color-border-strong);
}

.page-context {
  color: var(--color-accent-strong);
  font-size: 12px;
  font-weight: 650;
}

.page-heading h1 {
  margin-top: 8px;
  font-size: clamp(32px, 3.5vw, 44px);
  font-weight: 720;
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.page-heading p {
  max-width: 650px;
  margin-top: 10px;
  color: var(--color-muted);
  font-size: 14px;
}

.page-actions {
  padding-bottom: 2px;
}

@media (max-width: 940px) {
  .workspace-grid {
    grid-template-columns: 1fr;
    gap: 24px;
    padding-top: 24px;
  }

  .workspace-sidebar {
    position: static;
  }

  .workspace-label,
  .gateway-note {
    display: none;
  }

  .side-nav {
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
    margin: 0;
  }

  .side-nav a {
    justify-content: center;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
  }

  .side-nav a.active {
    border-color: #bec9ee;
  }
}

@media (max-width: 640px) {
  .workspace-grid {
    width: calc(100% - 32px);
    padding-bottom: 48px;
  }

  .page-heading {
    min-height: 0;
    align-items: stretch;
    flex-direction: column;
  }

  .page-heading h1 {
    font-size: 34px;
  }

  .page-actions,
  .page-actions :deep(.btn) {
    width: 100%;
  }
}
</style>
