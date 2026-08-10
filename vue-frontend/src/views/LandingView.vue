<template>
  <div class="landing-page">
    <AppHeader />

    <main>
      <section class="hero container">
        <div class="hero-copy">
          <p class="hero-label">편의점 본사-가맹점 발주 관리</p>
          <h1>가맹점 발주를<br />한 흐름으로<br />관리합니다.</h1>
          <p class="hero-description">가맹점 요청부터 본사 승인, 입고 확인과 재고 반영까지 같은 상태를 봅니다.</p>
          <div class="hero-actions">
            <router-link :to="primaryDestination" class="btn btn-primary">
              {{ auth.isAuthenticated ? '업무 화면 열기' : '시작하기' }}
            </router-link>
            <a href="#workflow" class="btn btn-secondary">업무 흐름 보기</a>
          </div>
        </div>

        <figure class="hero-media">
          <img
            :src="heroImage"
            alt="편의점 입고 상품의 바코드를 확인하는 가맹점 관리자"
            fetchpriority="high"
          />
        </figure>
      </section>

      <section id="workflow" class="workflow-section">
        <div class="container workflow-inner">
          <div class="workflow-intro">
            <h2>요청과 확인 사이의<br />누락을 줄입니다.</h2>
            <p>상태는 정해진 순서로만 변경됩니다. 입고가 끝난 시점에만 재고를 반영합니다.</p>
          </div>

          <figure class="workflow-media">
            <img :src="receivingImage" alt="입고 목록과 실제 상품을 대조하는 모습" loading="lazy" />
          </figure>

          <ol class="workflow-list">
            <li v-for="item in workflow" :key="item.status">
              <div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.description }}</p>
              </div>
              <code>{{ item.status }}</code>
            </li>
          </ol>
        </div>
      </section>

      <section id="services" class="services-section container">
        <div class="section-heading">
          <h2>기존 Gateway 계약을<br />업무에 맞게 사용합니다.</h2>
          <p>새 경로를 추가하지 않고 제공된 서비스 경로에 상품, 발주, 정산 책임을 배치했습니다.</p>
        </div>

        <div class="route-list" role="list" aria-label="Gateway 서비스 경로">
          <article v-for="service in services" :key="service.path" role="listitem">
            <div>
              <strong>{{ service.name }}</strong>
              <p>{{ service.description }}</p>
            </div>
            <code>{{ service.path }}</code>
          </article>
        </div>

      </section>

      <section class="roles-section container">
        <article>
          <span>본사 관리자</span>
          <h2>상품과 발주 결정을 관리합니다.</h2>
          <p>공급 상품을 등록하고 가맹점의 발주 요청을 승인하거나 반려합니다.</p>
        </article>
        <article>
          <span>가맹점 관리자</span>
          <h2>발주와 입고 결과를 확인합니다.</h2>
          <p>필요한 상품을 요청하고 승인 상태를 확인한 뒤 실제 입고를 완료합니다.</p>
        </article>
      </section>
    </main>

    <footer class="landing-footer">
      <div class="container footer-inner">
        <div>
          <strong>Storelink</strong>
          <span>본사와 가맹점을 잇는 발주 관리</span>
        </div>
        <router-link :to="primaryDestination">업무 시작하기</router-link>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { useAuthStore } from '@/store/auth.js'
import heroImage from '@/assets/images/storelink-hero.jpg'
import receivingImage from '@/assets/images/storelink-receiving.jpg'

const auth = useAuthStore()
const primaryDestination = computed(() => auth.isAuthenticated ? '/courses' : '/login')

const workflow = [
  { title: '발주 요청', description: '가맹점이 필요한 상품을 선택합니다.', status: 'REQUESTED' },
  { title: '승인 또는 반려', description: '본사가 요청 내용을 확인하고 결정합니다.', status: 'APPROVED / REJECTED' },
  { title: '입고 확인', description: '가맹점 확인이 끝나면 재고가 반영됩니다.', status: 'RECEIVED' }
]

const services = [
  { name: '사용자 관리', path: '/api/users/**', description: '사용자 정보와 본사, 가맹점 역할을 관리합니다.' },
  { name: '상품 관리', path: '/api/courses/**', description: 'Course Service를 공급 상품 카탈로그로 사용합니다.' },
  { name: '발주 관리', path: '/api/enrollments/**', description: 'Enrollment Service에서 발주와 상태 변경을 처리합니다.' },
  { name: '정산 관리', path: '/api/payments/**', description: 'Payment Service의 기존 정산 흐름을 유지합니다.' }
]
</script>

<style scoped>
.landing-page {
  min-height: 100dvh;
  background: #ffffff;
}

.hero {
  min-height: calc(100dvh - 68px);
  padding-top: 48px;
  padding-bottom: 48px;
  display: grid;
  grid-template-columns: minmax(0, 0.84fr) minmax(520px, 1.16fr);
  align-items: center;
  gap: clamp(44px, 7vw, 104px);
}

.hero-copy {
  max-width: 560px;
}

.hero-label {
  color: var(--color-accent-strong);
  font-size: 13px;
  font-weight: 650;
}

.hero h1 {
  margin-top: 20px;
  font-size: clamp(45px, 5.2vw, 72px);
  font-weight: 760;
  line-height: 1.05;
  letter-spacing: -0.055em;
}

.hero-description {
  max-width: 500px;
  margin-top: 24px;
  color: var(--color-ink-soft);
  font-size: 17px;
  line-height: 1.7;
}

.hero-actions {
  margin-top: 30px;
  display: flex;
  gap: 9px;
}

.hero-media {
  height: min(72vh, 720px);
  min-height: 480px;
  margin: 0;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: #e8eaee;
}

.hero-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 58% center;
}

.workflow-section {
  padding: 120px 0;
  background: #f1f3f6;
}

.workflow-inner {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 72px 96px;
}

.workflow-intro h2,
.section-heading h2 {
  font-size: clamp(36px, 4vw, 54px);
  font-weight: 740;
  line-height: 1.12;
  letter-spacing: -0.045em;
}

.workflow-intro p,
.section-heading p {
  max-width: 520px;
  margin-top: 20px;
  color: var(--color-muted);
  font-size: 15px;
  line-height: 1.75;
}

.workflow-media {
  grid-row: 1 / span 2;
  grid-column: 1;
  align-self: end;
  margin: 0;
  overflow: hidden;
  border-radius: var(--radius-md);
}

.workflow-media img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
}

.workflow-intro,
.workflow-list {
  grid-column: 2;
}

.workflow-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.workflow-list li {
  min-height: 100px;
  padding: 20px 0;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--color-border-strong);
}

.workflow-list li:first-child {
  border-top: 1px solid var(--color-border-strong);
}

.workflow-list strong {
  font-size: 16px;
}

.workflow-list p {
  margin-top: 5px;
  color: var(--color-muted);
  font-size: 13px;
}

.workflow-list code,
.route-list code {
  color: var(--color-accent-strong);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 650;
}

.services-section {
  padding-top: 120px;
  padding-bottom: 120px;
}

.section-heading {
  max-width: 740px;
}

.route-list {
  margin-top: 64px;
  border-top: 1px solid var(--color-border-strong);
}

.route-list article {
  min-height: 104px;
  padding: 22px 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, auto);
  align-items: center;
  gap: 32px;
  border-bottom: 1px solid var(--color-border);
}

.route-list strong {
  font-size: 17px;
}

.route-list p {
  margin-top: 5px;
  color: var(--color-muted);
  font-size: 13px;
}

.route-list code {
  justify-self: end;
  font-size: 13px;
}

.roles-section {
  padding-top: 100px;
  padding-bottom: 120px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  border-top: 1px solid var(--color-border-strong);
}

.roles-section article {
  min-height: 300px;
  padding: 20px 64px 0 0;
  display: flex;
  flex-direction: column;
}

.roles-section article + article {
  padding-right: 0;
  padding-left: 64px;
  border-left: 1px solid var(--color-border-strong);
}

.roles-section span {
  color: var(--color-accent-strong);
  font-size: 13px;
  font-weight: 650;
}

.roles-section h2 {
  max-width: 450px;
  margin-top: auto;
  font-size: clamp(31px, 3.2vw, 44px);
  font-weight: 730;
  line-height: 1.15;
  letter-spacing: -0.04em;
}

.roles-section p {
  max-width: 450px;
  margin-top: 18px;
  color: var(--color-muted);
  font-size: 14px;
}

.landing-footer {
  border-top: 1px solid var(--color-border);
  background: #f4f5f7;
}

.footer-inner {
  min-height: 112px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.footer-inner > div {
  display: grid;
  gap: 4px;
}

.footer-inner strong {
  font-size: 16px;
}

.footer-inner span,
.footer-inner a {
  color: var(--color-muted);
  font-size: 12px;
}

.footer-inner a:hover {
  color: var(--color-accent-strong);
}

@media (max-width: 980px) {
  .hero {
    min-height: auto;
    grid-template-columns: 1fr;
    padding-top: 64px;
    padding-bottom: 72px;
  }

  .hero-copy {
    max-width: 680px;
  }

  .hero-media {
    height: 58vw;
    min-height: 420px;
  }

  .workflow-inner {
    grid-template-columns: 1fr;
    gap: 44px;
  }

  .workflow-media,
  .workflow-intro,
  .workflow-list {
    grid-column: 1;
    grid-row: auto;
  }

  .workflow-media {
    max-width: 720px;
  }
}

@media (max-width: 680px) {
  .hero {
    padding-top: 48px;
  }

  .hero h1 {
    font-size: 43px;
  }

  .hero-description {
    font-size: 15px;
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-media {
    height: 92vw;
    min-height: 390px;
  }

  .workflow-section,
  .services-section {
    padding-top: 80px;
    padding-bottom: 80px;
  }

  .workflow-intro h2,
  .section-heading h2 {
    font-size: 36px;
  }

  .workflow-list li,
  .route-list article {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .route-list code {
    justify-self: start;
  }

  .roles-section {
    padding-top: 72px;
    padding-bottom: 80px;
    grid-template-columns: 1fr;
  }

  .roles-section article,
  .roles-section article + article {
    min-height: 250px;
    padding: 24px 0 48px;
    border-left: 0;
  }

  .roles-section article + article {
    padding-top: 48px;
    border-top: 1px solid var(--color-border);
  }

  .footer-inner {
    padding: 28px 0;
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
