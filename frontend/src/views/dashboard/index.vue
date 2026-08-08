<template>
  <div>
    <a-row :gutter="[16, 16]">
      <a-col v-for="item in statCards" :key="item.label" :xs="12" :sm="8" :lg="4">
        <a-card class="stat-card">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="12">
        <a-card title="AI 任务状态分布">
          <div ref="pieRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="基础数据统计">
          <div ref="barRef" class="chart" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { getDashboardStats } from '@/api/monitor'
import type { DashboardStatsVo } from '@/api/monitor'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const stats = ref<DashboardStatsVo | null>(null)
const pieRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null

const statCards = computed(() => {
  const s = stats.value
  return [
    { label: '用户数', value: s?.userCount ?? 0 },
    { label: '角色数', value: s?.roleCount ?? 0 },
    { label: '菜单数', value: s?.menuCount ?? 0 },
    { label: 'AI 任务', value: s?.aiTaskTotal ?? 0 },
    { label: '进行中', value: s?.aiTaskRunning ?? 0 },
    { label: '日志数', value: (s?.loginLogCount ?? 0) + (s?.operLogCount ?? 0) },
  ]
})

function renderCharts() {
  if (!stats.value || !pieRef.value || !barRef.value) {
    return
  }
  pieChart?.dispose()
  barChart?.dispose()
  pieChart = echarts.init(pieRef.value, appStore.darkTheme ? 'dark' : undefined)
  barChart = echarts.init(barRef.value, appStore.darkTheme ? 'dark' : undefined)

  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        name: 'AI 任务',
        type: 'pie',
        radius: ['42%', '68%'],
        data: [
          { name: '成功', value: stats.value.aiTaskSucceeded },
          { name: '失败', value: stats.value.aiTaskFailed },
          { name: '进行中', value: stats.value.aiTaskRunning },
          { name: '其他', value: Math.max(stats.value.aiTaskTotal - stats.value.aiTaskSucceeded - stats.value.aiTaskFailed - stats.value.aiTaskRunning, 0) },
        ],
      },
    ],
  })

  barChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: ['用户', '角色', '菜单'] },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        barWidth: 36,
        data: [
          { name: '用户', value: stats.value.userCount },
          { name: '角色', value: stats.value.roleCount },
          { name: '菜单', value: stats.value.menuCount },
        ],
      },
    ],
  })
}

function resize() {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(async () => {
  stats.value = await getDashboardStats()
  renderCharts()
  window.addEventListener('resize', resize)
})

watch(() => appStore.darkTheme, renderCharts)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
}

.stat-label {
  color: #888;
  margin-top: 4px;
}

.chart {
  height: 320px;
}
</style>

