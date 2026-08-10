<template>
  <header class="app-header" :class="{ 'app-header-overlay': overlay }">
    <div class="header-inner">
      <router-link to="/" class="brand" aria-label="Storelink 홈">
        <span class="brand-mark" aria-hidden="true">
          <img src="/storelink-logo.png" alt="" />
        </span>
        <span class="brand-copy">
          <strong>Storelink</strong>
          <small>본사-가맹점 발주 관리</small>
        </span>
      </router-link>

      <nav class="main-nav" aria-label="주요 메뉴">
        <template v-if="auth.isAuthenticated">
          <router-link to="/courses" :class="{ active: route.path.startsWith('/courses') }">상품</router-link>
          <router-link to="/enrollments" :class="{ active: route.path === '/enrollments' }">
            {{ auth.isHeadquarters ? '발주 관리' : '내 발주' }}
          </router-link>
          <router-link to="/mypage" :class="{ active: route.path === '/mypage' }">
            {{ auth.isHeadquarters ? '운영 현황' : '재고' }}
          </router-link>
        </template>
        <template v-else>
          <a href="/#workflow">업무 흐름</a>
          <a href="/#services">서비스 구조</a>
        </template>
      </nav>

      <div class="header-actions">
        <template v-if="auth.isAuthenticated">
          <router-link to="/mypage" class="account-link">
            <span class="account-avatar">{{ userInitial }}</span>
            <span class="account-copy">
              <strong>{{ auth.user?.name || '사용자' }}</strong>
              <small>{{ auth.businessRoleLabel }}</small>
            </span>
          </router-link>
          <button class="logout-button" type="button" @click="handleLogout">로그아웃</button>
        </template>
        <router-link v-else to="/login" class="header-login">로그인</router-link>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

defineProps({
  overlay: { type: Boolean, default: false }
})

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const userInitial = computed(() => auth.user?.name?.trim()?.charAt(0) || 'S')

function handleLogout() {
  auth.logout(false)
  router.push('/')
}
</script>

<style scoped>
.app-header {
  position: sticky;
  z-index: 20;
  top: 0;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.96);
}

.app-header-overlay {
  position: absolute;
  right: 0;
  left: 0;
  color: #ffffff;
  border-bottom-color: rgba(255, 255, 255, 0.22);
  background: transparent;
}

.header-inner {
  width: min(var(--content-width), calc(100% - 64px));
  min-height: 68px;
  margin-inline: auto;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto minmax(220px, 1fr);
  align-items: center;
  gap: 24px;
}

.brand,
.account-link,
.main-nav,
.header-actions {
  display: flex;
  align-items: center;
}

.brand {
  width: fit-content;
  gap: 10px;
}

.brand-mark {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
  overflow: hidden;
  border: 1px solid rgba(49, 87, 213, 0.14);
  border-radius: 8px;
  background: #ffffff;
}

.brand-mark img {
  width: 48px;
  max-width: none;
  height: 48px;
  object-fit: cover;
}

.brand-copy,
.account-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.brand-copy strong {
  font-size: 17px;
  font-weight: 750;
  line-height: 1;
  letter-spacing: -0.025em;
}

.brand-copy small {
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 10px;
  line-height: 1;
}

.app-header-overlay .brand-copy small,
.app-header-overlay .main-nav a,
.app-header-overlay .account-copy small,
.app-header-overlay .logout-button {
  color: rgba(255, 255, 255, 0.74);
}

.main-nav {
  justify-content: center;
  gap: 28px;
}

.main-nav a {
  position: relative;
  padding: 25px 0 23px;
  color: var(--color-ink-soft);
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
}

.main-nav a:hover,
.main-nav a.active {
  color: var(--color-ink);
}

.main-nav a.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--color-accent);
  content: "";
}

.header-actions {
  justify-content: flex-end;
  gap: 14px;
}

.account-link {
  gap: 8px;
}

.account-avatar {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--color-accent-strong);
  background: var(--color-accent-soft);
  font-size: 12px;
  font-weight: 700;
}

.account-copy strong {
  max-width: 120px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-copy small {
  color: var(--color-muted);
  font-size: 10px;
}

.logout-button,
.header-login {
  padding: 8px 0;
  color: var(--color-muted);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
}

.header-login {
  color: var(--color-ink);
}

.logout-button:hover,
.header-login:hover {
  color: var(--color-accent-strong);
}

@media (max-width: 900px) {
  .header-inner {
    grid-template-columns: 1fr auto;
    min-height: 62px;
  }

  .main-nav {
    grid-column: 1 / -1;
    grid-row: 2;
    justify-content: flex-start;
    gap: 24px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .main-nav::-webkit-scrollbar {
    display: none;
  }

  .main-nav a {
    padding: 9px 0 12px;
    white-space: nowrap;
  }

  .account-copy,
  .logout-button {
    display: none;
  }
}

@media (max-width: 560px) {
  .header-inner {
    width: calc(100% - 32px);
    gap: 16px;
  }

  .brand-copy small {
    display: none;
  }
}
</style>
