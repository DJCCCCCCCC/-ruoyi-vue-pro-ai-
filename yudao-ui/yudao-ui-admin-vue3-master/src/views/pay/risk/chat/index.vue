<template>
  <ContentWrap :bodyStyle="{ padding: '0px' }" class="!mb-0">
    <IFrame v-if="!loading" v-loading="loading" :src="src" />
  </ContentWrap>
</template>

<script lang="ts" setup>
defineOptions({ name: 'PayRiskChat' })

const loading = ref(true)
const src = ref(import.meta.env.VITE_CHAT_RISK_URL || 'http://localhost:5173')

onMounted(() => {
  loading.value = false
})
</script>

