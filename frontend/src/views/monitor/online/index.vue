<template>
  <a-card :title="t('page.monitorOnlineTitle')">
    <div class="toolbar">
      <a-button type="primary" :loading="loading" @click="loadData">{{ t('common.search') }}</a-button>
    </div>
    <a-table :columns="columns" :data-source="records" :loading="loading" row-key="tokenId" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'actions'">
          <a v-permission="'monitor:online:kick'" @click="onKick(record)">{{ t('page.monitorKick') }}</a>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getOnlineUsers, kickOnlineUser } from '@/api/monitor'
import type { OnlineUserVo } from '@/api/monitor'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const columns = [
  { title: t('page.monitorUsername'), dataIndex: 'username', key: 'username' },
  { title: t('page.monitorIp'), dataIndex: 'ip', key: 'ip' },
  { title: t('page.monitorLoginTime'), dataIndex: 'loginTime', key: 'loginTime' },
  { title: t('page.monitorUserAgent'), dataIndex: 'userAgent', key: 'userAgent' },
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const loading = ref(false)
const records = ref<OnlineUserVo[]>([])

async function loadData() {
  loading.value = true
  try {
    records.value = await getOnlineUsers()
  } finally {
    loading.value = false
  }
}

function onKick(record: OnlineUserVo) {
  Modal.confirm({
    title: t('page.monitorKick'),
    content: `${t('page.monitorKick')} ${record.username}?`,
    onOk: async () => {
      await kickOnlineUser(record.tokenId)
      message.success(t('page.monitorKickSuccess'))
      loadData()
    },
  })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>


