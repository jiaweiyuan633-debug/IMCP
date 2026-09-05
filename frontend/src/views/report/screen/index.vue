<template>
  <div class="screen">
    <div class="screen-header">
      <div class="screen-title">{{ t('page.reportScreenTitle') }}</div>
      <div class="screen-actions">
        <a-tag v-if="connected" color="success">{{ t('page.screenRealtimeOn') }}</a-tag>
        <a-button size="small" class="action-btn" @click="goTemplates">
          {{ t('page.screenTemplateLibrary') }}
        </a-button>
        <a-button size="small" class="action-btn" type="primary" @click="goDesigner">
          {{ t('page.screenDesigner') }}
        </a-button>
        <a-button size="small" class="action-btn" @click="resetLayout">
          {{ t('page.screenResetLayout') }}
        </a-button>
        <a-divider type="vertical" />
        <span class="screen-clock">{{ now }}</span>
      </div>
    </div>

    <div class="screen-canvas">
      <div
        v-for="widget in layout.widgets"
        :key="widget.id"
        class="widget-slot"
        :style="gridStyle(widget)"
      >
        <ScreenWidget :widget="widget" :stats="stats" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ScreenWidget from './ScreenWidget.vue'
import {
  DEFAULT_LAYOUT,
  LAYOUT_STORAGE_KEY,
  gridStyle,
  loadSavedLayout,
  type ScreenLayout,
} from './screenTypes'
import { useScreenStream } from './useScreenStream'

const { t } = useI18n()
const router = useRouter()

const { stats, connected, connect } = useScreenStream()

const layout = ref<ScreenLayout>(loadSavedLayout() || DEFAULT_LAYOUT)
const now = ref('')
const tickTimer = ref<ReturnType<typeof setInterval>>()

function goDesigner() {
  router.push('/report/screen-designer')
}

function goTemplates() {
  router.push('/report/screen-templates')
}

function resetLayout() {
  localStorage.removeItem(LAYOUT_STORAGE_KEY)
  layout.value = { ...DEFAULT_LAYOUT }
  message.success(t('page.screenResetDone'))
}

function tick() {
  now.value = new Date().toLocaleString()
}

onMounted(() => {
  tick()
  tickTimer.value = setInterval(tick, 1000)
  connect()
})

onBeforeUnmount(() => {
  if (tickTimer.value) {
    clearInterval(tickTimer.value)
  }
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

.screen-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-btn {
  border-color: rgba(64, 169, 255, 0.35);
  color: #a6c1e0;
}

.action-btn.ant-btn-primary {
  background: #40a9ff;
  border-color: #40a9ff;
}

.screen-clock {
  font-size: 16px;
  color: #a6c1e0;
}

.screen-canvas {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  grid-auto-rows: 56px;
  gap: 10px;
  padding: 4px 8px;
}

.widget-slot {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
</style>
