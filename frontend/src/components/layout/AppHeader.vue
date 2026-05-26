<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { Menu, LogOut, User, Sun, Moon, Palette, Languages, ChevronDown, Check, KeyRound } from 'lucide-vue-next'
import ChangePasswordDialog from './ChangePasswordDialog.vue'

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

function openChangePassword() {
  userOpen.value = false
  passwordDialogOpen.value = true
}

async function handleLogout() {
  userOpen.value = false
  // OIDC mode: logout() navigates to Keycloak's end_session_endpoint and
  // returns true — we MUST NOT router.push afterwards, the page is
  // already on its way out. Password mode: returns false, we handle the
  // /login navigation here.
  const navigatedAway = await authStore.logout()
  if (!navigatedAway) router.push('/login')
}

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', closeLangMenu, true)
  document.removeEventListener('mousedown', closePaletteMenu, true)
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
      <img src="@/assets/logo.svg" alt="Access Matrix" class="h-8 hidden sm:block" />
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
              {{ authStore.userInfo?.realname || t('layout.header.userFallback') }}
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
  </header>
</template>
