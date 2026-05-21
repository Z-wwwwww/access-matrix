<script setup>
import { onMounted, reactive, ref } from 'vue'
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

const successOptions = [
  { label: 'すべて', value: '' },
  { label: '成功', value: 'true' },
  { label: '失敗', value: 'false' }
]

const showDetail = ref(false)
const detail = ref(null)

const columns = [
  { key: 'createTime', title: '時刻', minWidth: '170px' },
  { key: 'username', title: 'ユーザー', minWidth: '120px' },
  { key: 'module', title: 'モジュール', minWidth: '100px' },
  { key: 'action', title: 'アクション', minWidth: '160px' },
  { key: 'targetType', title: '対象', minWidth: '100px' },
  { key: 'clientIp', title: 'IP', minWidth: '120px' },
  { key: 'success', title: '結果', minWidth: '80px', align: 'center' },
  { key: 'costMs', title: 'ms', minWidth: '70px', align: 'right' },
  { key: 'actions', title: '詳細', minWidth: '70px', align: 'center', sticky: 'right' }
]

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
      toast.error(res.data.msg || '取得失敗')
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
          <label class="text-xs text-muted-foreground block mb-1">モジュール</label>
          <Input v-model="search.module" placeholder="system / pms / iot" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">アクション</label>
          <Input v-model="search.action" placeholder="role.create" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">ユーザー</label>
          <UserPicker v-model="search.userId" placeholder="全ユーザー" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">対象タイプ</label>
          <Input v-model="search.targetType" placeholder="role / user" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">対象 ID</label>
          <Input v-model="search.targetId" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">結果</label>
          <Select v-model="search.success" :options="successOptions" />
        </div>
        <div class="flex items-end gap-2 col-span-2 md:col-span-2">
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="() => { page = 1; fetchData() }">
            <Search class="size-4" /> 検索
          </button>
          <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                  @click="resetSearch">
            <RotateCcw class="size-4" /> リセット
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
            {{ row.success ? '成功' : '失敗' }}
          </Badge>
        </template>
        <template #cell-costMs="{ row }">
          <span class="font-mono text-xs">{{ row.costMs ?? '-' }}</span>
        </template>
        <template #cell-actions="{ row }">
          <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center"
                  @click="openDetail(row)" title="詳細">
            <Eye class="size-3.5" />
          </button>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showDetail" title="操作ログ詳細" width="max-w-2xl">
      <div v-if="detail" class="space-y-3 text-sm">
        <div class="grid grid-cols-2 gap-x-6 gap-y-2">
          <div><span class="text-muted-foreground">時刻：</span><span class="font-mono">{{ formatTime(detail.createTime) }}</span></div>
          <div><span class="text-muted-foreground">耗時：</span><span class="font-mono">{{ detail.costMs }} ms</span></div>
          <div><span class="text-muted-foreground">ユーザー：</span><span>{{ detail.username || '-' }}</span></div>
          <div><span class="text-muted-foreground">ユーザー ID：</span><span class="font-mono text-xs">{{ detail.userId || '-' }}</span></div>
          <div><span class="text-muted-foreground">モジュール：</span><span>{{ detail.module || '-' }}</span></div>
          <div><span class="text-muted-foreground">アクション：</span><span class="font-mono">{{ detail.action }}</span></div>
          <div><span class="text-muted-foreground">対象タイプ：</span><span>{{ detail.targetType || '-' }}</span></div>
          <div><span class="text-muted-foreground">対象 ID：</span><span class="font-mono text-xs">{{ detail.targetId || '-' }}</span></div>
          <div><span class="text-muted-foreground">メソッド：</span><span class="font-mono">{{ detail.method || '-' }}</span></div>
          <div><span class="text-muted-foreground">結果：</span>
            <Badge :variant="detail.success ? 'default' : 'destructive'">
              {{ detail.success ? '成功' : '失敗' }}
            </Badge>
          </div>
          <div class="col-span-2"><span class="text-muted-foreground">URI：</span><span class="font-mono text-xs break-all">{{ detail.requestUri || '-' }}</span></div>
          <div class="col-span-2"><span class="text-muted-foreground">クライアント IP：</span><span class="font-mono">{{ detail.clientIp || '-' }}</span></div>
          <div class="col-span-2"><span class="text-muted-foreground">User-Agent：</span><span class="text-xs break-all">{{ detail.userAgent || '-' }}</span></div>
        </div>

        <div v-if="detail.errorMsg">
          <div class="text-xs text-muted-foreground mb-1">エラーメッセージ</div>
          <pre class="bg-destructive/10 text-destructive text-xs p-2 rounded whitespace-pre-wrap break-all">{{ detail.errorMsg }}</pre>
        </div>

        <div>
          <div class="text-xs text-muted-foreground mb-1">リクエスト本文（パスワード自動マスク済み）</div>
          <pre class="bg-muted/30 text-xs p-2 rounded whitespace-pre-wrap break-all max-h-96 overflow-y-auto">{{ prettyJson(detail.requestBody) || '(なし)' }}</pre>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showDetail = false">閉じる</button>
        </div>
      </template>
    </Drawer>
  </div>
</template>
