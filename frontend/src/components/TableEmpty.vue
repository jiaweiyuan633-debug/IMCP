<template>
  <a-empty :description="description || t('common.noData')" :image="simpleImage">
    <!-- antd Empty 无 extra 插槽，footer 区域由 default 插槽承载 -->
    <template v-if="$slots.action" #default>
      <slot name="action" />
    </template>
  </a-empty>
</template>

<script setup lang="ts">
import { Empty } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

withDefaults(defineProps<{ description?: string }>(), { description: '' })

const { t } = useI18n()
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE
</script>
