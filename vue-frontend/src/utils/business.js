const roleAliases = {
  STUDENT: 'STORE_ADMIN',
  STORE_ADMIN: 'STORE_ADMIN',
  INSTRUCTOR: 'HEADQUARTERS_ADMIN',
  HEADQUARTERS_ADMIN: 'HEADQUARTERS_ADMIN'
}

export const categoryMeta = {
  BACKEND: { label: '간편식', code: 'FOOD', short: 'FD', tone: 'coral' },
  FOOD: { label: '간편식', code: 'FOOD', short: 'FD', tone: 'coral' },
  FRONTEND: { label: '음료', code: 'DRINK', short: 'DR', tone: 'aqua' },
  DRINK: { label: '음료', code: 'DRINK', short: 'DR', tone: 'aqua' },
  DEVOPS: { label: '생활용품', code: 'DAILY', short: 'DL', tone: 'olive' },
  DAILY: { label: '생활용품', code: 'DAILY', short: 'DL', tone: 'olive' },
  DATA_SCIENCE: { label: '신선식품', code: 'FRESH', short: 'FR', tone: 'leaf' },
  FRESH: { label: '신선식품', code: 'FRESH', short: 'FR', tone: 'leaf' },
  MOBILE: { label: '스낵', code: 'SNACK', short: 'SN', tone: 'amber' },
  SNACK: { label: '스낵', code: 'SNACK', short: 'SN', tone: 'amber' },
  SECURITY: { label: '위생용품', code: 'HYGIENE', short: 'HG', tone: 'blue' },
  HYGIENE: { label: '위생용품', code: 'HYGIENE', short: 'HG', tone: 'blue' },
  DATABASE: { label: '냉장식품', code: 'CHILLED', short: 'CH', tone: 'violet' },
  CHILLED: { label: '냉장식품', code: 'CHILLED', short: 'CH', tone: 'violet' },
  OTHER: { label: '기타', code: 'OTHER', short: 'OT', tone: 'stone' }
}

const orderStatusAliases = {
  PENDING: 'REQUESTED',
  ACTIVE: 'APPROVED',
  CANCELLED: 'REJECTED',
  REQUESTED: 'REQUESTED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  RECEIVED: 'RECEIVED'
}

export const orderStatusMeta = {
  REQUESTED: { label: '승인 대기', tone: 'warning', order: 1 },
  APPROVED: { label: '입고 대기', tone: 'info', order: 2 },
  RECEIVED: { label: '입고 완료', tone: 'success', order: 3 },
  REJECTED: { label: '반려', tone: 'danger', order: 4 }
}

export function normalizeRole(role) {
  return roleAliases[role] || 'STORE_ADMIN'
}

export function roleLabel(role) {
  return normalizeRole(role) === 'HEADQUARTERS_ADMIN' ? '본사 관리자' : '가맹점 관리자'
}

export function backendRole(role) {
  return normalizeRole(role) === 'HEADQUARTERS_ADMIN' ? 'INSTRUCTOR' : 'STUDENT'
}

export function getCategoryMeta(category) {
  return categoryMeta[category] || categoryMeta.OTHER
}

export function normalizeProduct(raw = {}) {
  const category = raw.category || 'OTHER'
  const meta = getCategoryMeta(category)
  const price = Number(raw.supplyPrice ?? raw.price ?? 0)
  const inventory = Number(raw.inventory ?? raw.enrollmentCount ?? raw.enrollment_count ?? 0)

  return {
    ...raw,
    id: raw.id,
    name: raw.name || raw.title || '이름 없는 상품',
    title: raw.name || raw.title || '이름 없는 상품',
    description: raw.description || '',
    category,
    categoryLabel: meta.label,
    categoryCode: meta.code,
    categoryShort: meta.short,
    categoryTone: meta.tone,
    supplyPrice: Number.isFinite(price) ? price : 0,
    price: Number.isFinite(price) ? price : 0,
    inventory: Number.isFinite(inventory) ? inventory : 0,
    orderUnit: Number(raw.orderUnit ?? 1) || 1,
    status: raw.active === false || raw.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE'
  }
}

export function normalizeOrder(raw = {}) {
  const product = normalizeProduct(raw.product || raw.course || {
    id: raw.productId || raw.courseId,
    title: raw.productName,
    price: raw.unitPrice
  })
  const status = orderStatusAliases[raw.status] || raw.status || 'REQUESTED'
  const quantity = Number(raw.quantity ?? 1) || 1
  const unitPrice = Number(raw.unitPrice ?? product.supplyPrice ?? 0) || 0

  return {
    ...raw,
    id: raw.id,
    storeId: raw.storeId ?? raw.userId,
    productId: raw.productId ?? raw.courseId ?? product.id,
    product,
    productName: raw.productName || product.name,
    quantity,
    unitPrice,
    amount: Number(raw.amount ?? unitPrice * quantity) || 0,
    status,
    statusMeta: orderStatusMeta[status] || orderStatusMeta.REQUESTED,
    requestedAt: raw.requestedAt || raw.createdAt,
    updatedAt: raw.updatedAt
  }
}

export function formatMoney(value) {
  return `${Number(value || 0).toLocaleString('ko-KR')}원`
}

export function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}
