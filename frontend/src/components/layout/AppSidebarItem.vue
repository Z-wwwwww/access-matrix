<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChevronDown, ChevronRight, Star } from 'lucide-vue-next'
import LucideIcon from '@/components/shared/LucideIcon.vue'
import { useMenuTitle } from '@/composables/useMenuTitle'
import { useFavoriteMenus } from '@/composables/useFavoriteMenus'
import { getMenuItemColor } from '@/lib/menu-color'

const { t } = useI18n()
const { translate: translateMenu } = useMenuTitle()
const { isFavorite, toggleFavorite } = useFavoriteMenus()

defineOptions({ name: 'AppSidebarItem' })

const props = defineProps({
  item: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  collapsed: { type: Boolean, default: false },
  expandedKeys: { type: Set, required: true }
})

const emit = defineEmits(['toggle'])

const route = useRoute()
const router = useRouter()

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function hasChildren(item) {
  return item.children && item.children.length > 0
}

// 收藏开关只在叶子菜单 + 非折叠状态 + 有 id 时显示
const canFavorite = computed(
  () => props.item.id != null && !hasChildren(props.item) && !props.collapsed
)

function onToggleFavorite() {
  if (props.item.id != null) toggleFavorite(props.item.id)
}

// 後端 target: '_blank' / 2 → 外部リンク，'_self' / 0 → 通常路由
function isExternalLink(item) {
  const t = item.target
  return t === '_blank' || t === 2 || t === '2'
}

// 一級メニューのアイコン色：sort から導出。active 時は親の text-white を維持するため適用しない。
const iconStyle = computed(() => {
  if (props.depth !== 0) return null
  if (isActive(props.item.path) && !hasChildren(props.item)) return null
  return { color: getMenuItemColor(props.item.sort) }
})

// ── Collapsed-mode flyout：折り畳み時に一級メニューへホバーで子メニュー or タイトルをポップアップ表示 ──
const buttonRef = ref(null)
const flyoutOpen = ref(false)
const flyoutPos = ref({ top: 0, left: 0 })
let closeTimer = null

const visibleChildren = computed(() =>
  (props.item.children || []).filter((c) => !c.hide)
)

function showFlyout() {
  if (!props.collapsed || props.depth !== 0) return
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
  const rect = buttonRef.value?.getBoundingClientRect()
  if (!rect) return
  flyoutPos.value = { top: rect.top, left: rect.right + 4 }
  flyoutOpen.value = true
}

function scheduleHideFlyout() {
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    flyoutOpen.value = false
    closeTimer = null
  }, 120)
}

function cancelHideFlyout() {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
}

function navigateFromFlyout(child) {
  if (isExternalLink(child)) {
    window.open(child.path, '_blank', 'noopener,noreferrer')
  } else {
    router.push(child.path)
  }
  flyoutOpen.value = false
}

// flyout 顶部の一级メニュー：外部リンクの場合のみクリック可能（新タブで開く）
function onFlyoutParentClick() {
  if (!isExternalLink(props.item)) return
  window.open(props.item.path, '_blank', 'noopener,noreferrer')
  flyoutOpen.value = false
}

onBeforeUnmount(() => {
  if (closeTimer) clearTimeout(closeTimer)
})

function onClick() {
  if (hasChildren(props.item)) {
    emit('toggle', props.item.path)
  } else if (isExternalLink(props.item)) {
    window.open(props.item.path, '_blank', 'noopener,noreferrer')
  } else {
    router.push(props.item.path)
  }
}
</script>

<template>
  <div>
    <button
      ref="buttonRef"
      class="group w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer"
      :class="[
        isActive(item.path) && !hasChildren(item)
          ? 'bg-brand-orange text-white shadow-sm'
          : depth === 0
            ? 'text-foreground hover:bg-muted hover:text-foreground'
            : 'text-muted-foreground hover:bg-muted hover:text-foreground'
      ]"
      @click="onClick"
      @mouseenter="showFlyout"
      @mouseleave="scheduleHideFlyout"
    >
      <LucideIcon v-if="item.icon" :name="item.icon" :size="16" class="shrink-0" :style="iconStyle" />
      <span class="flex-1 truncate text-left" :class="collapsed && depth === 0 ? 'sr-only' : ''">
        {{ translateMenu(item) }}
      </span>
      <template v-if="hasChildren(item) && !collapsed">
        <ChevronDown v-if="expandedKeys.has(item.path)" :size="16" class="opacity-50 shrink-0" />
        <ChevronRight v-else :size="16" class="opacity-50 shrink-0" />
      </template>
      <!-- 收藏开关：未收藏时 hover 显示淡淡轮廓，鼠标落到星上才变清晰；已收藏则常驻金色 -->
      <span
        v-if="canFavorite"
        role="button"
        :aria-label="isFavorite(item.id) ? t('layout.sidebar.unfavorite') : t('layout.sidebar.favorite')"
        :title="isFavorite(item.id) ? t('layout.sidebar.unfavorite') : t('layout.sidebar.favorite')"
        class="shrink-0 -mr-1 p-0.5 rounded inline-flex transition-opacity hover:bg-foreground/10"
        :class="
          isFavorite(item.id)
            ? 'opacity-100'
            : 'opacity-0 group-hover:opacity-20 hover:!opacity-100'
        "
        @click.stop="onToggleFavorite"
      >
        <Star
          :size="14"
          :class="isFavorite(item.id) ? 'fill-amber-400 text-amber-400' : 'text-current'"
        />
      </span>
    </button>

    <div
      v-if="hasChildren(item) && expandedKeys.has(item.path) && !collapsed"
      class="ml-3 mt-0.5 space-y-0.5"
    >
      <template v-for="child in item.children" :key="child.path">
        <AppSidebarItem
          v-if="!child.hide"
          :item="child"
          :depth="depth + 1"
          :collapsed="collapsed"
          :expanded-keys="expandedKeys"
          @toggle="(p) => emit('toggle', p)"
        />
      </template>
    </div>

    <!-- Collapsed-mode flyout (depth=0 only) -->
    <Teleport v-if="collapsed && depth === 0" to="body">
      <Transition
        enter-active-class="transition-opacity duration-100"
        leave-active-class="transition-opacity duration-100"
        enter-from-class="opacity-0"
        leave-to-class="opacity-0"
      >
        <div
          v-if="flyoutOpen"
          class="fixed z-50 min-w-[200px] max-w-[260px] rounded-lg border border-border bg-card shadow-xl py-1 px-1"
          :style="{ top: flyoutPos.top + 'px', left: flyoutPos.left + 'px' }"
          @mouseenter="cancelHideFlyout"
          @mouseleave="scheduleHideFlyout"
        >
          <!-- Parent title (外部リンク時のみクリック可能) -->
          <div
            class="flex items-center gap-2 px-2 py-1.5 text-sm font-medium text-foreground transition-colors"
            :class="[
              visibleChildren.length && 'border-b border-border mb-1',
              isExternalLink(item) ? 'cursor-pointer rounded hover:bg-muted' : 'cursor-default',
            ]"
            @click="onFlyoutParentClick"
          >
            <LucideIcon
              v-if="item.icon"
              :name="item.icon"
              :size="14"
              class="shrink-0"
              :style="iconStyle"
            />
            <span class="truncate">{{ translateMenu(item) }}</span>
          </div>
          <!-- Children list -->
          <button
            v-for="child in visibleChildren"
            :key="child.path"
            class="w-full flex items-center gap-2 px-2 py-1.5 rounded text-sm text-left transition-colors cursor-pointer"
            :class="
              isActive(child.path)
                ? 'bg-brand-orange/10 text-brand-orange font-medium'
                : 'text-foreground hover:bg-muted'
            "
            @click="navigateFromFlyout(child)"
          >
            <LucideIcon
              v-if="child.icon"
              :name="child.icon"
              :size="14"
              class="shrink-0 opacity-70"
            />
            <span class="truncate">{{ translateMenu(child) }}</span>
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
