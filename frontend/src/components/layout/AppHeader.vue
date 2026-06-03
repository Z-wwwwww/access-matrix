<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useNotificationStream } from '@/composables/useNotificationStream'
import { useTheme } from '@/composables/useTheme'
import { Menu, LogOut, User, Sun, Moon, Palette, Languages, ChevronDown, Check, KeyRound, ShieldAlert, Bell, AlertCircle } from 'lucide-vue-next'
import ChangePasswordDialog from './ChangePasswordDialog.vue'
import BreakGlassPasswordDialog from './BreakGlassPasswordDialog.vue'
import { usePermission } from '@/composables/usePermission'
import { oidcConfig } from '@/utils/oidc'
import { toJST } from '@/lib/date'

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle-sidebar'])

const router = useRouter()
const authStore = useAuthStore()
const { theme, toggleTheme, palette, setPalette, palettes } = useTheme()
const { locale, t } = useI18n()

// ── Palette switcher ──
const paletteOpen = ref(false)
const palettePanelRef = ref(null)
const paletteTriggerRef = ref(null)

function togglePaletteMenu() {
  paletteOpen.value = !paletteOpen.value
  if (paletteOpen.value) {
    setTimeout(() => document.addEventListener('mousedown', closePaletteMenu, true), 0)
  }
}

function closePaletteMenu(e) {
  if (palettePanelRef.value?.contains(e?.target)) return
  if (paletteTriggerRef.value?.contains(e?.target)) return
  paletteOpen.value = false
  document.removeEventListener('mousedown', closePaletteMenu, true)
}

function pickPalette(value) {
  setPalette(value)
  paletteOpen.value = false
  document.removeEventListener('mousedown', closePaletteMenu, true)
}

// ── Notifications (站内通知 + 即时红点) ──
const notificationStore = useNotificationStore()
useNotificationStream()                 // open SSE; pushes update store.unread
const bellOpen = ref(false)
const bellPanelRef = ref(null)
const bellTriggerRef = ref(null)

// コンパクトな時刻表示(JST、MM/DD HH:mm)。relativeTime プラグインは未導入なので
// プロジェクト共通の lib/date に揃える。
function notifTime(v) {
  if (!v) return ''
  const d = toJST(v)
  return d.isValid() ? d.format('MM/DD HH:mm') : ''
}

function toggleBellMenu() {
  bellOpen.value = !bellOpen.value
  if (bellOpen.value) {
    notificationStore.fetchList()       // lazy-load recent items on open
    setTimeout(() => document.addEventListener('mousedown', closeBellMenu, true), 0)
  }
}

function closeBellMenu(e) {
  if (bellPanelRef.value?.contains(e?.target)) return
  if (bellTriggerRef.value?.contains(e?.target)) return
  bellOpen.value = false
  document.removeEventListener('mousedown', closeBellMenu, true)
}

function openNotification(item) {
  // info 型は開いた時点で既読。action 型は「実際に処理完了(resolve)」されるまで
  // 未読のまま=「待処理」バッジと赤点を維持する(開いただけでは消さない)。
  if (item.kind !== 1) notificationStore.markRead(item.id)
  bellOpen.value = false
  document.removeEventListener('mousedown', closeBellMenu, true)
  if (!item.link) return
  if (item.bizId) {
    // ドロワー型:対象 id は URL に出さず store 経由で渡し、遷移はクリーンな link のみ。
    // URL/keep-alive キャッシュキーが変わらないのでドロワー多重化を防げる。
    notificationStore.setPendingNav({ path: item.link, bizType: item.bizType, id: item.bizId })
    router.push(item.link)
  } else {
    // 普通のページ遷移(タブを開く)。
    router.push(item.link)
  }
}

// ── Language switcher ──
const langOpen = ref(false)
const langPanelRef = ref(null)
const langTriggerRef = ref(null)

const langOptions = [
  { value: 'ja_JP', label: '日本語' },
  { value: 'en', label: 'English' },
  { value: 'zh_CN', label: '简体中文' },
  { value: 'zh_TW', label: '繁體中文' },
  { value: 'ko_KR', label: '한국어' }
]

function switchLang(lang) {
  locale.value = lang
  localStorage.setItem('i18n-lang', lang)
  langOpen.value = false
}

function toggleLangMenu() {
  langOpen.value = !langOpen.value
  if (langOpen.value) {
    setTimeout(() => document.addEventListener('mousedown', closeLangMenu, true), 0)
  }
}

function closeLangMenu(e) {
  if (langPanelRef.value?.contains(e?.target)) return
  if (langTriggerRef.value?.contains(e?.target)) return
  langOpen.value = false
  document.removeEventListener('mousedown', closeLangMenu, true)
}

const currentLangLabel = () => langOptions.find(o => o.value === locale.value)?.label || locale.value

// ── User menu (hover) ──
const userOpen = ref(false)

function goProfile() {
  userOpen.value = false
  router.push('/profile')
}

const passwordDialogOpen = ref(false)
const breakGlassDialogOpen = ref(false)
const { hasPermission } = usePermission()
// Break-glass entry only relevant when the deployment uses SSO (otherwise
// the user IS using password mode and "break-glass" makes no sense) AND
// the caller is a super-admin (only super-admins have a break-glass hash).
const showBreakGlassEntry = computed(() => oidcConfig().enabled && hasPermission('*:*'))

function openChangePassword() {
  userOpen.value = false
  passwordDialogOpen.value = true
}

function openBreakGlass() {
  userOpen.value = false
  breakGlassDialogOpen.value = true
}

function handleLogout() {
  userOpen.value = false
  // Route to the SignOut transition page. It handles the entire logout
  // flow (backend revoke, KC probe, redirect or local routing) and
  // shows a spinner + status while the work is in flight, which is
  // crucial UX — the KC probe alone can take up to 3 s.
  router.push('/signout')
}

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', closeLangMenu, true)
  document.removeEventListener('mousedown', closePaletteMenu, true)
  document.removeEventListener('mousedown', closeBellMenu, true)
})
</script>

<template>
  <header class="h-14 sticky top-0 z-40 bg-card/95 backdrop-blur-xl border-b border-border shadow-sm flex items-center justify-between px-3 md:px-4 shrink-0">
    <!-- Left -->
    <div class="flex items-center gap-3">
      <button
        class="p-2 rounded-lg hover:bg-muted transition-colors"
        @click="emit('toggle-sidebar')"
      >
        <Menu :size="18" class="text-foreground" />
      </button>
      <!-- Brand lockup: gold matrix mark (image) + wordmark (text). The
           wordmark is real text so its color follows the theme via
           `text-foreground` — an inline SVG couldn't react to dark mode. -->
      <div class="hidden sm:flex items-center gap-2">
        <img src="@/assets/logo-mark.svg" alt="" class="h-5 w-5 shrink-0" />
        <span class="text-sm font-semibold tracking-tight text-foreground">Access Matrix</span>
      </div>
    </div>

    <!-- Right -->
    <div class="flex items-center gap-1">
      <!-- Theme toggle -->
      <button class="p-2 rounded-lg hover:bg-muted transition-colors" @click="toggleTheme">
        <Sun v-if="theme === 'dark'" :size="18" class="text-foreground" />
        <Moon v-else :size="18" class="text-foreground" />
      </button>

      <!-- Palette switcher -->
      <div class="relative">
        <button
          ref="paletteTriggerRef"
          class="p-2 rounded-lg hover:bg-muted transition-colors"
          :class="{ 'bg-muted': paletteOpen }"
          @click="togglePaletteMenu"
        >
          <Palette :size="18" class="text-foreground" />
        </button>
        <div
          v-if="paletteOpen"
          ref="palettePanelRef"
          class="absolute right-0 top-full mt-1 min-w-[180px] rounded-2xl border border-border bg-card shadow-xl z-50 overflow-hidden py-1"
        >
          <button
            v-for="opt in palettes"
            :key="opt.value"
            class="flex items-center gap-2 w-[calc(100%-0.5rem)] mx-1 px-3 py-2 text-sm transition-colors text-left rounded-lg"
            :class="opt.value === palette
              ? 'bg-primary/10 text-primary font-medium'
              : 'text-foreground hover:bg-muted'"
            @click="pickPalette(opt.value)"
          >
            <span class="inline-flex shrink-0 rounded-md ring-1 ring-border overflow-hidden h-5 w-10">
              <span class="h-full w-1/3" :style="{ backgroundColor: opt.swatch.bg }" />
              <span class="h-full w-1/3" :style="{ backgroundColor: opt.swatch.card }" />
              <span class="h-full w-1/3" :style="{ backgroundColor: opt.swatch.primary }" />
            </span>
            <span class="flex-1 truncate">{{ opt.label }}</span>
            <Check
              v-if="opt.value === palette"
              :size="14"
              class="shrink-0 text-primary"
            />
          </button>
        </div>
      </div>

      <!-- Language switcher -->
      <div class="relative">
        <button
          ref="langTriggerRef"
          class="flex items-center gap-1 px-2 py-1.5 rounded-lg hover:bg-muted transition-colors text-sm"
          @click="toggleLangMenu"
        >
          <Languages :size="16" class="text-foreground" />
          <span class="text-foreground hidden sm:inline">{{ currentLangLabel() }}</span>
        </button>
        <div
          v-if="langOpen"
          ref="langPanelRef"
          class="absolute right-0 top-full mt-1 min-w-[140px] rounded-2xl border border-border bg-card shadow-xl z-50 overflow-hidden py-1"
        >
          <button
            v-for="opt in langOptions"
            :key="opt.value"
            class="flex items-center justify-between w-[calc(100%-0.5rem)] mx-1 px-3 py-2 text-sm transition-colors text-left rounded-lg"
            :class="opt.value === locale
              ? 'bg-primary/10 text-primary font-medium'
              : 'text-foreground hover:bg-muted'"
            @click="switchLang(opt.value)"
          >
            <span class="truncate">{{ opt.label }}</span>
            <Check
              v-if="opt.value === locale"
              :size="14"
              class="shrink-0 ml-2 text-primary"
            />
          </button>
        </div>
      </div>

      <!-- Notifications (站内通知 + 即时红点) -->
      <div class="relative">
        <button
          ref="bellTriggerRef"
          class="relative p-2 rounded-lg hover:bg-muted transition-colors"
          :class="{ 'bg-muted': bellOpen }"
          :aria-label="t('layout.notification.title')"
          @click="toggleBellMenu"
        >
          <Bell :size="18" class="text-foreground" />
          <span
            v-if="notificationStore.unread > 0"
            class="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-signal-red text-white text-[10px] leading-4 font-medium text-center"
          >{{ notificationStore.unread > 99 ? '99+' : notificationStore.unread }}</span>
        </button>
        <div
          v-if="bellOpen"
          ref="bellPanelRef"
          class="absolute right-0 top-full mt-1 w-[360px] rounded-2xl border border-border bg-card shadow-xl z-50 overflow-hidden"
        >
          <div class="flex items-center justify-between px-4 h-11 border-b border-border">
            <span class="text-sm font-semibold text-foreground">{{ t('layout.notification.title') }}</span>
            <button
              v-if="notificationStore.unread > 0"
              class="text-xs text-muted-foreground hover:text-foreground transition-colors"
              @click="notificationStore.markAllRead()"
            >{{ t('layout.notification.markAllRead') }}</button>
          </div>
          <div class="max-h-[420px] overflow-y-auto scrollbar-thin p-1">
            <p
              v-if="!notificationStore.list.length"
              class="px-4 py-10 text-center text-sm text-muted-foreground"
            >{{ t('layout.notification.empty') }}</p>
            <!-- フラットな行リスト(GitHub/Linear 風)。未読は背景を薄く染め + 先頭ドット、
                 末尾に「待処理」を控えめに表示。カード枠は付けず雑然さを避ける。 -->
            <button
              v-for="item in notificationStore.list"
              :key="item.id"
              class="flex w-full gap-2.5 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-muted/60"
              :class="{ 'bg-primary/5': item.readFlag !== 1 }"
              @click="openNotification(item)"
            >
              <span class="mt-1 shrink-0 w-2 flex justify-center">
                <span v-if="item.readFlag !== 1" class="w-2 h-2 rounded-full bg-primary" />
              </span>
              <!-- 本文(タイトル/内容)。改行せず、右の meta 列に触れる手前で … 省略。 -->
              <span class="flex-1 min-w-0">
                <span
                  class="block truncate text-sm"
                  :class="item.readFlag === 1 ? 'text-muted-foreground' : 'font-medium text-foreground'"
                  :title="item.title"
                >{{ item.title }}</span>
                <span
                  v-if="item.content"
                  class="mt-0.5 block truncate text-xs text-muted-foreground"
                  :title="item.content"
                >{{ item.content }}</span>
              </span>
              <!-- meta 列:時刻が上、待処理アイコンは常にその真下(固定位置、折り返さない)。 -->
              <span class="shrink-0 flex flex-col items-end gap-1 pl-1">
                <span class="text-[11px] text-muted-foreground tabular-nums whitespace-nowrap">{{ notifTime(item.createTime) }}</span>
                <span
                  v-if="item.kind === 1 && item.readFlag !== 1"
                  class="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-medium whitespace-nowrap bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400"
                >
                  <AlertCircle :size="11" />
                  {{ t('layout.notification.actionRequired') }}
                </span>
              </span>
            </button>
          </div>
        </div>
      </div>

      <!-- User -->
      <div class="ml-2 pl-2 border-l border-border">
        <div
          class="relative"
          @mouseenter="userOpen = true"
          @mouseleave="userOpen = false"
        >
          <button
            type="button"
            class="flex items-center gap-2 w-full sm:min-w-[160px] px-2 py-1 rounded-lg hover:bg-muted transition-colors"
          >
            <div class="w-7 h-7 sm:w-8 sm:h-8 rounded-full bg-muted flex items-center justify-center shrink-0">
              <User :size="16" class="text-muted-foreground" />
            </div>
            <span class="flex-1 text-sm font-medium text-foreground hidden sm:block text-left truncate">
              {{ authStore.userInfo?.displayName || authStore.userInfo?.username || t('layout.header.userFallback') }}
            </span>
            <ChevronDown
              :size="14"
              class="text-muted-foreground transition-transform shrink-0"
              :class="{ 'rotate-180': userOpen }"
            />
          </button>
          <div
            v-if="userOpen"
            class="absolute left-0 right-0 top-full pt-1 z-50"
          >
            <div class="rounded-xl border border-border bg-card shadow-xl py-1">
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-foreground hover:bg-muted transition-colors text-left"
            @click="goProfile"
          >
            <User :size="16" class="text-muted-foreground" />
            {{ t('layout.header.profile') }}
          </button>
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-foreground hover:bg-muted transition-colors text-left"
            @click="openChangePassword"
          >
            <KeyRound :size="16" class="text-muted-foreground" />
            {{ t('layout.header.password') }}
          </button>
          <button
            v-if="showBreakGlassEntry"
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-foreground hover:bg-muted transition-colors text-left"
            @click="openBreakGlass"
          >
            <ShieldAlert :size="16" class="text-amber-600 dark:text-amber-400" />
            {{ t('layout.header.breakGlass') }}
          </button>
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-foreground hover:bg-muted transition-colors text-left"
            @click="handleLogout"
          >
            <LogOut :size="16" class="text-muted-foreground" />
            {{ t('layout.header.logout') }}
          </button>
          </div>
        </div>
        </div>
      </div>
    </div>

    <!-- パスワード変更ダイアログ -->
    <ChangePasswordDialog v-model:open="passwordDialogOpen" />
    <BreakGlassPasswordDialog v-if="showBreakGlassEntry" v-model:open="breakGlassDialogOpen" />
  </header>
</template>
