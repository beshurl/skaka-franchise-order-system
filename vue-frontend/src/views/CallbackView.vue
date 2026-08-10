<template>
  <main class="callback-page">
    <div class="callback-card">
      <span class="brand-mark">S</span>
      <div class="spinner" aria-hidden="true"></div>
      <h1>{{ message }}</h1>
      <p>잠시만 기다려 주세요. 업무 화면으로 연결하고 있습니다.</p>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const message = ref('로그인 확인 중')
const processing = ref(false)

onMounted(async () => {
  if (processing.value) return
  processing.value = true

  if (route.query.error) {
    message.value = '로그인에 실패했습니다'
    window.setTimeout(() => router.replace('/login'), 700)
    return
  }

  if (!route.query.code) {
    message.value = '잘못된 로그인 요청입니다'
    window.setTimeout(() => router.replace('/login'), 700)
    return
  }

  try {
    await auth.handleCallback(route.query.code)
    message.value = '로그인이 완료되었습니다'
    await router.replace('/courses')
  } catch {
    message.value = '로그인 처리에 실패했습니다'
    window.setTimeout(() => router.replace('/login'), 700)
  }
})
</script>

<style scoped>
.callback-page {
  min-height: 100dvh;
  padding: 24px;
  display: grid;
  place-items: center;
  background: var(--color-paper);
}

.callback-card {
  width: min(100%, 420px);
  padding: 48px 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  text-align: center;
}

.brand-mark {
  width: 40px;
  height: 40px;
  margin-inline: auto;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: #ffffff;
  background: var(--color-accent);
  font-weight: 800;
}

.spinner {
  margin: 34px auto 0;
}

.callback-card h1 {
  margin-top: 20px;
  font-size: 25px;
  font-weight: 720;
  letter-spacing: -0.035em;
}

.callback-card p {
  margin-top: 9px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
