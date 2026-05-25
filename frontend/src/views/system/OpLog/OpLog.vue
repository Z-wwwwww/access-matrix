<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import UserPicker from '@/components/shared/UserPicker.vue'
import { toast } from '@/composables/useToast'
import { Search, RotateCcw, Eye } from 'lucide-vue-next'
import { getOpLogListApi, getOpLogApi } from '../../../../services/oplog'

const { t } = useI18n()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const search = reactive({
  module: '',
  action: '',
  userId: '',
  targetType: '',
  targetId: '',
  success: ''
})

const successOptions = computed(() => [
  { label: t('oplog.option.result.all'), value: '' },
  { label: t('oplog.option.result.success'), value: 'true' },
  { label: t('oplog.option.result.failure'), value: 'false' }
])

const showDetail = ref(false)
const detail = ref(null)

const columns = computed(() => [
  { key: 'createTime', title: t('oplog.column.createTime'), minWidth: '170px' },
  { key: 'username', title: t('oplog.column.username'), minWidth: '120px' },
  { key: 'module', title: t('oplog.column.module'), minWidth: '100px' },
  { key: 'action', title: t('oplog.column.action'), minWidth: '160px' },
  { key: 'targetType', title: t('oplog.column.targetType'), minWidth: '100px' },
  { key: 'clientIp', title: t('oplog.column.clientIp'), minWidth: '120px' },
  { key: 'success', title: t('oplog.column.success'), minWidth: '80px', align: 'center' },
  { key: 'costMs', title: t('oplog.column.costMs'), minWidth: '70px', align: 'right' },
  { key: 'actions', title: t('oplog.column.actions'), minWidth: '70px', align: 'center', sticky: 'right' }
])

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    Object.entries(search).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params[k] = v
    })
    const res = await getOpLogListApi(params)
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally { loading.value = false }
}

function resetSearch() {
  Object.assign(search, { module: '', action: '', userId: '', targetType: '', targetId: '', success: '' })
  page.value = 1
  fetchData()
}

async function openDetail(row) {
  try {
    const res = await getOpLogApi(row.id)
    if (res.data.code === 0) {
      detail.value = res.data.data
      showDetail.value = true
    } else {
      toast.error(res.data.msg || t('oplog.message.fetchFailed'))
    }
  } catch (e) { toast.error(e.message) }
}

function formatTime(iso) {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 19)
}

function prettyJson(s) {
  if (!s) return ''
  try { return JSON.stringify(JSON.parse(s), null, 2) }
  catch { return s }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('oplog.search.label.module') }}</label>
          <Input v-model="search.module" :placeholder="t('oplog.search.placeholder.module')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('oplog.search.label.action') }}</label>
          <Input v-model="search.action" :placeholder="t('oplog.search.placeholder.action')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('oplog.search.label.user') }}</label>
          <UserPicker v-model="search.userId" :placeholder="t('oplog.search.placeholder.user')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('oplog.search.label.targetType') }}</label>
          <Input v-model="search.targetType" :placeholder="t('oplog.search.placeholder.targetType')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('oplog.search.label.targetId') }}</label>
          <Input v-model="search.targetId" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('oplog.search.label.result') }}</label>
          <Select v-model="search.success" :options="successOptions" />
        </div>
        <div class="flex items-end gap-2 col-span-2 md:col-span-2">
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="() => { page = 1; fetchData() }">
            <Search class="size-4" /> {{ t('common.button.search') }}
          </button>
          <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                  @click="resetSearch">
            <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
          </button>
        </div>
      </div>
    </Card>

    <Card>
      <DataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        @update:page="fetchData"
        @update:page-size="fetchData"
      >
        <template #cell-createTime="{ row }">
          <span class="font-mono text-xs">{{ formatTime(row.createTime) }}</span>
        </template>
        <template #cell-username="{ row }">
          {{ row.username || row.userId || '-' }}
        </template>
        <template #cell-action="{ row }">
          <span class="font-mono text-xs">{{ row.action }}</span>
        </template>
        <template #cell-targetType="{ row }">
          <span v-if="row.targetType">{{ row.targetType }}</span>
          <span v-else class="text-muted-foreground">-</span>
        </template>
        <template #cell-clientIp="{ row }">
          <span class="font-mono text-xs">{{ row.clientIp || '-' }}</span>
        </template>
        <template #cell-success="{ row }">
          <Badge :variant="row.success ? 'default' : 'destructive'">
            {{ row.success ? t('oplog.status.success') : t('oplog.status.failure') }}
          </Badge>
        </template>
        <template #cell-costMs="{ row }">
          <span class="font-mono text-xs">{{ row.costMs ?? '-' }}</span>
        </template>
        <template #cell-actions="{ row }">
          <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center"
                  @click="openDetail(row)" :title="t('common.button.detail')">
            <Eye class="size-3.5" />
          </button>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showDetail" :title="t('oplog.detail.title')" width="max-w-2xl">
      <div v-if="detail" class="space-y-3 text-sm">
        <div class="grid grid-cols-2 gap-x-6 gap-y-2">
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.createTime') }}：</span><span class="font-mono">{{ formatTime(detail.createTime) }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.costMs') }}：</span><span class="font-mono">{{ detail.costMs }} ms</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.username') }}：</span><span>{{ detail.username || '-' }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.userId') }}：</span><span class="font-mono text-xs">{{ detail.userId || '-' }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.module') }}：</span><span>{{ detail.module || '-' }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.action') }}：</span><span class="font-mono">{{ detail.action }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.targetType') }}：</span><span>{{ detail.targetType || '-' }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.targetId') }}：</span><span class="font-mono text-xs">{{ detail.targetId || '-' }}</span></div>
          <div><span class="text-muted-foreground">{{ t('oplog.detail.label.method') }}：</span><span class="font-mono">{{ detail.method || '-' }}</span></div>
          <div>
<span class="text-muted-foreground">{{ t('oplog.detail.label.result') }}：</span>
            <Badge :variant="detail.success ? 'default' : 'destructive'">
              {{ detail.success ? t('oplog.status.success') : t('oplog.status.failure') }}
            </Badge>
          </div>
          <div class="col-span-2"><span class="text-muted-foreground">{{ t('oplog.detail.label.uri') }}：</span><span class="font-mono text-xs break-all">{{ detail.requestUri || '-' }}</span></div>
          <div class="col-span-2"><span class="text-muted-foreground">{{ t('oplog.detail.label.clientIp') }}：</span><span class="font-mono">{{ detail.clientIp || '-' }}</span></div>
          <div class="col-span-2"><span class="text-muted-foreground">{{ t('oplog.detail.label.userAgent') }}：</span><span class="text-xs break-all">{{ detail.userAgent || '-' }}</span></div>
        </div>

        <div v-if="detail.errorMsg">
          <div class="text-xs text-muted-foreground mb-1">{{ t('oplog.detail.section.errorMsg') }}</div>
          <pre class="bg-destructive/10 text-destructive text-xs p-2 rounded whitespace-pre-wrap break-all">{{ detail.errorMsg }}</pre>
        </div>

        <div>
          <div class="text-xs text-muted-foreground mb-1">{{ t('oplog.detail.section.requestBody') }}</div>
          <pre class="bg-muted/30 text-xs p-2 rounded whitespace-pre-wrap break-all max-h-96 overflow-y-auto">{{ prettyJson(detail.requestBody) || t('oplog.detail.message.empty') }}</pre>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showDetail = false">{{ t('oplog.detail.button.close') }}</button>
        </div>
      </template>
    </Drawer>
  </div>
</template>
