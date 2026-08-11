<template>
  <div class="designer">
    <div class="designer-toolbar">
      <div class="designer-name">
        <a-input v-model:value="layout.name" style="width: 180px" :placeholder="t('page.screenDesignerName')" />
      </div>
      <a-select
        :value="selectedTemplateId"
        class="template-select"
        :placeholder="t('page.screenLoadTemplate')"
        allow-clear
        style="width: 200px"
        @change="applyTemplate"
      >
        <a-select-option v-for="template in templates" :key="template.id" :value="template.id">
          {{ template.name }}
        </a-select-option>
      </a-select>
      <div class="spacer" />
      <a-button @click="saveLayoutLocal">{{ t('page.screenSaveLayout') }}</a-button>
      <a-button type="primary" @click="openSaveAs">{{ t('page.screenSaveAsTemplate') }}</a-button>
      <a-button @click="goPreview">{{ t('page.screenPreview') }}</a-button>
      <a-button danger @click="clearCanvas">{{ t('page.screenClear') }}</a-button>
    </div>

    <div class="designer-body">
      <div class="palette">
        <div class="palette-title">{{ t('page.screenPalette') }}</div>
        <div
          v-for="item in palette"
          :key="item.type"
          class="palette-item"
          draggable="true"
          @dragstart="onPaletteDragStart($event, item.type)"
        >
          {{ item.label }}
        </div>
      </div>

      <div
        ref="canvasRef"
        class="designer-canvas"
        @dragover.prevent
        @drop="onCanvasDrop"
      >
        <div
          v-for="widget in layout.widgets"
          :key="widget.id"
          class="widget-slot"
          :class="{ selected: selectedId === widget.id }"
          :style="gridStyle(widget)"
          @pointerdown="onWidgetPointerDown(widget, $event)"
        >
          <ScreenWidget :widget="widget" :stats="stats" />
          <div v-if="selectedId === widget.id" class="widget-resize" @pointerdown="onResizePointerDown(widget, $event)" />
          <a-button
            v-if="selectedId === widget.id"
            class="widget-remove"
            size="small"
            danger
            type="primary"
            @click.stop="removeWidget(widget.id)"
          >
            {{ t('page.screenDelete') }}
          </a-button>
        </div>
        <div v-if="layout.widgets.length === 0" class="canvas-empty">
          {{ t('page.screenCanvasEmpty') }}
        </div>
      </div>

      <div class="inspector">
        <div class="inspector-title">{{ t('page.screenInspector') }}</div>
        <template v-if="selected">
          <div class="field">
            <label>{{ t('page.screenWidgetType') }}</label>
            <a-select v-model:value="selected.type" style="width: 100%">
              <a-select-option v-for="item in palette" :key="item.type" :value="item.type">
                {{ item.label }}
              </a-select-option>
            </a-select>
          </div>
          <div class="field">
            <label>{{ t('page.screenWidgetTitle') }}</label>
            <a-input v-model:value="selected.title" />
          </div>
          <div class="field">
            <label>{{ t('page.screenDataKey') }}</label>
            <a-select v-model:value="selected.dataKey" style="width: 100%">
              <a-select-option v-for="option in dataKeyOptions(selected.type)" :key="option" :value="option">
                {{ dataKeyLabel(option) }}
              </a-select-option>
            </a-select>
          </div>
          <div class="field-grid">
            <div class="field">
              <label>X</label>
              <a-input-number v-model:value="selected.x" :min="0" :max="GRID_COLS - selected.w" style="width: 100%" />
            </div>
            <div class="field">
              <label>Y</label>
              <a-input-number v-model:value="selected.y" :min="0" style="width: 100%" />
            </div>
            <div class="field">
              <label>W</label>
              <a-input-number v-model:value="selected.w" :min="1" :max="GRID_COLS - selected.x" style="width: 100%" />
            </div>
            <div class="field">
              <label>H</label>
              <a-input-number v-model:value="selected.h" :min="1" style="width: 100%" />
            </div>
          </div>
        </template>
        <div v-else class="inspector-empty">{{ t('page.screenSelectHint') }}</div>
      </div>
    </div>

    <a-modal
      v-model:open="saveAsOpen"
      :title="t('page.screenSaveAsTemplate')"
      @ok="saveAsTemplate"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('page.screenWidgetTitle')">
          <a-input v-model:value="saveAsName" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ScreenWidget from './ScreenWidget.vue'
import {
  GRID_COLS,
  ROW_HEIGHT,
  GRID_GAP,
  gridStyle,
  loadSavedLayout,
  saveLayout,
  type ScreenLayout,
  type ScreenWidget as ScreenWidgetType,
  type ScreenWidgetType as WidgetKind,
} from './screenTypes'
import {
  createScreenTemplate,
  listScreenTemplates,
  type ScreenTemplate,
} from '@/api/screenTemplate'
import { useScreenStream } from './useScreenStream'

const { t } = useI18n()
const router = useRouter()

const { stats, connect } = useScreenStream()
connect()

const palette: Array<{ type: WidgetKind; label: string }> = [
  { type: 'metric', label: t('page.screenMetric') },
  { type: 'number', label: t('page.screenNumber') },
  { type: 'line', label: t('page.screenLine') },
  { type: 'pie', label: t('page.screenPie') },
  { type: 'table', label: t('page.screenTable') },
]

// 画布初始加载当前已保存布局（来自「使用模板」或上次设计），保证进入即续作
const layout = ref<ScreenLayout>(
  loadSavedLayout() || { name: t('page.screenDesignerName'), theme: 'dark', widgets: [] },
)
const selectedId = ref<string>()
const selected = computed<ScreenWidgetType | undefined>(
  () => layout.value.widgets.find((widget) => widget.id === selectedId.value),
)

const templates = ref<ScreenTemplate[]>([])
const selectedTemplateId = ref<number>()
listScreenTemplates()
  .then((rows) => {
    templates.value = rows
  })
  .catch(() => {
    // 模板加载失败静默：不影响画布本地设计
  })

// 类型切换时数据源不兼容则回落到新类型的默认数据源
watch(
  () => selected.value?.type,
  (type) => {
    if (!selected.value || !type) {
      return
    }
    const options = dataKeyOptions(type)
    if (selected.value.dataKey && !options.includes(selected.value.dataKey)) {
      selected.value.dataKey = options[0]
    }
  },
)

const canvasRef = ref<HTMLDivElement>()

function cellMetrics() {
  const el = canvasRef.value
  if (!el) {
    return { cellW: 80, cellH: ROW_HEIGHT + GRID_GAP }
  }
  return { cellW: el.clientWidth / GRID_COLS, cellH: ROW_HEIGHT + GRID_GAP }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function updateWidget(id: string, mutator: (widget: ScreenWidgetType) => void) {
  const widget = layout.value.widgets.find((item) => item.id === id)
  if (widget) {
    mutator(widget)
  }
}

function addWidget(type: WidgetKind, x: number, y: number) {
  const defaults: Record<WidgetKind, { w: number; h: number; dataKey: string }> = {
    metric: { w: 3, h: 2, dataKey: 'loginSuccessCount' },
    number: { w: 3, h: 2, dataKey: 'operTotal' },
    line: { w: 4, h: 4, dataKey: 'loginTrend' },
    pie: { w: 4, h: 4, dataKey: 'operByModule' },
    table: { w: 8, h: 4, dataKey: 'recentOpers' },
  }
  const widget: ScreenWidgetType = {
    id: `w-${Date.now()}-${layout.value.widgets.length}`,
    type,
    title: dataKeyLabel(defaults[type].dataKey),
    dataKey: defaults[type].dataKey,
    x,
    y,
    w: defaults[type].w,
    h: defaults[type].h,
  }
  layout.value.widgets.push(widget)
  selectedId.value = widget.id
}

function removeWidget(id: string) {
  layout.value.widgets = layout.value.widgets.filter((widget) => widget.id !== id)
  if (selectedId.value === id) {
    selectedId.value = undefined
  }
}

function onPaletteDragStart(event: DragEvent, type: WidgetKind) {
  event.dataTransfer?.setData('application/x-screen-widget', type)
}

function onCanvasDrop(event: DragEvent) {
  event.preventDefault()
  const type = event.dataTransfer?.getData('application/x-screen-widget') as WidgetKind | undefined
  const el = canvasRef.value
  if (!type || !el) {
    return
  }
  const rect = el.getBoundingClientRect()
  const { cellW, cellH } = cellMetrics()
  const x = clamp(Math.floor((event.clientX - rect.left) / cellW), 0, GRID_COLS - 1)
  const y = clamp(Math.floor((event.clientY - rect.top) / cellH), 0, 40)
  addWidget(type, x, y)
}

function onWidgetPointerDown(widget: ScreenWidgetType, event: PointerEvent) {
  selectedId.value = widget.id
  const { cellW, cellH } = cellMetrics()
  const startX = event.clientX
  const startY = event.clientY
  const orig = { ...widget }
  const move = (e: PointerEvent) => {
    const dx = (e.clientX - startX) / cellW
    const dy = (e.clientY - startY) / cellH
    updateWidget(widget.id, (target) => {
      target.x = clamp(orig.x + Math.round(dx), 0, GRID_COLS - target.w)
      target.y = clamp(orig.y + Math.round(dy), 0, 40 - target.h)
    })
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function onResizePointerDown(widget: ScreenWidgetType, event: PointerEvent) {
  event.stopPropagation()
  event.preventDefault()
  const { cellW, cellH } = cellMetrics()
  const startX = event.clientX
  const startY = event.clientY
  const orig = { ...widget }
  const move = (e: PointerEvent) => {
    const dx = (e.clientX - startX) / cellW
    const dy = (e.clientY - startY) / cellH
    updateWidget(widget.id, (target) => {
      target.w = clamp(orig.w + Math.round(dx), 1, GRID_COLS - target.x)
      target.h = clamp(orig.h + Math.round(dy), 1, 40)
    })
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function dataKeyOptions(type: WidgetKind): string[] {
  if (type === 'line') {
    return ['loginTrend', 'operTrend']
  }
  if (type === 'pie') {
    return ['operByModule', 'deviceByType', 'deviceByStatus', 'jobByStatus', 'aiByStatus']
  }
  if (type === 'table') {
    return ['recentOpers']
  }
  return ['loginSuccessCount', 'operTotal', 'operErrorCount', 'aiTaskCount']
}

const DATA_KEY_LABELS: Record<string, string> = {
  loginSuccessCount: t('page.screenKeyLogin'),
  operTotal: t('page.screenKeyOperTotal'),
  operErrorCount: t('page.screenKeyOperError'),
  aiTaskCount: t('page.screenKeyAiTask'),
  loginTrend: t('page.screenKeyLoginTrend'),
  operTrend: t('page.screenKeyOperTrend'),
  operByModule: t('page.screenKeyOperModule'),
  deviceByType: t('page.screenKeyDeviceType'),
  deviceByStatus: t('page.screenKeyDeviceStatus'),
  jobByStatus: t('page.screenKeyJobStatus'),
  aiByStatus: t('page.screenKeyAiStatus'),
  recentOpers: t('page.screenKeyRecent'),
}

function dataKeyLabel(key?: string): string {
  return key ? DATA_KEY_LABELS[key] || key : ''
}

function saveLayoutLocal() {
  saveLayout(layout.value)
  message.success(t('page.screenSaveLayoutDone'))
}

const saveAsOpen = ref(false)
const saveAsName = ref('')

function openSaveAs() {
  saveAsName.value = layout.value.name || t('page.screenDesignerName')
  saveAsOpen.value = true
}

async function saveAsTemplate() {
  if (!saveAsName.value.trim()) {
    message.warning(t('page.screenNameRequired'))
    return
  }
  try {
    await createScreenTemplate({
      name: saveAsName.value.trim(),
      layout: JSON.stringify(layout.value),
      category: 'custom',
    })
    saveAsOpen.value = false
    message.success(t('page.screenSaveAsDone'))
    templates.value = await listScreenTemplates()
  } catch {
    message.error(t('page.screenSaveAsFailed'))
  }
}

function applyTemplate(id?: number) {
  if (!id) {
    return
  }
  const template = templates.value.find((item) => item.id === id)
  if (!template) {
    return
  }
  try {
    const parsed = JSON.parse(template.layout) as ScreenLayout
    if (Array.isArray(parsed.widgets)) {
      layout.value = parsed
      selectedId.value = undefined
    }
  } catch {
    message.error(t('page.screenTemplateInvalid'))
  }
}

function clearCanvas() {
  layout.value.widgets = []
  selectedId.value = undefined
}

function goPreview() {
  saveLayout(layout.value)
  router.push('/report/screen')
}
</script>

<style scoped>
.designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
}

.designer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(64, 169, 255, 0.2);
  background: rgba(15, 36, 64, 0.5);
}

.designer-name {
  display: flex;
  align-items: center;
}

.spacer {
  flex: 1;
}

.designer-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.palette {
  width: 140px;
  padding: 12px;
  border-right: 1px solid rgba(64, 169, 255, 0.2);
}

.palette-title,
.inspector-title {
  color: #7fc7ff;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.palette-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px dashed rgba(64, 169, 255, 0.4);
  border-radius: 6px;
  color: #a6c1e0;
  cursor: grab;
  text-align: center;
  font-size: 13px;
}

.palette-item:hover {
  background: rgba(64, 169, 255, 0.1);
}

.designer-canvas {
  position: relative;
  flex: 1;
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  grid-auto-rows: 56px;
  gap: 10px;
  padding: 14px;
  overflow: auto;
  background: linear-gradient(180deg, #0b1a2e 0%, #0f2440 100%);
}

.widget-slot {
  position: relative;
  min-width: 0;
  min-height: 0;
  cursor: move;
}

.widget-slot.selected {
  outline: 2px solid #40a9ff;
  outline-offset: -1px;
}

.widget-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 5;
}

.widget-resize {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 14px;
  height: 14px;
  cursor: nwse-resize;
  border-right: 2px solid #40a9ff;
  border-bottom: 2px solid #40a9ff;
}

.canvas-empty {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a6c1e0;
  opacity: 0.6;
}

.inspector {
  width: 220px;
  padding: 12px;
  border-left: 1px solid rgba(64, 169, 255, 0.2);
  overflow-y: auto;
}

.field {
  margin-bottom: 10px;
}

.field label {
  display: block;
  color: #a6c1e0;
  font-size: 12px;
  margin-bottom: 4px;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.inspector-empty {
  color: #a6c1e0;
  opacity: 0.6;
  font-size: 13px;
}
</style>
