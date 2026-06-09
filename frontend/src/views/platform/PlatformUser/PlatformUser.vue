<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { toJSTDateTimeDisp } from '@/lib/date'
import { isValidEmail } from '@/lib/validators'
import { Plus, Search, RotateCcw, Copy, ShieldCheck, Pause, Play, KeyRound, Trash2, Pencil, Mail, LogOut } from 'lucide-vue-next'
import {
  listPlatformUsersApi, createPlatformUserApi, updatePlatformUserApi,
  disablePlatformUserApi, enablePlatformUserApi, resetPlatformUserPwApi,
  resendPlatformUserInviteApi, forcePlatformUserLogoutApi, deletePlatformUserApi
} from '@/services/platformUser'

const { t } = useI18n()
const { confirm } = useConfirm()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '' })

const showCreate = ref(false)
const saving = ref(false)
const form = reactive({ username: '', email: '', displayName: '' })

const showEdit = ref(false)
const editForm = reactive({ id: '', username: '', email: '', displayName: '' })

// Shared "one-time secret" drawer for create + reset-password.
const secret = ref(null)   // { username, tempPassword, title }

const columns = [
  { key: 'username',      label: () => t('platform.user.column.username'),    width: '170px' },
  { key: 'displayName',   label: () => t('platform.user.column.displayName') },
  { key: 'email',         label: () => t('platform.user.column.email'),       width: '220px' },
  { key: 'platformAdmin', label: () => t('platform.user.column.role'),        width: '120px' },
  { key: 'status',        label: () => t('platform.user.column.status'),      width: '90px' },
  { key: 'createTime',    label: () => t('platform.user.column.createTime'),  width: '170px' },
  { key: 'actions',       label: () => t('platform.user.column.actions'),     width: '150px' }
]

function fmtDate(s) {
  return s ? toJSTDateTimeDisp(s) : '—'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listPlatformUsersApi({
      page: page.value, size: pageSize.value, keyword: search.keyword || undefined
    })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    } else {
      toast.error(res.data.msg || t('platform.user.message.loadFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

function applySearch() {
  page.value = 1
  fetchData()
}
function resetSearch() {
  search.keyword = ''
  applySearch()
}

function openCreate() {
  form.username = ''
  form.email = ''
  form.displayName = ''
  showCreate.value = true
}

async function submitCreate() {
  if (!form.username || !form.displayName || !form.email) {
    toast.error(t('platform.user.message.required'))
    return
  }
  if (!isValidEmail(form.email)) {
    toast.error(t('common.message.invalidEmail'))
    return
  }
  saving.value = true
  try {
    const res = await createPlatformUserApi({
      username: form.username,
      email: form.email || undefined,
      displayName: form.displayName
    })
    if (res.data.code === 0) {
      showCreate.value = false
      // Plan B: no temp password popup — the user sets their own via the invite link.
      if (res.data.data?.emailSent) {
        toast.success(t('platform.user.message.createdInviteSent', { email: form.email }))
      } else {
        toast.error(t('platform.user.message.createdInviteFailed'))
      }
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.user.message.createFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.user.message.createFailed'))
  } finally {
    saving.value = false
  }
}

function openEdit(row) {
  editForm.id = row.id
  editForm.username = row.username
  editForm.email = row.email || ''
  editForm.displayName = row.displayName || ''
  showEdit.value = true
}

async function submitEdit() {
  if (!editForm.displayName || !editForm.email) {
    toast.error(t('platform.user.message.required'))
    return
  }
  if (!isValidEmail(editForm.email)) {
    toast.error(t('common.message.invalidEmail'))
    return
  }
  saving.value = true
  try {
    const res = await updatePlatformUserApi(editForm.id, {
      email: editForm.email,
      displayName: editForm.displayName
    })
    if (res.data.code === 0) {
      showEdit.value = false
      toast.success(t('platform.user.message.updateSuccess'))
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.user.message.opFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.user.message.opFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDisable(row) {
  const ok = await confirm({
    title: t('platform.user.confirm.disableTitle'),
    message: t('platform.user.confirm.disableMessage', { username: row.username }),
    confirmText: t('platform.user.action.disable')
  })
  if (!ok) return
  await runOp(() => disablePlatformUserApi(row.id), 'disableSuccess')
}

async function handleEnable(row) {
  await runOp(() => enablePlatformUserApi(row.id), 'enableSuccess')
}

async function handleReset(row) {
  const ok = await confirm({
    title: t('platform.user.confirm.resetTitle'),
    message: t('platform.user.confirm.resetMessage', { username: row.username }),
    variant: 'destructive',
    confirmText: t('platform.user.action.reset')
  })
  if (!ok) return
  try {
    const res = await resetPlatformUserPwApi(row.id)
    if (res.data.code === 0) {
      secret.value = { ...res.data.data, email: row.email, kind: 'reset', title: t('platform.user.secret.titleReset') }
      toast.success(t('platform.user.message.resetSuccess'))
    } else {
      toast.error(res.data.msg || t('platform.user.message.opFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.user.message.opFailed'))
  }
}

async function handleResend(row) {
  const ok = await confirm({
    title: t('platform.user.confirm.resendTitle'),
    message: t('platform.user.confirm.resendMessage', { username: row.username, email: row.email }),
    confirmText: t('platform.user.action.resend')
  })
  if (!ok) return
  try {
    const res = await resendPlatformUserInviteApi(row.id)
    if (res.data.code === 0) {
      // Resend just (re)sends the email to the user — no temp-password popup here
      // (that's the "reset password" action's job). Password is still rotated server-side.
      toast.success(t('platform.user.message.resendSuccess'))
    } else {
      toast.error(res.data.msg || t('platform.user.message.opFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.user.message.opFailed'))
  }
}

async function handleForceLogout(row) {
  const ok = await confirm({
    title: t('platform.user.confirm.forceLogoutTitle'),
    message: t('platform.user.confirm.forceLogoutMessage', { username: row.username }),
    variant: 'destructive',
    confirmText: t('platform.user.action.forceLogout')
  })
  if (!ok) return
  await runOp(() => forcePlatformUserLogoutApi(row.id), 'forceLogoutSuccess')
}

async function handleDelete(row) {
  const ok = await confirm({
    title: t('platform.user.confirm.deleteTitle'),
    message: t('platform.user.confirm.deleteMessage', { username: row.username }),
    confirmText: t('platform.user.action.delete')
  })
  if (!ok) return
  await runOp(() => deletePlatformUserApi(row.id), 'deleteSuccess')
}

async function runOp(apiCall, successKey) {
  try {
    const res = await apiCall()
    if (res.data.code === 0) {
      toast.success(t('platform.user.message.' + successKey))
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.user.message.opFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.user.message.opFailed'))
  }
}

async function copySecret() {
  try {
    await navigator.clipboard.writeText(secret.value.tempPassword)
    toast.success(t('platform.user.message.copied'))
  } catch {
    toast.error(t('platform.user.message.copyFailed'))
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <!-- Search + create -->
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('platform.user.search.placeholder')" class="w-60" />
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="applySearch">
          <Search class="size-4" /> {{ t('common.button.search') }}
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                @click="resetSearch">
          <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
        </button>
        <div class="ml-auto">
          <button v-permission="'opsuser:create'"
                  class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="openCreate">
            <Plus class="size-4" /> {{ t('platform.user.button.new') }}
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
        <template #cell-username="{ row }">
          <span class="font-mono text-sm">{{ row.username }}</span>
        </template>
        <template #cell-email="{ row }">
          <span class="text-sm">{{ row.email || '—' }}</span>
        </template>
        <template #cell-platformAdmin="{ row }">
          <Badge v-if="row.platformAdmin" variant="outline" class="text-[10px] border-primary/40 text-primary">
            <ShieldCheck class="size-3 inline -mt-0.5 mr-0.5" />{{ t('platform.user.role.admin') }}
          </Badge>
          <span v-else class="text-xs text-muted-foreground">{{ t('platform.user.role.operator') }}</span>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? t('common.status.enabled') : t('common.status.disabled') }}
          </Badge>
        </template>
        <template #cell-createTime="{ row }">
          <span class="text-sm">{{ fmtDate(row.createTime) }}</span>
        </template>
        <template #cell-actions="{ row }">
          <!-- the super 'ops' (platformAdmin) is unmanageable from here -->
          <span v-if="row.platformAdmin" class="text-xs text-muted-foreground">—</span>
          <div v-else class="inline-flex items-center gap-0.5">
            <button v-permission="'opsuser:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center"
                    :title="t('platform.user.action.edit')" @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button v-if="row.status === 1" v-permission="'opsuser:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center"
                    :title="t('platform.user.action.disable')" @click="handleDisable(row)">
              <Pause class="size-3.5" />
            </button>
            <button v-else v-permission="'opsuser:update'"
                    class="h-7 px-2 rounded hover:bg-emerald-500/10 text-emerald-600 text-xs inline-flex items-center"
                    :title="t('platform.user.action.enable')" @click="handleEnable(row)">
              <Play class="size-3.5" />
            </button>
            <button v-permission="'opsuser:update'"
                    class="h-7 px-2 rounded hover:bg-primary/10 text-primary text-xs inline-flex items-center"
                    :title="t('platform.user.action.reset')" @click="handleReset(row)">
              <KeyRound class="size-3.5" />
            </button>
            <button v-permission="'opsuser:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center"
                    :title="t('platform.user.action.resend')" @click="handleResend(row)">
              <Mail class="size-3.5" />
            </button>
            <button v-permission="'opsuser:update'"
                    class="h-7 px-2 rounded hover:bg-amber-500/10 text-amber-600 text-xs inline-flex items-center"
                    :title="t('platform.user.action.forceLogout')" @click="handleForceLogout(row)">
              <LogOut class="size-3.5" />
            </button>
            <button v-permission="'opsuser:delete'"
                    class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center"
                    :title="t('platform.user.action.delete')" @click="handleDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <!-- Create drawer -->
    <Drawer v-model:open="showCreate" :title="t('platform.user.create.title')" width="max-w-md">
      <div class="space-y-3">
        <p class="text-xs text-muted-foreground leading-relaxed">{{ t('platform.user.create.intro') }}</p>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.column.username') }} <span class="text-destructive">*</span></label>
          <Input v-model="form.username" :placeholder="t('platform.user.create.usernamePlaceholder')" />
          <p class="text-[11px] text-muted-foreground mt-1">{{ t('platform.user.create.usernameHint') }}</p>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.column.displayName') }} <span class="text-destructive">*</span></label>
          <Input v-model="form.displayName" :placeholder="t('platform.user.create.displayNamePlaceholder')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.column.email') }} <span class="text-destructive">*</span></label>
          <Input v-model="form.email" :placeholder="t('platform.user.create.emailPlaceholder')" />
        </div>
      </div>
      <template #footer>
        <button class="h-9 px-3 rounded border border-border text-sm" @click="showCreate = false">
          {{ t('common.button.cancel') }}
        </button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1 disabled:opacity-50"
                :disabled="saving" @click="submitCreate">
          {{ saving ? t('platform.user.create.creating') : t('platform.user.button.new') }}
        </button>
      </template>
    </Drawer>

    <!-- Edit drawer (display name + email; username is immutable) -->
    <Drawer v-model:open="showEdit" :title="t('platform.user.edit.title')" width="max-w-md">
      <div class="space-y-3">
        <p class="text-xs text-muted-foreground leading-relaxed">{{ t('platform.user.edit.intro') }}</p>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.column.username') }}</label>
          <div class="px-3 py-2 rounded bg-muted/40 font-mono text-sm">{{ editForm.username }}</div>
          <p class="text-[11px] text-muted-foreground mt-1">{{ t('platform.user.edit.usernameReadonly') }}</p>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.column.displayName') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.displayName" :placeholder="t('platform.user.create.displayNamePlaceholder')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.column.email') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.email" :placeholder="t('platform.user.create.emailPlaceholder')" />
        </div>
      </div>
      <template #footer>
        <button class="h-9 px-3 rounded border border-border text-sm" @click="showEdit = false">
          {{ t('common.button.cancel') }}
        </button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1 disabled:opacity-50"
                :disabled="saving" @click="submitEdit">
          {{ saving ? t('platform.user.edit.saving') : t('platform.user.edit.save') }}
        </button>
      </template>
    </Drawer>

    <!-- One-time temp-password drawer (create + reset) -->
    <Drawer :open="!!secret" :title="secret ? secret.title : ''" width="max-w-md" @close="secret = null">
      <div v-if="secret" class="space-y-3 text-sm">
        <!-- create / reset / resend share this drawer; secret.kind picks the intro line. All carry emailSent. -->
        <p class="text-muted-foreground">{{ secret.kind === 'reset' ? t('platform.user.create.resetIntro', { username: secret.username }) : secret.kind === 'resend' ? t('platform.user.create.resendIntro', { username: secret.username }) : t('platform.user.create.doneIntro', { username: secret.username }) }}</p>
        <p v-if="secret.emailSent === true" class="text-emerald-600">{{ t('platform.user.create.emailSent', { email: secret.email }) }}</p>
        <p v-else-if="secret.emailSent === false" class="text-amber-600">{{ t('platform.user.create.emailNotSent') }}</p>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.user.create.tempPassword') }}</label>
          <div class="flex items-center gap-2">
            <code class="flex-1 px-3 py-2 rounded bg-muted/60 font-mono text-sm break-all">{{ secret.tempPassword }}</code>
            <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1 shrink-0"
                    @click="copySecret">
              <Copy class="size-4" /> {{ t('platform.user.create.copy') }}
            </button>
          </div>
        </div>
        <p class="text-xs text-amber-600">{{ t('platform.user.create.tempPasswordHint') }}</p>
      </div>
      <template #footer>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="secret = null">
          {{ t('common.button.close') }}
        </button>
      </template>
    </Drawer>
  </div>
</template>
