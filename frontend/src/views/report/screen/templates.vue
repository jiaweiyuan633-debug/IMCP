<template>
  <div class="template-library">
    <div class="template-toolbar">
      <span class="template-title">{{ t('page.screenTemplateLibrary') }}</span>
      <span class="template-subtitle">{{ t('page.screenTemplateHint') }}</span>
    </div>
    <a-spin :spinning="loading">
      <div class="template-grid">
        <div v-for="template in templates" :key="template.id" class="template-card">
          <div class="template-body">
            <div class="template-name">{{ template.name }}</div>
            <div class="template-tags">
              <a-tag v-if="template.builtin" color="gold">{{ t('page.screenBuiltin') }}</a-tag>
              <a-tag v-else color="blue">{{ t('page.screenCustom') }}</a-tag>
              <a-tag>{{ categoryLabel(template.category) }}</a-tag>
            </div>
            <div class="template-desc">
              {{ template.remark || t('page.screenTemplateNoRemark') }}
            </div>
          </div>
          <div class="template-actions">
            <a-button size="small" type="primary" @click="useTemplate(template)">
              {{ t('page.screenUse') }}
            </a-button>
            <a-button size="small" @click="designTemplate(template)">
              {{ t('page.screenDesign') }}
            </a-button>
            <a-popconfirm :title="t('page.screenDeleteConfirm')" @confirm="removeTemplate(template)">
              <a-button v-if="!template.builtin" size="small" danger>
                {{ t('page.screenDelete') }}
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  deleteScreenTemplate,
  listScreenTemplates,
  type ScreenTemplate,
} from '@/api/screenTemplate'
import { saveLayout, type ScreenLayout } from './screenTypes'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const templates = ref<ScreenTemplate[]>([])

async function refresh() {
  loading.value = true
  try {
    templates.value = await listScreenTemplates()
  } catch {
    message.error(t('page.screenTemplateLoadFailed'))
  } finally {
    loading.value = false
  }
}

function parseLayout(template: ScreenTemplate): ScreenLayout | null {
  try {
    const parsed = JSON.parse(template.layout) as ScreenLayout
    return Array.isArray(parsed.widgets) ? parsed : null
  } catch {
    return null
  }
}

function useTemplate(template: ScreenTemplate) {
  const layout = parseLayout(template)
  if (!layout) {
    message.error(t('page.screenTemplateInvalid'))
    return
  }
  saveLayout(layout)
  message.success(t('page.screenUseDone'))
  router.push('/report/screen')
}

function designTemplate(template: ScreenTemplate) {
  const layout = parseLayout(template)
  if (!layout) {
    message.error(t('page.screenTemplateInvalid'))
    return
  }
  saveLayout(layout)
  router.push('/report/screen-designer')
}

async function removeTemplate(template: ScreenTemplate) {
  try {
    await deleteScreenTemplate(template.id)
    message.success(t('page.screenDeleteDone'))
    await refresh()
  } catch {
    message.error(t('page.screenDeleteFailed'))
  }
}

const CATEGORY_LABELS: Record<string, string> = {
  comprehensive: t('page.screenCategoryComprehensive'),
  device: t('page.screenCategoryDevice'),
  operation: t('page.screenCategoryOperation'),
  custom: t('page.screenCategoryCustom'),
}

function categoryLabel(category?: string): string {
  return category ? CATEGORY_LABELS[category] || category : '-'
}

onMounted(refresh)
</script>

<style scoped>
.template-library {
  padding: 16px;
}

.template-toolbar {
  margin-bottom: 16px;
}

.template-title {
  font-size: 18px;
  font-weight: 700;
  margin-right: 12px;
}

.template-subtitle {
  color: #888;
  font-size: 13px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.template-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.template-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.template-body {
  padding: 16px;
}

.template-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.template-tags {
  margin-bottom: 8px;
}

.template-desc {
  color: #666;
  font-size: 13px;
  min-height: 36px;
}

.template-actions {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
