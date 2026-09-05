<template>
  <a-card :title="t('page.monitorServerTitle')">
    <a-row :gutter="[16, 16]">
      <a-col v-for="item in cards" :key="item.label" :xs="12" :sm="8" :lg="6">
        <a-card class="metric-card">
          <div class="metric-value">{{ item.value }}</div>
          <div class="metric-label">{{ item.label }}</div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="12">
        <a-card :title="t('page.monitorOs')">
          <a-descriptions :column="1" bordered size="small">
            <a-descriptions-item :label="t('page.monitorOs')">{{ data?.osName }} {{ data?.osArch }}</a-descriptions-item>
            <a-descriptions-item label="Hostname">{{ data?.hostName }}</a-descriptions-item>
            <a-descriptions-item :label="`${t('page.monitorCpu')} Cores`">{{ data?.cpuCores }}</a-descriptions-item>
            <a-descriptions-item label="Uptime">{{ uptimeText }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :title="t('page.monitorDisk')">
          <a-table :columns="diskColumns" :data-source="data?.disks || []" row-key="name" :pagination="false" size="small" />
        </a-card>
      </a-col>
    </a-row>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getServerMonitor } from '@/api/monitor'
import type { ServerMonitorVo } from '@/api/monitor'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const data = ref<ServerMonitorVo | null>(null)

const diskColumns = [
  { title: t('page.monitorDisk'), dataIndex: 'name', key: 'name' },
  { title: 'Total', dataIndex: 'total', key: 'total', customRender: ({ text }: { text: number }) => formatBytes(text) },
  { title: 'Used', dataIndex: 'used', key: 'used', customRender: ({ text }: { text: number }) => formatBytes(text) },
  { title: 'Usage', dataIndex: 'usagePercent', key: 'usagePercent', customRender: ({ text }: { text: number }) => `${text}%` },
]

const cards = computed(() => {
  const d = data.value
  return [
    { label: t('page.monitorCpu'), value: d ? `${d.cpuLoad.toFixed(2)}%` : '-' },
    { label: t('page.monitorMemory'), value: d ? `${d.memUsagePercent}%` : '-' },
    { label: t('page.monitorJvm'), value: d ? `${d.jvmUsagePercent}%` : '-' },
    { label: `${t('page.monitorCpu')} Cores`, value: d ? String(d.cpuCores) : '-' },
  ]
})

const uptimeText = computed(() => {
  if (!data.value) {
    return '-'
  }
  const seconds = data.value.uptimeSeconds
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}h ${minutes}m`
})

function formatBytes(value: number) {
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index++
  }
  return `${size.toFixed(1)} ${units[index]}`
}

onMounted(async () => {
  try {
    data.value = await getServerMonitor()
  } catch {
    // 监控数据加载失败保持空态（请求层已 toast），避免未捕获 rejection
    data.value = null
  }
})
</script>

<style scoped>
.metric-card {
  text-align: center;
}

.metric-value {
  font-size: 26px;
  font-weight: 600;
}

.metric-label {
  color: #888;
  margin-top: 4px;
}
</style>

