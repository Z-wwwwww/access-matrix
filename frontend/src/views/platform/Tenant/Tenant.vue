<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthStore } from '@/stores/auth'
import { Plus, Search, RotateCcw, Trash2, Pause, Play, Pencil, LifeBuoy } from 'lucide-vue-next'
import {
  listTenantsApi,
  suspendTenantApi, resumeTenantApi,
  startSupportSessionApi
} from '../../../../services/tenant'
import TenantCreate from './TenantCreate.vue'
import TenantEdit from './TenantEdit.vue'
import TenantSupportSession from './TenantSupportSession.vue'
import TenantHardDelete from './TenantHardDelete.vue'

const { t } = useI18n()
const { confirm } = useConfirm()
const auth = useAuthStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '' })

const showCreate = ref(false)
const editTarget = ref(null)            // null = closed; row object = open with that row
const supportTarget = ref(null)         // null = closed; row object = open support-session dialog
const hardDeleteTarget = ref(null)      // null = closed; row object = open hard-delete confirmation

const columns = [
  { key: 'tenantCode',    label: () => t('platform.tenant.column.tenantCode'),    width: '180px' },
  { key: 'displayName',   label: () => t('platform.tenant.column.displayName') },
  { key: 'contactEmail',  label: () => t('platform.tenant.column.contactEmail'), width: '240px' },
  { key: 'status',        label: () => t('platform.tenant.column.status'),        width: '100px' },
  { key: 'createTime',    label: () => t('platform.tenant.column.createTime'),    width: '180px' },
  { key: 'actions',       label: () => t('platform.tenant.column.actions'),       width: '160px' }
]

async function fetchData() {
  loading.value = true
  try {
    const res = await listTenantsApi({
      page: page.value, size: pageSize.value, keyword: search.keyword || undefined
    })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.loadFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  page.value = 1
  fetchData()
}

function openCreate() {
  showCreate.value = true
}

function openEdit(row) {
  editTarget.value = row
}

function isBuiltIn(row) {
  return row.tenantCode === 'system' || row.tenantCode === 'demo'
}

function openHardDelete(row) {
  // Hard delete uses a typed-confirmation modal — too dangerous for the
  // generic useConfirm dialog. The modal also enforces "row must be
  // suspended" matching the backend gate; we don't surface the button
  // for active rows in the first place.
  hardDeleteTarget.value = row
}

async function handleSuspend(row) {
  const ok = await confirm({
    title: t('platform.tenant.confirm.suspendTitle'),
    message: t('platform.tenant.confirm.suspendMessage', {
      tenantCode: row.tenantCode,
      displayName: row.displayName
    }),
    confirmText: t('platform.tenant.confirm.suspendConfirm')
  })
  if (!ok) return
  try {
    const res = await suspendTenantApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.suspendSuccess'))
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.suspendFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleResume(row) {
  // Resume is non-destructive — confirm-less is fine.
  try {
    const res = await resumeTenantApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.resumeSuccess'))
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.resumeFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

function openSupportSession(row) {
  supportTarget.value = row
}

async function handleSupportSession({ row, reason }) {
  try {
    const res = await startSupportSessionApi(row.id, reason)
    if (res.data.code === 0) {
      const data = res.data.data
      auth.enterSupportSession(data.token, {
        sessionId:   data.sessionId,
        tenantCode:  data.tenantCode,
        displayName: data.displayName,
        expiresAt:   data.expiresAt
      })
      supportTarget.value = null
      toast.success(t('platform.tenant.support.message.started', {
        tenantCode: data.tenantCode
      }))
      // Hard navigation (NOT router.push) so menu / sidebar / /me all re-fetch
      // under the new support identity on a clean page-load. Dynamic routes are
      // registered once per load from the menu and aren't rebuilt on identity
      // change, so a client-side push would keep the stale ops routes. Landing
      // on '/' lets the fresh load redirect to the support identity's home.
      window.location.assign('/')
    } else {
      toast.error(res.data.msg || t('platform.tenant.support.message.startFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.tenant.support.message.startFailed'))
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="space-y-3">
    <!-- Search + create -->
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('platform.tenant.search.placeholder')" class="w-60" />
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="() => { page = 1; fetchData() }">
          <Search class="size-4" /> {{ t('common.button.search') }}
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                @click="resetSearch">
          <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
        </button>
        <div class="ml-auto">
          <button v-permission="'platform:tenant:create'"
                  class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="openCreate">
            <Plus class="size-4" /> {{ t('platform.tenant.button.new') }}
          </button>
        </div>
      </div>
    </Card>

    <!-- Recycle-bin hint: explain the two-step delete model BEFORE
         someone hunts for a missing trash icon on an active row. -->
    <Card class="p-3 bg-amber-500/5 border-amber-500/30">
      <p class="text-xs text-muted-foreground leading-relaxed">
        <span class="font-medium text-foreground">{{ t('platform.tenant.recycleBinHint.title') }}</span>
        {{ t('platform.tenant.recycleBinHint.body') }}
      </p>
    </Card>

    <!-- Table -->
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
        <template #cell-tenantCode="{ row }">
          <span class="font-mono text-sm">{{ row.tenantCode }}</span>
          <Badge v-if="isBuiltIn(row)" variant="outline" class="ml-2 text-[10px]">
            {{ t('common.status.builtIn') }}
          </Badge>
        </template>
        <template #cell-contactEmail="{ row }">
          <span class="text-sm">{{ row.contactEmail || '—' }}</span>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'default' : 'outline'">
            {{ row.status === 1 ? t('platform.tenant.status.active') : t('platform.tenant.status.suspended') }}
          </Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-0.5">
            <!-- Edit (displayName + contactEmail) -->
            <button v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.tooltip.edit')"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>

            <!-- Support session — only for active, non-built-in tenants -->
            <button v-permission="'platform:tenant:impersonate'"
                    class="h-7 px-2 rounded hover:bg-amber-500/10 text-amber-600 text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row) || row.status !== 1"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : (row.status !== 1
                            ? t('platform.tenant.support.tooltip.disabledSuspended')
                            : t('platform.tenant.support.tooltip.start'))"
                    @click="openSupportSession(row)">
              <LifeBuoy class="size-3.5" />
            </button>

            <!-- Suspend / Resume toggle — same column slot, behavior swaps on row.status -->
            <button v-if="row.status === 1"
                    v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.tooltip.suspend')"
                    @click="handleSuspend(row)">
              <Pause class="size-3.5" />
            </button>
            <button v-else
                    v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-emerald-500/10 text-emerald-600 text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.tooltip.resume')"
                    @click="handleResume(row)">
              <Play class="size-3.5" />
            </button>

            <!-- Hard delete — recycle-bin model: only suspended rows
                 expose this button. Active rows must Suspend first.
                 Modal then requires typing the tenantCode exactly. -->
            <button v-if="row.status !== 1"
                    v-permission="'platform:tenant:delete'"
                    class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.hardDelete.tooltip.confirm')"
                    @click="openHardDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <TenantCreate v-model:open="showCreate" @saved="fetchData" />
    <TenantEdit :row="editTarget" @close="editTarget = null" @saved="() => { editTarget = null; fetchData() }" />
    <TenantSupportSession :row="supportTarget"
                          @close="supportTarget = null"
                          @start="handleSupportSession" />
    <TenantHardDelete :row="hardDeleteTarget"
                      @close="hardDeleteTarget = null"
                      @deleted="() => { hardDeleteTarget = null; fetchData() }" />
  </div>
</template>
