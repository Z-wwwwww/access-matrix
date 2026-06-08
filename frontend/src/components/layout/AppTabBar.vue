<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useTabsStore } from '@/stores/tabs'
// Business-specific quick-filter labels were removed with the PMS business code.
// Stub returns null so the tab title falls through to the menu-translated base.
function getQuickFilterLabelFromFullPath() { return null }
import { useMenuTitle } from '@/composables/useMenuTitle'
import { useI18n } from 'vue-i18n'
import { X, MoreHorizontal } from 'lucide-vue-next'
import { hasStickySubheader } from '@/composables/useStickySubheader'

const tabsStore = useTabsStore()
const { translate: translateMenu } = useMenuTitle()
const { t } = useI18n()

/**
 * tab 标题生成：
 *  - 詳細 / 新規 ページ（tab.path が `/detail` で終わる）は、
 *    親 path で一覧ページのタイトルを翻訳 → `common.button.detail|new` を追記
 *  - quickFilter 付きで遷移してきた一覧ページは、Dashboard カード名を後缀に追加
 *  - それ以外は `tab.path` で直接翻訳（fallback は backend title）
 */
function tabLabel(tab) {
  const path = tab.path || tab.key || ''
  if (typeof path === 'string' && path.endsWith('/detail')) {
    const parentPath = path.slice(0, -'/detail'.length)
    const base = translateMenu({ path: parentPath, title: stripSuffix(tab.title), titleI18n: tab.titleI18n })
    const hasId = tab.fullPath && tab.fullPath.includes('id=')
    // ページが setTabExtra で { prefixKey, badge } を渡せる:
    //   prefixKey … 詳細名の i18n キー（例 'reservation.detailTabTitle'）。ここで t() するので言語切替に追従。
    //               無ければ「菜单名 - 詳細/新規」
    //   badge     … 予約番号 / 施設名 など。あれば後ろに付与
    const ex = tabsStore.tabExtras[tab.fullPath] || tabsStore.tabExtras[tab.key]
    const exObj = ex && typeof ex === 'object' ? ex : ex ? { badge: ex } : null
    const defaultSuffix = hasId ? t('common.button.detail') : t('common.button.new')
    const prefix = exObj && exObj.prefixKey
      ? t(exObj.prefixKey)
      : `${base} - ${defaultSuffix}`
    const badge = exObj ? exObj.badge : ''
    if (hasId && badge) return `${prefix} - ${badge}`
    return prefix
  }
  const base = translateMenu({ path, title: tab.title, titleI18n: tab.titleI18n })
  // Dashboard カードからの quickFilter 付き遷移: カード名を後缀に表示
  const qfLabel = getQuickFilterLabelFromFullPath(tab.fullPath)
  return qfLabel ? `${base} - ${qfLabel}` : base
}

function stripSuffix(title) {
  if (typeof title !== 'string') return title
  return title.replace(/\s*[-－]\s*(詳細|新規|Detail|New|详情|新建|詳情|新增|상세|신규)$/, '').trim()
}

function isClosable(tab) {
  return tab.key !== tabsStore.homePath
}

// ── Drag & Drop（Pointer Events 自实现，弃用原生 HTML5 drag）──
// 原因：原生 drag 中浏览器接管光标 + 出 tab 栏时显示禁止标志（⊘），UX 差。
// pointer events 全程可控：5px 阈值区分点击/拖动，setPointerCapture 锁指针，
// document.body 加 class 强制全局 cursor=grabbing。
const draggingKey = ref('')
const DRAG_THRESHOLD = 5
let dragSession = null      // { key, startX, started, captured, el, suppressClick }

function canDrag(tab) {
  return tab.key !== tabsStore.homePath
}

function onTabPointerDown(e, tab) {
  // 仅左键
  if (e.button !== 0) return
  if (!canDrag(tab)) return
  const rect = e.currentTarget.getBoundingClientRect()
  dragSession = {
    key: tab.key,
    startX: e.clientX,
    started: false,
    captured: false,
    el: e.currentTarget,
    suppressClick: false,
    // ghost 偏移：保持鼠标在副本中的相对位置不变
    grabOffsetX: e.clientX - rect.left,
    grabOffsetY: e.clientY - rect.top,
    ghost: null
  }
  // 注意：先不 setPointerCapture，等过了阈值再 capture，避免短点击也走捕获影响 click
  window.addEventListener('pointermove', onWindowPointerMove)
  window.addEventListener('pointerup', onWindowPointerUp)
  window.addEventListener('pointercancel', onWindowPointerUp)
}

let pendingHitTest = false

function runHitTest(clientX) {
  pendingHitTest = false
  if (!dragSession || !dragSession.started) return
  const barRoot = dragSession.el.closest('[data-tabbar-root]')
  if (!barRoot) return
  const tabEls = barRoot.querySelectorAll('[data-tab-key]')
  let targetKey = ''
  let targetSide = 'before'
  for (const el of tabEls) {
    const key = el.dataset.tabKey
    if (key === tabsStore.homePath) continue
    if (key === draggingKey.value) continue
    const rect = el.getBoundingClientRect()
    const mid = rect.left + rect.width / 2
    if (clientX < mid) {
      targetKey = key
      targetSide = 'before'
      break
    }
    targetKey = key
    targetSide = 'after'
  }
  if (targetKey) {
    tabsStore.moveTab(draggingKey.value, targetKey, targetSide)
  }
}

function onWindowPointerMove(e) {
  if (!dragSession) return
  const dx = e.clientX - dragSession.startX
  if (!dragSession.started) {
    if (Math.abs(dx) < DRAG_THRESHOLD) return
    dragSession.started = true
    dragSession.suppressClick = true
    draggingKey.value = dragSession.key
    tip.value.show = false
    document.body.classList.add('app-tab-dragging')
    try {
      dragSession.el.setPointerCapture(e.pointerId)
      dragSession.captured = true
    } catch (_) { /* 某些浏览器在 disabled 元素上会失败，忽略 */ }
    // 创建跟随鼠标的浮动副本（ghost）
    const src = dragSession.el
    const srcRect = src.getBoundingClientRect()
    const ghost = src.cloneNode(true)
    ghost.style.position = 'fixed'
    ghost.style.left = '0'
    ghost.style.top = '0'
    ghost.style.width = srcRect.width + 'px'
    ghost.style.height = srcRect.height + 'px'
    ghost.style.pointerEvents = 'none'
    ghost.style.zIndex = '9999'
    ghost.style.opacity = '0.85'
    ghost.style.boxShadow = '0 4px 12px rgba(0,0,0,0.18)'
    ghost.style.willChange = 'transform'
    // 关键：去掉继承的 transition-all（否则每次 transform 都被 200ms 插值，鼠标永远追不上）
    ghost.style.transition = 'none'
    // 内部子元素也禁用过渡（hover/group-hover 等等）
    ghost.querySelectorAll('*').forEach((el) => { el.style.transition = 'none' })
    ghost.style.transform = `translate3d(${e.clientX - dragSession.grabOffsetX}px, ${e.clientY - dragSession.grabOffsetY}px, 0)`
    document.body.appendChild(ghost)
    dragSession.ghost = ghost
  }
  // ghost 必须立即贴鼠标走，不能被命中检测/重排卡住
  if (dragSession.ghost) {
    dragSession.ghost.style.transform = `translate3d(${e.clientX - dragSession.grabOffsetX}px, ${e.clientY - dragSession.grabOffsetY}px, 0)`
  }
  // 命中检测 + moveTab 走 rAF，避免每个 pointermove 都同步触发整条 tab 列表 re-render
  if (!pendingHitTest) {
    pendingHitTest = true
    const x = e.clientX
    requestAnimationFrame(() => runHitTest(x))
  }
}

function onWindowPointerUp(e) {
  if (!dragSession) return
  if (dragSession.captured) {
    try { dragSession.el.releasePointerCapture(e.pointerId) } catch (_) { /* noop */ }
  }
  if (dragSession.ghost) {
    dragSession.ghost.remove()
    dragSession.ghost = null
  }
  document.body.classList.remove('app-tab-dragging')
  draggingKey.value = ''
  // suppressClick 不立即清；click 事件会紧随 pointerup 后触发，由 onTabClick 读取并清掉
  const wasDragged = dragSession.suppressClick
  dragSession = wasDragged ? { ...dragSession, key: '', el: null } : null
  window.removeEventListener('pointermove', onWindowPointerMove)
  window.removeEventListener('pointerup', onWindowPointerUp)
  window.removeEventListener('pointercancel', onWindowPointerUp)
  // 短期保留 suppressClick 标记，下一帧后清空
  if (wasDragged) {
    setTimeout(() => { dragSession = null }, 0)
  }
}

function onTabClick(tab) {
  // 拖动结束后的伪 click 事件抑制
  if (dragSession?.suppressClick) return
  tabsStore.switchTab(tab.key)
}

// ── hover 提示：标题被省略(…)时，在 tab 正下方显示完整标题 ──
// 纯展示浮层（pointer-events-none），与同行 tab 不同高度 → 不遮挡邻居。
// Teleport 到 body 避免被 tab 栏 overflow 裁剪；仅 scrollWidth > clientWidth 时弹。
const tip = ref({ show: false, cx: 0, top: 0, text: '' })

function onTabEnter(e, tab) {
  if (draggingKey.value) return
  const el = e.currentTarget
  const span = el.querySelector('span')
  // 文字未被截断则不弹（短标签无需提示）
  if (!span || span.scrollWidth <= span.clientWidth + 1) {
    tip.value.show = false
    return
  }
  const rect = el.getBoundingClientRect()
  tip.value = {
    show: true,
    cx: rect.left + rect.width / 2,
    top: rect.bottom + 8,
    text: tabLabel(tab)
  }
}

function onTabLeave() {
  tip.value.show = false
}

// ── 右键菜单 ──
const contextMenu = ref({ show: false, x: 0, y: 0, key: '' })

function onContextMenu(e, key) {
  e.preventDefault()
  contextMenu.value = { show: true, x: e.clientX, y: e.clientY, key }
  const close = () => {
    contextMenu.value.show = false
    document.removeEventListener('click', close)
  }
  setTimeout(() => document.addEventListener('click', close), 0)
}

function ctxCloseCurrent() {
  if (contextMenu.value.key && contextMenu.value.key !== tabsStore.homePath) {
    tabsStore.removeTab(contextMenu.value.key)
  }
}

// ── 批量操作下拉菜单（tab 栏末端按钮） ──
const dropdown = ref({ show: false, x: 0, y: 0 })

function toggleDropdown(e) {
  if (dropdown.value.show) {
    dropdown.value.show = false
    return
  }
  const rect = e.currentTarget.getBoundingClientRect()
  dropdown.value = { show: true, x: rect.right, y: rect.bottom + 4 }
  const close = (ev) => {
    if (!ev.target.closest('.tab-dropdown-menu')) {
      dropdown.value.show = false
      document.removeEventListener('click', close)
    }
  }
  setTimeout(() => document.addEventListener('click', close), 0)
}

function ddCloseCurrent() {
  const active = tabsStore.activeTab
  if (active && active !== tabsStore.homePath) {
    tabsStore.removeTab(active)
  }
  dropdown.value.show = false
}

function ddCloseOthers() {
  const keep = tabsStore.activeTab || tabsStore.homePath
  tabsStore.closeOthers(keep)
  dropdown.value.show = false
}

function ddCloseAll() {
  tabsStore.closeAll()
  dropdown.value.show = false
}

const canCloseCurrent = computed(
  () => tabsStore.activeTab && tabsStore.activeTab !== tabsStore.homePath
)
const canCloseOthers = computed(
  () => tabsStore.tabs.filter((t) => t.path !== tabsStore.homePath).length >= 1
)
const canCloseAll = canCloseOthers

// ── overflow（多到极限不向右延伸，多出的收进 +N）──
const MIN_TAB = 56   // tab 最小宽度（与 min-w-[56px] 一致）
const PLUS_W = 48    // +N 按钮预留宽度
const navRef = ref(null)
const navWidth = ref(9999) // 测量前先假设够宽，避免首帧闪 +N
let navRO = null

onMounted(() => {
  if (!navRef.value) return
  navWidth.value = navRef.value.clientWidth
  navRO = new ResizeObserver((entries) => {
    navWidth.value = entries[0].contentRect.width
  })
  navRO.observe(navRef.value)
})
onBeforeUnmount(() => {
  if (navRO) navRO.disconnect()
})

// 容纳得下的 tab 数（放不下时为 +N 预留空间）
const visibleCount = computed(() => {
  const total = tabsStore.tabs.length
  const w = navWidth.value
  let count = Math.max(1, Math.floor(w / MIN_TAB))
  if (total > count) {
    count = Math.max(1, Math.floor((w - PLUS_W) / MIN_TAB))
  }
  return count
})

// 可见 tab：始终保证 active 在可见区
const visibleTabs = computed(() => {
  const all = tabsStore.tabs
  const n = visibleCount.value
  if (all.length <= n) return all
  const head = all.slice(0, n)
  const activeKey = tabsStore.activeTab
  if (head.some((t) => t.key === activeKey)) return head
  const activeTab = all.find((t) => t.key === activeKey)
  if (!activeTab) return head
  // active 落在隐藏区：用它替换最后一个可见槽
  return [...all.slice(0, Math.max(1, n - 1)), activeTab]
})

const hiddenTabs = computed(() => {
  const visKeys = new Set(visibleTabs.value.map((t) => t.key))
  return tabsStore.tabs.filter((t) => !visKeys.has(t.key))
})

// ── +N 溢出面板（hover 展开全部未显示 tab）──
const overflowPanel = ref({ show: false, x: 0, y: 0 })
let overflowTimer = null

function openOverflow(e) {
  clearTimeout(overflowTimer)
  tip.value.show = false // tooltip 与面板互斥
  const r = e.currentTarget.getBoundingClientRect()
  overflowPanel.value = { show: true, x: r.right, y: r.bottom + 6 }
}
function keepOverflow() {
  clearTimeout(overflowTimer)
}
function scheduleCloseOverflow() {
  overflowTimer = setTimeout(() => {
    overflowPanel.value.show = false
  }, 120)
}
function pickHidden(tab) {
  tabsStore.switchTab(tab.key)
  overflowPanel.value.show = false
}
function closeHidden(tab) {
  tabsStore.removeTab(tab.key)
}
</script>

<template>
  <div
    data-tabbar-root
    class="sticky top-14 z-30 bg-background flex items-center px-3 md:px-4 lg:px-6 py-2 gap-2 shrink-0 transition-shadow duration-200"
    :class="hasStickySubheader ? '' : 'shadow-[0_3px_6px_-3px_rgba(0,0,0,0.16)]'"
  >
    <!-- Browser 风 tabs：竖分割线分隔，均匀缩小并 truncate，缩到极限后收进 +N（不向右延伸） -->
    <div ref="navRef" class="flex items-center flex-1 min-w-0">
      <div class="flex items-center flex-1 min-w-0 overflow-hidden">
        <button
          v-for="tab in visibleTabs"
          :key="tab.key"
          :data-tab-key="tab.key"
          class="app-tab group relative inline-flex items-center justify-center h-7 px-3 rounded-md text-xs font-medium transition-all duration-200 cursor-pointer flex-1 min-w-[56px] max-w-[120px]"
          :class="[
            tabsStore.activeTab === tab.key
              ? 'is-active bg-brand-orange text-white'
              : 'text-muted-foreground hover:text-foreground hover:bg-muted',
            isClosable(tab) && 'hover:pr-7',
            draggingKey === tab.key && 'opacity-40 cursor-grabbing'
          ]"
          @pointerdown="onTabPointerDown($event, tab)"
          @click="onTabClick(tab)"
          @dblclick="tabsStore.refreshTab(tab.fullPath)"
          @contextmenu="onContextMenu($event, tab.key)"
          @mouseenter="onTabEnter($event, tab)"
          @mouseleave="onTabLeave"
        >
          <span class="truncate min-w-0">{{ tabLabel(tab) }}</span>
          <span
            v-if="isClosable(tab)"
            class="absolute right-1 top-1/2 -translate-y-1/2 inline-flex items-center justify-center w-4 h-4 rounded opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
            :class="tabsStore.activeTab === tab.key ? 'hover:bg-white/25' : 'hover:bg-foreground/15'"
            @pointerdown.stop
            @click.stop="tabsStore.removeTab(tab.key)"
            @dblclick.stop
          >
            <X :size="12" />
          </span>
        </button>
      </div>

      <!-- +N 溢出：未显示的 tab 数；hover 展开全部 -->
      <button
        v-if="hiddenTabs.length"
        type="button"
        class="shrink-0 ml-1 inline-flex items-center justify-center h-7 px-2 rounded-md text-xs font-semibold text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        @mouseenter="openOverflow"
        @mouseleave="scheduleCloseOverflow"
        @click.stop="openOverflow"
      >
        +{{ hiddenTabs.length }}
      </button>
    </div>

    <!-- 末端批量操作按钮 -->
    <div class="shrink-0 flex items-center pl-2 border-l border-border ml-1">
      <button
        type="button"
        :title="t('layout.tabs.tabAction')"
        class="inline-flex items-center justify-center h-7 w-7 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        @click.stop="toggleDropdown"
      >
        <MoreHorizontal :size="16" />
      </button>
    </div>

    <!-- +N 溢出面板：列出全部未显示的 tab -->
    <Teleport to="body">
      <div
        v-if="overflowPanel.show && hiddenTabs.length"
        class="fixed z-[100] -translate-x-full min-w-[200px] max-w-[280px] max-h-[60vh] overflow-y-auto scrollbar-thin py-1 bg-card border border-border rounded-lg shadow-xl text-sm"
        :style="{ left: overflowPanel.x + 'px', top: overflowPanel.y + 'px' }"
        @mouseenter="keepOverflow"
        @mouseleave="scheduleCloseOverflow"
      >
        <div
          v-for="tab in hiddenTabs"
          :key="tab.key"
          class="group flex items-center gap-2 px-3 py-1.5 cursor-pointer transition-colors hover:bg-muted"
          :class="tabsStore.activeTab === tab.key ? 'text-brand-orange font-medium' : 'text-foreground'"
          @click="pickHidden(tab)"
        >
          <span class="flex-1 truncate">{{ tabLabel(tab) }}</span>
          <span
            v-if="isClosable(tab)"
            class="shrink-0 inline-flex items-center justify-center w-4 h-4 rounded opacity-0 transition group-hover:opacity-100 hover:bg-background"
            @click.stop="closeHidden(tab)"
          >
            <X :size="12" />
          </span>
        </div>
      </div>
    </Teleport>

    <!-- hover 提示：标题被截断时，在 tab 正下方显示完整标题（纯展示，不遮挡邻居）-->
    <Teleport to="body">
      <div
        v-if="tip.show"
        class="app-tab-tip fixed z-[110] -translate-x-1/2 whitespace-nowrap px-2.5 py-1 rounded-md bg-foreground text-background text-xs font-medium shadow-lg pointer-events-none"
        :style="{ left: tip.cx + 'px', top: tip.top + 'px' }"
      >
        {{ tip.text }}
      </div>
    </Teleport>

    <!-- 右键上下文菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.show"
        class="fixed z-[100] min-w-[160px] py-1 bg-card border border-border rounded-lg shadow-xl text-sm"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <button
          v-if="contextMenu.key !== tabsStore.homePath"
          class="w-full px-3 py-1.5 text-left text-foreground hover:bg-muted transition-colors"
          @click="ctxCloseCurrent"
        >
          {{ t('layout.tabs.closeCurrent') }}
        </button>
        <button
          class="w-full px-3 py-1.5 text-left text-foreground hover:bg-muted transition-colors"
          @click="tabsStore.closeOthers(contextMenu.key)"
        >
          {{ t('layout.tabs.closeOthers') }}
        </button>
        <button
          class="w-full px-3 py-1.5 text-left text-foreground hover:bg-muted transition-colors"
          @click="tabsStore.closeAll()"
        >
          {{ t('layout.tabs.closeAll') }}
        </button>
      </div>
    </Teleport>

    <!-- 末端按钮下拉菜单 -->
    <Teleport to="body">
      <div
        v-if="dropdown.show"
        class="tab-dropdown-menu fixed z-[100] min-w-[180px] py-1 bg-card border border-border rounded-lg shadow-xl text-sm -translate-x-full"
        :style="{ left: dropdown.x + 'px', top: dropdown.y + 'px' }"
      >
        <button
          class="w-full px-3 py-1.5 text-left transition-colors"
          :class="canCloseCurrent ? 'text-foreground hover:bg-muted' : 'text-muted-foreground/50 cursor-not-allowed'"
          :disabled="!canCloseCurrent"
          @click="ddCloseCurrent"
        >
          {{ t('layout.tabs.closeCurrent') }}
        </button>
        <button
          class="w-full px-3 py-1.5 text-left transition-colors"
          :class="canCloseOthers ? 'text-foreground hover:bg-muted' : 'text-muted-foreground/50 cursor-not-allowed'"
          :disabled="!canCloseOthers"
          @click="ddCloseOthers"
        >
          {{ t('layout.tabs.closeOthers') }}
        </button>
        <button
          class="w-full px-3 py-1.5 text-left transition-colors"
          :class="canCloseAll ? 'text-foreground hover:bg-muted' : 'text-muted-foreground/50 cursor-not-allowed'"
          :disabled="!canCloseAll"
          @click="ddCloseAll"
        >
          {{ t('layout.tabs.closeAll') }}
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style>
/* 拖动期间强制全局 cursor，避免鼠标移到 tab 栏外时光标变回箭头 */
body.app-tab-dragging,
body.app-tab-dragging * {
  cursor: grabbing !important;
  user-select: none;
}

/* ── Browser 风分割线 ──
 * 非激活 tab 之间用竖线分隔；线在 tab 左侧。
 * 隐藏条件：首个 tab / 自身激活或 hover / 紧跟在激活或 hover 之后（Chrome 同款逻辑）。 */
.app-tab::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 1px;
  height: 16px;
  background: var(--border);
  transition: opacity 0.15s;
  pointer-events: none;
}
.app-tab:first-child::before,
.app-tab.is-active::before,
.app-tab:hover::before,
.app-tab.is-active + .app-tab::before,
.app-tab:hover + .app-tab::before {
  opacity: 0;
}

/* ── hover 提示（tab 下方）──
 * tab の真下に表示。上向きの三角矢印で tab を指す。 */
.app-tab-tip {
  animation: app-tab-tip-in 0.12s ease-out;
}
/* 上向き矢印 */
.app-tab-tip::before {
  content: '';
  position: absolute;
  left: 50%;
  top: -3px;
  width: 8px;
  height: 8px;
  background-color: inherit;
  transform: translateX(-50%) rotate(45deg);
}
@keyframes app-tab-tip-in {
  from {
    opacity: 0;
    transform: translate(-50%, -4px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}
</style>
