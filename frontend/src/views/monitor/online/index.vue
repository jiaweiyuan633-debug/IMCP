<template>
  <a-card title="在线用户">
    <div class="toolbar">
      <a-button type="primary" :loading="loading" @click="loadData">刷新</a-button>
    </div>
    <a-table :columns="columns" :data-source="records" :loading="loading" row-key="tokenId" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'actions'">
          <a v-permission="'monitor:online:kick'" @click="onKick(record)">强制下线</a>
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

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '登录时间', dataIndex: 'loginTime', key: 'loginTime' },
  { title: '浏览器', dataIndex: 'userAgent', key: 'userAgent' },
  { title: '操作', key: 'actions', width: 120 },
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
    title: '确认强制下线',
    content: `确定将用户 ${record.username} 强制下线吗？`,
    onOk: async () => {
      await kickOnlineUser(record.tokenId)
      message.success('已强制下线')
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


