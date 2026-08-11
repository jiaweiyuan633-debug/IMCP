<template>
  <a-card :title="t('page.telemetryTitle')">
    <a-row :gutter="16" align="middle" style="margin-bottom: 16px">
      <a-col :xs="24" :md="10">
        <a-select
          v-model:value="deviceId"
          :placeholder="t('page.telemetrySelectDevice')"
          style="width: 100%"
          :options="deviceOptions"
          show-search
          option-filter-prop="label"
          @change="onDeviceChange"
        />
      </a-col>
      <a-col :xs="24" :md="8">
        <a-input v-model:value="property" :placeholder="t('page.telemetryProperty')" allow-clear />
      </a-col>
      <a-col :xs="24" :md="6">
        <a-space>
          <a-button v-permission="'device:telemetry:list'" @click="loadAll">{{ t('common.search') }}</a-button>
          <a-button v-permission="'device:telemetry:report'" type="primary" :disabled="!deviceId" @click="openReport">
            {{ t('page.telemetryReport') }}
          </a-button>
        </a-space>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card size="small" :title="t('page.telemetryLatest')">
          <a-table :columns="latestColumns" :data-source="latestList" row-key="key" size="small" :pagination="false" :loading="latestLoading" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card size="small" :title="t('page.telemetryHistory')">
          <a-range-picker v-model:value="range" show-time style="margin-bottom: 12px; width: 100%" @change="onRangeChange" />
          <a-table
            :columns="historyColumns"
            :data-source="historyRecords"
            row-key="id"
            size="small"
            :loading="historyLoading"
            :pagination="{
              current: historyPageNum,
              pageSize: historyPageSize,
              total: historyTotal,
              showSizeChanger: true,
            }"
            @change="onHistoryPageChange"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-modal v-model:open="reportOpen" :title="t('page.telemetryReportTitle')" :confirm-loading="reporting" @ok="onReport">
      <a-form layout="vertical">
        <a-form-item :label="t('page.telemetryReportId')" required>
          <a-input v-model:value="reportForm.telemetryId" :placeholder="t('page.telemetryReportIdPlaceholder')" :maxlength="64" />
        </a-form-item>
        <a-form-item v-for="(point, index) in reportForm.points" :key="index" :label="`${t('page.telemetryPoint')} ${index + 1}`">
          <a-row :gutter="8">
            <a-col :span="8">
              <a-input v-model:value="point.key" :placeholder="t('page.telemetryPointKey')" />
            </a-col>
            <a-col :span="8">
              <a-input v-model:value="point.value" :placeholder="t('page.telemetryPointValue')" />
            </a-col>
            <a-col :span="8">
              <a-date-picker v-model:value="point.occurredAt" show-time :placeholder="t('page.telemetryPointTime')" style="width: 100%" />
            </a-col>
          </a-row>
        </a-form-item>
        <a-button type="dashed" block @click="addPoint">
          <PlusOutlined />
          {{ t('page.telemetryAddPoint') }}
        </a-button>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { getDevicePage } from '@/api/device'
import { getTelemetryHistory, getTelemetryLatest, reportTelemetry } from '@/api/telemetry'
import type { TelemetryLatestVo, TelemetryPointVo } from '@/api/telemetry'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const deviceId = ref<number | undefined>()
const property = ref('')
const deviceOptions = ref<{ label: string; value: number }[]>([])

async function loadDevices() {
  try {
    const page = await getDevicePage({ pageNum: 1, pageSize: 1000 })
    deviceOptions.value = page.records.map((item) => ({ label: `${item.deviceName}（${item.deviceCode}）`, value: item.id }))
  } catch {
    deviceOptions.value = []
  }
}
loadDevices()

const latestList = ref<TelemetryLatestVo[]>([])
const latestLoading = ref(false)

const latestColumns = [
  { title: t('page.telemetryKey'), dataIndex: 'key', key: 'key' },
  { title: t('page.telemetryValue'), dataIndex: 'value', key: 'value' },
  { title: t('page.telemetryTime'), dataIndex: 'occurredAt', key: 'occurredAt', width: 180 },
]

const historyRecords = ref<TelemetryPointVo[]>([])
const historyLoading = ref(false)
const historyPageNum = ref(1)
const historyPageSize = ref(10)
const historyTotal = ref(0)
const range = ref<[Dayjs, Dayjs] | null>(null)

const historyColumns = [
  { title: t('page.telemetryKey'), dataIndex: 'key', key: 'key', width: 140 },
  { title: t('page.telemetryValueNum'), dataIndex: 'valueNum', key: 'valueNum', width: 120 },
  { title: t('page.telemetryValueText'), dataIndex: 'valueText', key: 'valueText' },
  { title: t('page.telemetryTime'), dataIndex: 'occurredAt', key: 'occurredAt', width: 180 },
]

function loadLatest() {
  if (!deviceId.value) {
    return
  }
  latestLoading.value = true
  getTelemetryLatest(deviceId.value)
    .then((data) => {
      latestList.value = data
    })
    .finally(() => {
      latestLoading.value = false
    })
}

function loadHistory() {
  if (!deviceId.value) {
    return
  }
  historyLoading.value = true
  const params: Record<string, unknown> = {
    deviceId: deviceId.value,
    pageNum: historyPageNum.value,
    pageSize: historyPageSize.value,
    property: property.value || undefined,
    start: range.value ? range.value[0].format('YYYY-MM-DDTHH:mm:ss') : undefined,
    end: range.value ? range.value[1].format('YYYY-MM-DDTHH:mm:ss') : undefined,
  }
  getTelemetryHistory(params)
    .then((data) => {
      historyRecords.value = data.records
      historyTotal.value = data.total
    })
    .finally(() => {
      historyLoading.value = false
    })
}

function loadAll() {
  loadLatest()
  historyPageNum.value = 1
  loadHistory()
}

function onDeviceChange() {
  loadAll()
}

function onRangeChange() {
  historyPageNum.value = 1
  loadHistory()
}

function onHistoryPageChange(pagination: { current?: number; pageSize?: number }) {
  historyPageNum.value = pagination.current ?? 1
  historyPageSize.value = pagination.pageSize ?? 10
  loadHistory()
}

const reportOpen = ref(false)
const reporting = ref(false)
const reportForm = reactive<{ telemetryId: string; points: { key: string; value: string; occurredAt: Dayjs | null }[] }>({
  telemetryId: '',
  points: [{ key: '', value: '', occurredAt: null }],
})

function openReport() {
  reportForm.telemetryId = ''
  reportForm.points = [{ key: '', value: '', occurredAt: null }]
  reportOpen.value = true
}

function addPoint() {
  reportForm.points.push({ key: '', value: '', occurredAt: null })
}

const canReport = computed(() => reportForm.telemetryId && reportForm.points.every((p) => p.key && p.occurredAt))

async function onReport() {
  if (!canReport.value) {
    message.warning(t('page.telemetryReportRequired'))
    return
  }
  reporting.value = true
  try {
    await reportTelemetry({
      telemetryId: reportForm.telemetryId,
      deviceId: deviceId.value!,
      points: reportForm.points.map((p) => ({
        key: p.key,
        // 纯数字串转 number 存入 value_num，否则按文本/枚举存 value_text
        value: p.value.trim() && Number.isFinite(Number(p.value)) ? Number(p.value) : p.value,
        occurredAt: p.occurredAt!.format('YYYY-MM-DDTHH:mm:ss'),
      })),
    })
    message.success(t('page.telemetryReported'))
    reportOpen.value = false
    loadAll()
  } finally {
    reporting.value = false
  }
}
</script>
