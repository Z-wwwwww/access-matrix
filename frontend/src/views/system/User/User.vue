<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import Select from '@/components/ui/Select.vue'
import DeptPicker from '@/components/shared/DeptPicker.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Search, RotateCcw, Pencil, Trash2, Pause, Play, KeyRound, LogOut, Copy } from 'lucide-vue-next'

const { t } = useI18n()
const { confirm } = useConfirm()
import {
  getUserListApi, deleteUserApi, changeUserStatusApi, forceLogoutApi, resetUserPasswordApi
} from '@/services/user'
import { getDeptTreeApi } from '@/services/dept'
import { getRoleListApi } from '@/services/role'
import UserEdit from './UserEdit.vue'
import { useAuthStore } from '@/stores/auth'

// Password reset mirrors the platform-user console: the backend rotates a
// generated temporary password (written to Keycloak with forced change on
// next login; legacy password mode falls back to the local hash), kicks the
// user's sessions, best-effort emails the credentials, and returns the temp
// password ONCE — shown in the drawer below. The admin never types a
// password. Self-service password changes live solely in the AppHeader user
// menu (KC account console / break-glass for super-admins).

// "Protected admin" = the built-in admin OR the tenant's singular SUPER_ADMIN.
// Such rows are fully read-only in this console — no edit, suspend, delete,
// reset or force-logout, whoever the caller is. The admin manages their own
// info on the Profile page. The backend enforces the same; here we hide the
// action buttons entirely.
function isProtectedAdmin(row) {
  return row?.builtin === true || row?.superAdmin === true
}

// You cannot manage your own account from the admin console — edit your own
// info on the Profile page instead. Blocking self here prevents self
// privilege-escalation (granting yourself roles) and self-lockout (disabling /
// deleting / kicking yourself). The backend enforces the same on every path.
const authStore = useAuthStore()
function isSelf(row) {
  // NOTE: authStore.userId is the JWT `sub`, which in OIDC mode is the
  // Keycloak UUID — NOT the business ULID — so it never equals row.id.
  // The business id comes from /me (userInfo.userId === core_auth_user.id),
  // which matches the list row's id. Fall back to the login username (always
  // present in the JWT) for the brief window before /me is cached.
  const meId = authStore.userInfo?.userId
  if (meId) return row?.id === meId
  return !!row?.username && row.username === authStore.username
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '', deptId: '', roleId: '' })

// 角色筛选の選択肢。ロールは租户内せいぜい数十件想定なので 1 ページで取り切る
// (UserEdit のロール割当と同じ取り方)。失敗しても検索自体は使える(非致命)。
const roleOptions = ref([])

async function loadRoleOptions() {
  try {
    const res = await getRoleListApi({ page: 1, size: 100 })
    if (res.data.code === 0) {
      roleOptions.value = (res.data.data.records || []).map(r => ({ label: r.name, value: r.id }))
    }
  } catch { /* 非致命:下拉为空即可 */ }
}

const showEdit = ref(false)
const current = ref(null)

// One-time temp-password drawer (same UX as the platform-user console).
const secret = ref(null)   // { username, tempPassword, emailSent, email }

// 部署 id → 名称マップ（一覧列の表示用）。/dept/tree はログイン後キャッシュされる前提で
// 画面ロード時に 1 回だけ取得し、行ごとの解決はこのマップで O(1) で済ませる。
const deptNameMap = ref(new Map())

async function loadDeptMap() {
  try {
    const res = await getDeptTreeApi()
    if (res.data.code !== 0) return
    const map = new Map()
    function walk(nodes) {
      for (const n of nodes) {
        map.set(n.id, n.name)
        if (n.children?.length) walk(n.children)
      }
    }
    walk(res.data.data || [])
    deptNameMap.value = map
  } catch { /* 非致命：解決失敗時は '-' を表示 */ }
}

function deptName(id) {
  if (id == null || id === '') return '-'
  return deptNameMap.value.get(id) || '-'
}

// Use computed so column titles re-evaluate when the locale changes at runtime.
const columns = computed(() => [
  { key: 'username', title: t('user.column.username'), minWidth: '140px' },
  { key: 'displayName', title: t('user.column.displayName'), minWidth: '120px' },
  { key: 'userNo', title: t('user.column.userNo'), minWidth: '100px' },
  { key: 'email', title: t('user.column.email'), minWidth: '160px' },
  { key: 'deptId', title: t('user.column.deptId'), minWidth: '140px' },
  { key: 'status', title: t('user.column.status'), minWidth: '80px', align: 'center' },
  { key: 'actions', title: t('user.column.actions'), minWidth: '220px', align: 'center', sticky: 'right' }
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getUserListApi({ page: page.value, size: pageSize.value, ...search })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.deptId = ''
  search.roleId = ''
  page.value = 1
  fetchData()
}

function openCreate() {
  current.value = null
  showEdit.value = true
}

function openEdit(row) {
  current.value = row
  showEdit.value = true
}

async function handleResetPwd(row) {
  const ok = await confirm({
    title: t('user.confirm.resetTitle'),
    message: t('user.confirm.resetMessage', { name: row.username }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await resetUserPasswordApi(row.id)
    if (res.data.code === 0) {
      secret.value = { ...res.data.data, email: row.email }
      toast.success(t('user.resetPassword.message.success'))
    } else {
      toast.error(res.data.msg || t('user.resetPassword.message.failed'))
    }
  } catch (e) { toast.error(e.message) }
}

async function copySecret() {
  try {
    await navigator.clipboard.writeText(secret.value.tempPassword)
    toast.success(t('user.resetPassword.message.copied'))
  } catch {
    toast.error(t('user.resetPassword.message.copyFailed'))
  }
}

async function handleDelete(row) {
  const ok = await confirm({
    title: t('user.confirm.deleteTitle'),
    message: t('user.confirm.deleteMessage', { name: row.username }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await deleteUserApi(row.id)
    if (res.data.code === 0) { toast.success(t('common.message.deleteSuccessful')); fetchData() }
    else toast.error(res.data.msg || t('user.message.deleteFailed'))
  } catch (e) { toast.error(e.message) }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    const res = await changeUserStatusApi(row.id, next)
    if (res.data.code === 0) {
      toast.success(t('common.message.operationSuccessful'))
      fetchData()
    } else {
      // request.js only REJECTS on code 401 / 700 — every other business code
      // resolves, so without this branch a NOT_FOUND (404, the row was deleted
      // in another tab) or an OPTIMISTIC_LOCK_CONFLICT (702, from
      // ConcurrentEdit.requireApplied in UserAdminService.changeStatus) produced
      // total silence: no toast, and no fetchData(), so the row kept showing the
      // old status. Every sibling handler in this file already has this branch.
      toast.error(res.data.msg || t('common.message.operationFailed'))
    }
  } catch (e) { toast.error(e.message) }
}

async function handleForceLogout(row) {
  const ok = await confirm({
    title: t('user.confirm.forceLogoutTitle'),
    message: t('user.confirm.forceLogoutMessage', { name: row.username }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await forceLogoutApi(row.id)
    if (res.data.code === 0) toast.success(t('user.message.forceLogoutSuccess'))
    else toast.error(res.data.msg || t('user.resetPassword.message.failed'))
  } catch (e) { toast.error(e.message) }
}

onMounted(() => {
  fetchData()
  loadDeptMap()
  loadRoleOptions()
})
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('user.search.placeholder.keyword')" class="w-60" />
        </div>
        <div class="w-60">
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.search.label.deptId') }}</label>
          <DeptPicker v-model="search.deptId" :placeholder="t('common.placeholder.deptId')" />
        </div>
        <div class="w-60">
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.search.label.roleId') }}</label>
          <Select v-model="search.roleId" :options="roleOptions" />
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
          <button v-permission="'user:create'"
                  class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="openCreate">
            <Plus class="size-4" /> {{ t('common.button.new') }}
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
          <span>{{ row.username }}</span>
          <Badge v-if="isSelf(row)" variant="secondary" class="ml-2 text-[10px]">{{ t('user.badge.self') }}</Badge>
          <Badge v-if="row.builtin === true" variant="outline" class="ml-2 text-[10px]">{{ t('common.status.builtIn') }}</Badge>
          <Badge v-else-if="row.superAdmin === true" variant="outline" class="ml-2 text-[10px]">{{ t('user.badge.tenantAdmin') }}</Badge>
        </template>
        <template #cell-deptId="{ row }">
          <span>{{ deptName(row.deptId) }}</span>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? t('common.status.enabled') : t('common.status.disabled') }}
          </Badge>
        </template>
        <template #cell-actions="{ row }">
          <!-- Protected admin rows (built-in / tenant SUPER_ADMIN) get NO action
               buttons at all — nothing here is ever allowed on them, and the
               backend refuses every mutation anyway. -->
          <!-- Icons / colors / order mirror the platform-user console
               (PlatformUser.vue): edit → status → reset → logout → delete. -->
          <div v-if="!isProtectedAdmin(row)" class="inline-flex items-center gap-1">
            <button v-permission="'user:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                    :disabled="isSelf(row)"
                    :title="isSelf(row) ? t('user.tooltip.selfManaged') : t('user.tooltip.edit')"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button v-if="row.status === 1" v-permission="'user:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                    :disabled="isSelf(row)"
                    :title="isSelf(row) ? t('user.tooltip.selfManaged') : t('user.tooltip.disable')"
                    @click="toggleStatus(row)">
              <Pause class="size-3.5" />
            </button>
            <button v-else v-permission="'user:update'"
                    class="h-7 px-2 rounded hover:bg-emerald-500/10 text-emerald-600 text-xs inline-flex items-center disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                    :disabled="isSelf(row)"
                    :title="isSelf(row) ? t('user.tooltip.selfManaged') : t('user.tooltip.enable')"
                    @click="toggleStatus(row)">
              <Play class="size-3.5" />
            </button>
            <button v-permission="'auth:reset-password'"
                    class="h-7 px-2 rounded hover:bg-primary/10 text-primary text-xs inline-flex items-center disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                    :disabled="isSelf(row)"
                    :title="isSelf(row) ? t('user.tooltip.selfManaged') : t('user.tooltip.resetPassword')"
                    @click="handleResetPwd(row)">
              <KeyRound class="size-3.5" />
            </button>
            <button v-permission="'*:*'"
                    class="h-7 px-2 rounded hover:bg-amber-500/10 text-amber-600 text-xs inline-flex items-center disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                    :disabled="isSelf(row)"
                    :title="isSelf(row) ? t('user.tooltip.selfManaged') : t('user.tooltip.forceLogout')"
                    @click="handleForceLogout(row)">
              <LogOut class="size-3.5" />
            </button>
            <button v-permission="'user:delete'"
                    class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isSelf(row)"
                    :title="isSelf(row) ? t('user.tooltip.selfManaged') : t('common.button.delete')"
                    @click="handleDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <UserEdit v-model:open="showEdit" :user="current" @saved="fetchData" />

    <!-- One-time temp-password drawer (reset result; mirrors the platform-user console) -->
    <Drawer :open="!!secret" :title="t('user.resetPassword.title')" width="max-w-md" @close="secret = null">
      <div v-if="secret" class="space-y-3 text-sm">
        <p class="text-muted-foreground">{{ t('user.resetPassword.intro', { username: secret.username }) }}</p>
        <p v-if="secret.emailSent === true" class="text-emerald-600">{{ t('user.resetPassword.emailSent', { email: secret.email }) }}</p>
        <p v-else class="text-amber-600">{{ t('user.resetPassword.emailNotSent') }}</p>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.resetPassword.tempPassword') }}</label>
          <div class="flex items-center gap-2">
            <code class="flex-1 px-3 py-2 rounded bg-muted/60 font-mono text-sm break-all">{{ secret.tempPassword }}</code>
            <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1 shrink-0"
                    @click="copySecret">
              <Copy class="size-4" /> {{ t('user.resetPassword.copy') }}
            </button>
          </div>
        </div>
        <p class="text-xs text-amber-600">{{ t('user.resetPassword.hint') }}</p>
      </div>
      <template #footer>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="secret = null">
          {{ t('common.button.close') }}
        </button>
      </template>
    </Drawer>
  </div>
</template>
