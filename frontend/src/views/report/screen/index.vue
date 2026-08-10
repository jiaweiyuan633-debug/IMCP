<template>
  <div class="screen">
    <div class="screen-header">
      <div class="screen-title">{{ t('page.reportScreenTitle') }}</div>
      <div class="screen-clock">{{ now }}</div>
    </div>

    <a-row :gutter="[16, 16]" class="metric-row">
      <a-col v-for="card in metricCards" :key="card.label" :xs="12" :lg="3">
        <div class="metric-card">
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-label">{{ card.label }}</div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportLoginTrend') }}</div>
          <div ref="loginRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportOperTrend') }}</div>
          <div ref="operTrendRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportOperByModule') }}</div>
          <div ref="moduleRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportDeviceByType') }}</div>
          <div ref="deviceTypeRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportDeviceByStatus') }}</div>
          <div ref="deviceStatusRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportJobByStatus') }}</div>
          <div ref="jobRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="8">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportAiByStatus') }}</div>
          <div ref="aiRef" class="chart" />
        </div>
      </a-col>
      <a-col :xs="24" :lg="16">
        <div class="panel">
          <div class="panel-title">{{ t('page.reportRecentOpers') }}</div>
          <a-table
            class="screen-table"
            size="small"
            :columns="operColumns"
            :data-source="stats?.recentOpers || []"
            :pagination="false"
            row-key="rowIndex"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <span :class="record.status === 1 ? 'text-success' : 'text-danger'">
                  {{ record.status === 1 ? t('page.reportSucceeded') : t('page.reportFailed') }}
                </span>
              </template>
              <template v-else-if="column.key === 'durationMs'">
                {{ record.durationMs != null ? `${record.durationMs}ms` : '-' }}
              </template>
              <template v-else-if="column.key === 'operTime'">
                {{ record.operTime || '-' }}
              </template>
            </template>
          </a-table>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useQuery } from '@tanstack/vue-query'
import { getReportScreen } from '@/api/report'
import type { NameValueVo, ReportScreenVo } from '@/api/report'
import { useI18n } from 'vue-i18n'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const { t } = useI18n()
const { data: stats } = useQuery<ReportScreenVo>({
  queryKey: ['report-screen'],
  queryFn: getReportScreen,
  refetchInterval: 60_000,
})

const loginRef = ref<HTMLDivElement>()
const operTrendRef = ref<HTMLDivElement>()
const moduleRef = ref<HTMLDivElement>()
const deviceTypeRef = ref<HTMLDivElement>()
const deviceStatusRef = ref<HTMLDivElement>()
const jobRef = ref<HTMLDivElement>()
const aiRef = ref<HTMLDivElement>()

const charts: ReturnType<typeof init>[] = []
const now = ref('')

const darkText = '#a6c1e0'
const axisLine = '#1e3a5f'

const metricCards = computed(() => {
  const s = stats.value
  return [
    { label: t('page.reportLoginSuccess'), value: s?.loginSuccessCount ?? 0 },
    { label: t('page.reportOperTotal'), value: s?.operTotal ?? 0 },
    { label: t('page.reportOperError'), value: s?.operErrorCount ?? 0 },
    { label: t('page.reportAiTasks'), value: s?.aiTaskCount ?? 0 },
  ]
})

const operColumns = [
  { title: t('page.reportOperModule'), dataIndex: 'module', key: 'module', width: 90, ellipsis: true },
  { title: t('page.reportOperAction'), dataIndex: 'action', key: 'action', width: 110, ellipsis: true },
  { title: t('page.reportOperStatus'), dataIndex: 'status', key: 'status', width: 70 },
  { title: t('page.reportDuration'), dataIndex: 'durationMs', key: 'durationMs', width: 90 },
  { title: t('page.reportOperTime'), dataIndex: 'operTime', key: 'operTime' },
]

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

function lineOption(data: NameValueVo[]) {
  return {
    tooltip: { trigger: 'axis', textStyle: { color: '#fff' } },
    grid: { left: 40, right: 16, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map((item) => item.name),
      axisLabel: { color: darkText },
      axisLine: { lineStyle: { color: axisLine } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: darkText },
      splitLine: { lineStyle: { color: 'rgba(30,58,95,0.4)' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: '#40a9ff' },
        lineStyle: { width: 2 },
        areaStyle: { color: 'rgba(64,169,255,0.18)' },
        data: data.map((item) => item.value),
      },
    ],
  }
}

function pieOption(data: NameValueVo[], name: string) {
  return {
    tooltip: { trigger: 'item', textStyle: { color: '#fff' } },
    legend: { bottom: 0, textStyle: { color: darkText } },
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

function renderCharts() {
  if (!stats.value) {
    return
  }
  charts.forEach((chart) => chart.dispose())
  charts.length = 0

  const lineCharts: Array<{ el: HTMLDivElement; data: NameValueVo[] }> = [
    { el: loginRef.value!, data: stats.value.loginTrend },
    { el: operTrendRef.value!, data: stats.value.operTrend },
  ]
  lineCharts.forEach(({ el, data }) => {
    if (!el) {
      return
    }
    const chart = init(el)
    chart.setOption(lineOption(data))
    charts.push(chart)
  })

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
    const chart = init(el)
    chart.setOption(pieOption(data, key))
    charts.push(chart)
  })
}

function resize() {
  charts.forEach((chart) => chart.resize())
}

function tick() {
  now.value = new Date().toLocaleString()
}

onMounted(() => {
  tick()
  window.setInterval(tick, 1000)
  renderCharts()
  window.addEventListener('resize', resize)
})

watch(stats, renderCharts)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  charts.forEach((chart) => chart.dispose())
})
</script>

<style scoped>
.screen {
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #0b1a2e 0%, #0f2440 100%);
  padding: 16px;
  margin: -16px -24px -24px;
}

.screen-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px 16px;
  color: #7fc7ff;
}

.screen-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 4px;
}

.screen-clock {
  font-size: 16px;
  color: #a6c1e0;
}

.metric-row {
  margin-bottom: 16px;
}

.metric-card {
  text-align: center;
  padding: 16px 8px;
  border: 1px solid rgba(64, 169, 255, 0.25);
  border-radius: 6px;
  background: rgba(64, 169, 255, 0.08);
}

.metric-value {
  font-size: 30px;
  font-weight: 700;
  color: #40a9ff;
}

.metric-label {
  margin-top: 6px;
  color: #a6c1e0;
  font-size: 13px;
}

.panel {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid rgba(64, 169, 255, 0.2);
  border-radius: 6px;
  background: rgba(15, 36, 64, 0.6);
}

.panel-title {
  color: #7fc7ff;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.chart {
  height: 240px;
}

.screen-table :deep(.ant-table) {
  background: transparent;
  color: #a6c1e0;
}

.screen-table :deep(.ant-table-thead > tr > th) {
  background: rgba(64, 169, 255, 0.1);
  color: #7fc7ff;
  border-bottom: 1px solid rgba(64, 169, 255, 0.25);
}

.screen-table :deep(.ant-table-tbody > tr > td) {
  background: transparent;
  border-bottom: 1px solid rgba(30, 58, 95, 0.4);
}

.screen-table :deep(.ant-table-tbody > tr:hover > td) {
  background: rgba(64, 169, 255, 0.06) !important;
}

.text-success {
  color: #52c41a;
}

.text-danger {
  color: #ff4d4f;
}
</style>
