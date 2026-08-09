<template>
  <a-card title="服务器监控">
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
        <a-card title="系统信息">
          <a-descriptions :column="1" bordered size="small">
            <a-descriptions-item label="操作系统">{{ data?.osName }} {{ data?.osArch }}</a-descriptions-item>
            <a-descriptions-item label="主机名">{{ data?.hostName }}</a-descriptions-item>
            <a-descriptions-item label="CPU 核心">{{ data?.cpuCores }}</a-descriptions-item>
            <a-descriptions-item label="运行时长">{{ uptimeText }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="磁盘使用">
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

const data = ref<ServerMonitorVo | null>(null)

const diskColumns = [
  { title: '分区', dataIndex: 'name', key: 'name' },
  { title: '总量', dataIndex: 'total', key: 'total', customRender: ({ text }: { text: number }) => formatBytes(text) },
  { title: '已用', dataIndex: 'used', key: 'used', customRender: ({ text }: { text: number }) => formatBytes(text) },
  { title: '使用率', dataIndex: 'usagePercent', key: 'usagePercent', customRender: ({ text }: { text: number }) => `${text}%` },
]

const cards = computed(() => {
  const d = data.value
  return [
    { label: 'CPU 负载', value: d ? `${d.cpuLoad.toFixed(2)}` : '-' },
    { label: '内存使用', value: d ? `${d.memUsagePercent}%` : '-' },
    { label: 'JVM 堆', value: d ? `${d.jvmUsagePercent}%` : '-' },
    { label: 'CPU 核心', value: d ? String(d.cpuCores) : '-' },
  ]
})

const uptimeText = computed(() => {
  if (!data.value) {
    return '-'
  }
  const seconds = data.value.uptimeSeconds
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}小时${minutes}分钟`
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
  data.value = await getServerMonitor()
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

