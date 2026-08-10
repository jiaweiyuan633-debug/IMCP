<template>
  <a-card :title="t('page.reportCenterTitle')">
    <a-row :gutter="[16, 16]">
      <a-col v-for="card in statCards" :key="card.label" :xs="12" :sm="8" :lg="4">
        <a-card class="stat-card" size="small">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="12">
        <a-card size="small" :title="t('page.reportLoginTrend')">
          <div ref="trendRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card size="small" :title="t('page.reportOperByModule')">
          <div ref="moduleRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :lg="8">
        <a-card size="small" :title="t('page.reportDeviceByType')">
          <div ref="deviceTypeRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :lg="8">
        <a-card size="small" :title="t('page.reportDeviceByStatus')">
          <div ref="deviceStatusRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :lg="8">
        <a-card size="small" :title="t('page.reportJobByStatus')">
          <div ref="jobRef" class="chart" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :lg="8">
        <a-card size="small" :title="t('page.reportAiByStatus')">
          <div ref="aiRef" class="chart" />
        </a-card>
      </a-col>
    </a-row>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useQuery } from '@tanstack/vue-query'
import { getReportCenter } from '@/api/report'
import type { NameValueVo, ReportCenterVo } from '@/api/report'
import { useAppStore } from '@/stores/app'
import { useI18n } from 'vue-i18n'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const appStore = useAppStore()
const { t } = useI18n()
const { data: stats } = useQuery<ReportCenterVo>({
  queryKey: ['report-center'],
  queryFn: getReportCenter,
})

const trendRef = ref<HTMLDivElement>()
const moduleRef = ref<HTMLDivElement>()
const deviceTypeRef = ref<HTMLDivElement>()
const deviceStatusRef = ref<HTMLDivElement>()
const jobRef = ref<HTMLDivElement>()
const aiRef = ref<HTMLDivElement>()

const charts: ReturnType<typeof init>[] = []

const statCards = computed(() => {
  const s = stats.value
  return [
    { label: t('page.reportUsers'), value: s?.userCount ?? 0 },
    { label: t('page.reportRoles'), value: s?.roleCount ?? 0 },
    { label: t('page.reportDepts'), value: s?.deptCount ?? 0 },
    { label: t('page.reportDevices'), value: s?.deviceCount ?? 0 },
    { label: t('page.reportJobs'), value: s?.jobCount ?? 0 },
    { label: t('page.reportFlows'), value: s?.flowCount ?? 0 },
  ]
})

/** 后端返回原始值（status 1/0、job 1/0），转成可读文案 */
function localizeName(name: string): string {
  if (name === '1') return t('page.reportSucceeded')
  if (name === '0') return t('page.reportFailed')
  return name
}

function localizeStatus(name: string): string {
  if (name === '1') return t('page.reportEnabled')
  if (name === '0') return t('page.reportDisabled')
  return name
}

function pieOption(data: NameValueVo[], name: string) {
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        name,
        type: 'pie',
        radius: ['42%', '68%'],
        data: data.map((item) => ({ name: item.name, value: item.value })),
      },
    ],
  }
}

function lineOption(data: NameValueVo[]) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', boundaryGap: false, data: data.map((item) => item.name) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        data: data.map((item) => item.value),
      },
    ],
  }
}

function renderCharts() {
  if (!stats.value) {
    return
  }
  charts.forEach((chart) => chart.dispose())
  charts.length = 0
  const theme = appStore.darkTheme ? 'dark' : undefined

  if (trendRef.value) {
    const chart = init(trendRef.value, theme)
    chart.setOption(lineOption(stats.value.loginTrend))
    charts.push(chart)
  }
  const pieCharts: Array<{ el: HTMLDivElement; data: NameValueVo[]; key: string }> = [
    { el: moduleRef.value!, data: stats.value.operByModule, key: t('page.reportOperByModule') },
    { el: deviceTypeRef.value!, data: stats.value.deviceByType.map((i) => ({ ...i, name: i.name || t('page.reportOther') })), key: t('page.reportDeviceByType') },
    { el: deviceStatusRef.value!, data: stats.value.deviceByStatus.map((i) => ({ name: localizeStatus(i.name), value: i.value })), key: t('page.reportDeviceByStatus') },
    { el: jobRef.value!, data: stats.value.jobByStatus.map((i) => ({ name: localizeName(i.name), value: i.value })), key: t('page.reportJobByStatus') },
    { el: aiRef.value!, data: stats.value.aiByStatus.map((i) => ({ name: localizeName(i.name), value: i.value })), key: t('page.reportAiByStatus') },
  ]
  pieCharts.forEach(({ el, data, key }) => {
    if (!el) {
      return
    }
    const chart = init(el, theme)
    chart.setOption(pieOption(data, key))
    charts.push(chart)
  })
}

function resize() {
  charts.forEach((chart) => chart.resize())
}

onMounted(() => {
  renderCharts()
  window.addEventListener('resize', resize)
})

watch(stats, renderCharts)
watch(() => appStore.darkTheme, renderCharts)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  charts.forEach((chart) => chart.dispose())
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
  height: 300px;
}
</style>
