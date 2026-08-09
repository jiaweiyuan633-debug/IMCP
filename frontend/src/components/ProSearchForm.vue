<template>
  <a-form layout="inline" class="pro-search-form">
    <a-form-item v-for="field in fields" :key="field.prop" :label="field.label">
      <a-select
        v-if="field.type === 'select'"
        v-model:value="model[field.prop]"
        :placeholder="field.placeholder || t('common.selectPlaceholder')"
        :options="field.options"
        allow-clear
        style="width: 160px"
      />
      <a-input
        v-else
        v-model:value="model[field.prop]"
        :placeholder="field.placeholder || t('common.inputPlaceholder')"
        allow-clear
        style="width: 180px"
      />
    </a-form-item>
    <a-form-item>
      <a-space>
        <a-button type="primary" :loading="loading" @click="emit('search', model)">{{ t('common.search') }}</a-button>
        <a-button @click="reset">{{ t('common.reset') }}</a-button>
        <a-button type="text" :title="t('common.refresh')" @click="emit('search', model)">
          <ReloadOutlined />
        </a-button>
      </a-space>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { ReloadOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  fields: SearchField[]
  loading?: boolean
}>()

const emit = defineEmits<{
  search: [model: Record<string, unknown>]
  reset: []
}>()

const { t } = useI18n()

const model = reactive<Record<string, unknown>>({})

watch(
  () => props.fields,
  () => {
    props.fields.forEach((field) => {
      if (!(field.prop in model)) {
        model[field.prop] = undefined
      }
    })
  },
  { immediate: true, deep: true },
)

function reset() {
  Object.keys(model).forEach((key) => {
    model[key] = undefined
  })
  emit('reset')
}
</script>

<style scoped>
.pro-search-form {
  margin-bottom: 16px;
}
</style>
