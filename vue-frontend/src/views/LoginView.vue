<template>
  <main class="auth-page">
    <section class="auth-visual" aria-label="Storelink 서비스 소개">
      <img :src="heroImage" alt="편의점에서 입고 상품을 확인하는 관리자" />
      <div class="auth-visual-shade"></div>

      <router-link to="/" class="brand-link" aria-label="Storelink 홈">
        <span>S</span>
        <strong>Storelink</strong>
      </router-link>

      <div class="visual-copy">
        <h1>본사와 가맹점이<br />같은 발주 상태를 봅니다.</h1>
        <p>상품 발주부터 승인, 입고 확인과 재고 반영까지 한 흐름으로 관리하세요.</p>
      </div>
    </section>

    <section class="auth-content">
      <div class="auth-box">
        <router-link to="/" class="back-link">홈으로 돌아가기</router-link>

        <template v-if="!showRegister">
          <div class="auth-heading">
            <h2>로그인</h2>
            <p>계속하려면 인증 서버에서 로그인해 주세요.</p>
          </div>

          <button type="button" class="btn btn-primary auth-submit" @click="auth.redirectToLogin">
            OAuth2로 로그인
          </button>

          <p class="auth-help">로그인하면 역할에 맞는 업무 화면으로 이동합니다.</p>

          <p class="auth-switch">
            처음 이용하시나요?
            <button type="button" @click="openRegister">회원가입</button>
          </p>
        </template>

        <template v-else>
          <div class="auth-heading">
            <h2>회원가입</h2>
            <p>업무에 사용할 계정 정보를 입력해 주세요.</p>
          </div>

          <form class="register-form" novalidate @submit.prevent="handleRegister">
            <div class="field-group">
              <label for="register-name" class="field-label">이름</label>
              <input id="register-name" v-model.trim="registerForm.name" class="field-input" type="text" placeholder="서울역점 관리자" autocomplete="name" />
            </div>

            <div class="field-group">
              <label for="register-email" class="field-label">이메일</label>
              <input id="register-email" v-model.trim="registerForm.email" class="field-input" type="email" placeholder="store@example.com" autocomplete="email" />
            </div>

            <div class="field-group">
              <label for="register-password" class="field-label">비밀번호</label>
              <input id="register-password" v-model="registerForm.password" class="field-input" type="password" placeholder="8자 이상 입력" autocomplete="new-password" />
              <small>영문, 숫자 조합 제한 없이 8자 이상 입력해 주세요.</small>
            </div>

            <fieldset class="role-options">
              <legend class="field-label">업무 역할</legend>
              <label :class="{ active: registerForm.role === 'STORE_ADMIN' }">
                <input v-model="registerForm.role" type="radio" value="STORE_ADMIN" />
                <span><strong>가맹점 관리자</strong><small>상품 발주와 입고 확인</small></span>
              </label>
              <label :class="{ active: registerForm.role === 'HEADQUARTERS_ADMIN' }">
                <input v-model="registerForm.role" type="radio" value="HEADQUARTERS_ADMIN" />
                <span><strong>본사 관리자</strong><small>상품 등록과 발주 승인</small></span>
              </label>
            </fieldset>

            <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
            <p v-if="success" class="notice notice-success">{{ success }}</p>

            <button type="submit" class="btn btn-primary auth-submit" :disabled="loading">
              {{ loading ? '가입 처리 중' : '회원가입' }}
            </button>
          </form>

          <p class="auth-switch">
            이미 계정이 있나요?
            <button type="button" @click="showRegister = false">로그인</button>
          </p>
        </template>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { authApi } from '@/api/auth.js'
import { useAuthStore } from '@/store/auth.js'
import { backendRole } from '@/utils/business.js'
import heroImage from '@/assets/images/storelink-hero.jpg'

const auth = useAuthStore()
const showRegister = ref(false)
const loading = ref(false)
const error = ref('')
const success = ref('')
const registerForm = reactive({ name: '', email: '', password: '', role: 'STORE_ADMIN' })

function openRegister() {
  error.value = ''
  success.value = ''
  showRegister.value = true
}

function validate() {
  if (!registerForm.name) return '이름은 필수입니다.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) return '올바른 이메일 형식으로 입력해 주세요.'
  if (registerForm.password.length < 8) return '비밀번호는 8자 이상이어야 합니다.'
  return ''
}

async function handleRegister() {
  error.value = validate()
  success.value = ''
  if (error.value) return

  loading.value = true
  try {
    await authApi.register({
      name: registerForm.name,
      email: registerForm.email,
      password: registerForm.password,
      // 현재 Auth/User 이미지와 호환되도록 기존 enum으로 전송한다.
      role: backendRole(registerForm.role)
    })
    success.value = '회원가입이 완료되었습니다. OAuth2 로그인을 진행해 주세요.'
    registerForm.name = ''
    registerForm.email = ''
    registerForm.password = ''
    window.setTimeout(() => {
      showRegister.value = false
      success.value = ''
    }, 1400)
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '회원가입에 실패했습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(440px, 1fr) minmax(480px, 0.86fr);
  background: var(--color-surface);
}

.auth-visual {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  color: #ffffff;
  background: #22262f;
}

.auth-visual > img,
.auth-visual-shade {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.auth-visual > img {
  object-fit: cover;
  object-position: 57% center;
}

.auth-visual-shade {
  background: linear-gradient(180deg, rgba(12, 15, 22, 0.28) 0%, rgba(12, 15, 22, 0.08) 38%, rgba(12, 15, 22, 0.76) 100%);
}

.brand-link {
  position: absolute;
  z-index: 1;
  top: 36px;
  left: 40px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #ffffff;
}

.brand-link span {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--color-accent);
  font-size: 15px;
  font-weight: 800;
}

.brand-link strong {
  font-size: 18px;
  letter-spacing: -0.02em;
}

.visual-copy {
  position: absolute;
  z-index: 1;
  right: 44px;
  bottom: 48px;
  left: 40px;
}

.visual-copy h1 {
  max-width: 620px;
  font-size: clamp(34px, 4.1vw, 62px);
  font-weight: 680;
  letter-spacing: -0.045em;
  line-height: 1.08;
}

.visual-copy p {
  max-width: 520px;
  margin-top: 18px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  line-height: 1.7;
}

.auth-content {
  min-height: 100dvh;
  padding: 64px clamp(34px, 6vw, 92px);
  display: flex;
  align-items: center;
}

.auth-box {
  width: 100%;
  max-width: 440px;
  margin-inline: auto;
}

.back-link {
  display: inline-flex;
  margin-bottom: 64px;
  color: var(--color-muted);
  font-size: 13px;
}

.back-link::before {
  margin-right: 7px;
  content: "←";
}

.back-link:hover {
  color: var(--color-ink);
}

.auth-heading h2 {
  font-size: 34px;
  font-weight: 720;
  letter-spacing: -0.04em;
}

.auth-heading p {
  margin-top: 10px;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
}

.auth-submit {
  width: 100%;
  min-height: 50px;
  margin-top: 34px;
}

.auth-help {
  margin-top: 12px;
  color: var(--color-muted);
  font-size: 12px;
  text-align: center;
}

.auth-switch {
  margin-top: 38px;
  padding-top: 22px;
  border-top: 1px solid var(--color-border);
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}

.auth-switch button {
  margin-left: 5px;
  color: var(--color-accent-strong);
  font-weight: 700;
}

.register-form {
  margin-top: 30px;
  display: grid;
  gap: 19px;
}

.field-group small {
  display: block;
  margin-top: 7px;
  color: var(--color-muted);
  font-size: 11px;
}

.role-options {
  margin: 2px 0 0;
  padding: 0;
  border: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.role-options legend {
  margin-bottom: 8px;
}

.role-options > label {
  min-height: 78px;
  padding: 14px;
  display: flex;
  align-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.role-options > label.active {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-accent) 10%, transparent);
}

.role-options input {
  width: 15px;
  height: 15px;
  margin-right: 10px;
  accent-color: var(--color-accent);
}

.role-options span,
.role-options strong,
.role-options small {
  display: block;
}

.role-options strong {
  font-size: 13px;
}

.role-options small {
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 10px;
  line-height: 1.45;
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-visual {
    min-height: 320px;
  }

  .auth-visual > img {
    object-position: center 38%;
  }

  .brand-link {
    top: 24px;
    left: 24px;
  }

  .visual-copy {
    right: 24px;
    bottom: 28px;
    left: 24px;
  }

  .visual-copy p {
    max-width: 440px;
  }

  .auth-content {
    min-height: auto;
    padding: 46px 24px 64px;
  }

  .back-link {
    margin-bottom: 42px;
  }
}

@media (max-width: 520px) {
  .auth-visual {
    min-height: 270px;
  }

  .visual-copy h1 {
    font-size: 30px;
  }

  .visual-copy p {
    display: none;
  }

  .role-options {
    grid-template-columns: 1fr;
  }
}
</style>
