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
        <a-card :title="t('page.dashboardAiTitle')">
          <div ref="pieRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :title="t('page.dashboardBaseTitle')">
          <div ref="barRef" class="chart" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useQuery } from '@tanstack/vue-query'
import { getDashboardStats } from '@/api/monitor'
import { useAppStore } from '@/stores/app'
import { useI18n } from 'vue-i18n'

use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const appStore = useAppStore()
const { t } = useI18n()
const { data: stats } = useQuery({
  queryKey: ['dashboard-stats'],
  queryFn: getDashboardStats,
})
const pieRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()
let pieChart: ReturnType<typeof init> | null = null
let barChart: ReturnType<typeof init> | null = null

const statCards = computed(() => {
  const s = stats.value
  return [
    { label: t('page.dashboardUsers'), value: s?.userCount ?? 0 },
    { label: t('page.dashboardRoles'), value: s?.roleCount ?? 0 },
    { label: t('page.dashboardMenus'), value: s?.menuCount ?? 0 },
    { label: t('page.dashboardAiTasks'), value: s?.aiTaskTotal ?? 0 },
    { label: t('page.dashboardRunning'), value: s?.aiTaskRunning ?? 0 },
    { label: t('page.dashboardLogs'), value: (s?.loginLogCount ?? 0) + (s?.operLogCount ?? 0) },
  ]
})

function renderCharts() {
  if (!stats.value || !pieRef.value || !barRef.value) {
    return
  }
  pieChart?.dispose()
  barChart?.dispose()
  pieChart = init(pieRef.value, appStore.darkTheme ? 'dark' : undefined)
  barChart = init(barRef.value, appStore.darkTheme ? 'dark' : undefined)

  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        name: t('page.dashboardAiTasks'),
        type: 'pie',
        radius: ['42%', '68%'],
        data: [
          { name: t('page.dashboardSucceeded'), value: stats.value.aiTaskSucceeded },
          { name: t('page.dashboardFailed'), value: stats.value.aiTaskFailed },
          { name: t('page.dashboardRunning'), value: stats.value.aiTaskRunning },
          { name: t('page.dashboardOthers'), value: Math.max(stats.value.aiTaskTotal - stats.value.aiTaskSucceeded - stats.value.aiTaskFailed - stats.value.aiTaskRunning, 0) },
        ],
      },
    ],
  })

  barChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: [t('page.dashboardUsersShort'), t('page.dashboardRolesShort'), t('page.dashboardMenusShort')] },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        barWidth: 36,
        data: [
          { name: t('page.dashboardUsersShort'), value: stats.value.userCount },
          { name: t('page.dashboardRolesShort'), value: stats.value.roleCount },
          { name: t('page.dashboardMenusShort'), value: stats.value.menuCount },
        ],
      },
    ],
  })
}

function resize() {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  renderCharts()
  window.addEventListener('resize', resize)
})

watch(stats, () => renderCharts())

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

