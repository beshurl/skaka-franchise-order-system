<template>
  <span class="status-badge" :class="`status-${meta.tone}`">{{ meta.label }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { orderStatusMeta } from '@/utils/business.js'

const props = defineProps({
  status: { type: String, required: true }
})

const meta = computed(() => orderStatusMeta[props.status] || { label: props.status, tone: 'info' })
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  min-height: 25px;
  align-items: center;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
  line-height: 1;
  white-space: nowrap;
}

.status-warning {
  color: var(--color-warning);
  background: var(--color-warning-soft);
  border-color: #ead8b5;
}

.status-info {
  color: var(--color-info);
  background: var(--color-info-soft);
  border-color: #ccd5f4;
}

.status-success {
  color: var(--color-success);
  background: var(--color-success-soft);
  border-color: #cbe6d5;
}

.status-danger {
  color: var(--color-danger);
  background: var(--color-danger-soft);
  border-color: #efd0cc;
}
</style>
