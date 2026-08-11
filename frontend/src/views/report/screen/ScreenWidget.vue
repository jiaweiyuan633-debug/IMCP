<template>
  <div class="screen-widget">
    <div v-if="widget.title && widget.type !== 'metric'" class="widget-title">{{ widget.title }}</div>
    <div class="widget-body">
      <template v-if="widget.type === 'metric' || widget.type === 'number'">
        <div class="metric-value">{{ scalarValue }}</div>
        <div v-if="widget.type === 'metric'" class="metric-label">{{ widget.title }}</div>
      </template>
      <div v-else-if="widget.type === 'line' || widget.type === 'pie'" ref="chartEl" class="chart" />
      <a-table
        v-else-if="widget.type === 'table'"
        class="widget-table"
        size="small"
        :columns="tableColumns"
        :data-source="tableData"
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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch, ref } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useI18n } from 'vue-i18n'
import type { ReportScreenVo, NameValueVo, RecentOperVo } from '@/api/report'
import type { ScreenWidget } from './screenTypes'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  widget: ScreenWidget
  stats?: ReportScreenVo
}>()

const { t } = useI18n()

const chartEl = ref<HTMLDivElement>()
let chart: ReturnType<typeof init> | null = null

const darkText = '#a6c1e0'
const axisLine = '#1e3a5f'

/** 标量指标值（metric/number 部件） */
const scalarValue = computed(() => {
  const key = props.widget.dataKey
  if (!key || !props.stats) {
    return 0
  }
  const value = (props.stats as unknown as Record<string, unknown>)[key]
  return typeof value === 'number' ? value : 0
})

/** 图表数据数组（line/pie 部件） */
const chartData = computed<NameValueVo[]>(() => {
  const key = props.widget.dataKey
  if (!key || !props.stats) {
    return []
  }
  const value = (props.stats as unknown as Record<string, unknown>)[key]
  return Array.isArray(value) ? (value as NameValueVo[]) : []
})

const tableData = computed<Array<RecentOperVo & { rowIndex: number }>>(() =>
  (props.stats?.recentOpers || []).map((record, index) => ({ ...record, rowIndex: index })),
)

const tableColumns = [
  { title: t('page.reportOperModule'), dataIndex: 'module', key: 'module', width: 90, ellipsis: true },
  { title: t('page.reportOperAction'), dataIndex: 'action', key: 'action', width: 110, ellipsis: true },
  { title: t('page.reportOperStatus'), dataIndex: 'status', key: 'status', width: 70 },
  { title: t('page.reportDuration'), dataIndex: 'durationMs', key: 'durationMs', width: 90 },
  { title: t('page.reportOperTime'), dataIndex: 'operTime', key: 'operTime' },
]

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

function pieOption(data: NameValueVo[]) {
  return {
    tooltip: { trigger: 'item', textStyle: { color: '#fff' } },
    legend: { bottom: 0, textStyle: { color: darkText } },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        data: data.map((item) => ({ name: item.name, value: item.value })),
      },
    ],
  }
}

function renderChart() {
  if (!chartEl.value || (props.widget.type !== 'line' && props.widget.type !== 'pie')) {
    return
  }
  if (!chart) {
    chart = init(chartEl.value)
  }
  const option = props.widget.type === 'line' ? lineOption(chartData.value) : pieOption(chartData.value)
  chart.setOption(option, true)
}

let observer: ResizeObserver | null = null

onMounted(() => {
  if (props.widget.type === 'line' || props.widget.type === 'pie') {
    renderChart()
    if (chartEl.value && typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(() => chart?.resize())
      observer.observe(chartEl.value)
    }
  }
})

watch(chartData, renderChart)
watch(() => props.widget.id, renderChart)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.screen-widget {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 10px;
  border: 1px solid rgba(64, 169, 255, 0.2);
  border-radius: 6px;
  background: rgba(15, 36, 64, 0.6);
  overflow: hidden;
}

.widget-title {
  color: #7fc7ff;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.widget-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #40a9ff;
  line-height: 1.2;
}

.metric-label {
  margin-top: 6px;
  color: #a6c1e0;
  font-size: 13px;
}

.chart {
  flex: 1;
  min-height: 0;
}

.widget-table {
  flex: 1;
}

.widget-table :deep(.ant-table) {
  background: transparent;
  color: #a6c1e0;
}

.widget-table :deep(.ant-table-thead > tr > th) {
  background: rgba(64, 169, 255, 0.1);
  color: #7fc7ff;
  border-bottom: 1px solid rgba(64, 169, 255, 0.25);
}

.widget-table :deep(.ant-table-tbody > tr > td) {
  background: transparent;
  border-bottom: 1px solid rgba(30, 58, 95, 0.4);
}

.widget-table :deep(.ant-table-tbody > tr:hover > td) {
  background: rgba(64, 169, 255, 0.06) !important;
}

.text-success {
  color: #52c41a;
}

.text-danger {
  color: #ff4d4f;
}
</style>
