<script setup>
import { computed, ref, watch, nextTick, onMounted, onScopeDispose } from 'vue'
import { TabsRoot, TabsList, TabsTrigger } from 'radix-vue'
import { cn } from '@/lib/utils'

defineOptions({ name: 'Tabs' })

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  items: {
    type: Array,
    default: () => []
  },
  variant: {
    type: String,
    default: 'underline'
  },
  size: {
    type: String,
    default: 'md'
  },
  sticky: {
    type: Boolean,
    default: false
  },
  containerClass: {
    type: String,
    default: ''
  },
  listClass: {
    type: String,
    default: ''
  }
})

defineEmits(['update:modelValue'])

const visibleItems = computed(() => props.items.filter((it) => it.visible !== false))

const sizeCls = computed(() => {
  return {
    sm: 'px-3 py-2 text-xs',
    md: 'px-4 py-2.5 text-sm',
    lg: 'px-5 py-3 text-base'
  }[props.size] || 'px-4 py-2.5 text-sm'
})

const triggerBase = 'relative inline-flex items-center gap-2 font-medium text-muted-foreground transition-all duration-200 outline-none whitespace-nowrap cursor-pointer select-none'

const variantCls = computed(() => {
  if (props.variant === 'pill') {
    return {
      list: 'flex w-fit max-w-full min-w-0 items-center gap-1 rounded-lg bg-muted/60 p-1',
      trigger:
        'rounded-md hover:text-foreground data-[state=active]:bg-card data-[state=active]:text-primary data-[state=active]:shadow-sm'
    }
  }
  if (props.variant === 'segmented') {
    return {
      list: 'inline-flex items-center rounded-lg border border-border bg-card p-0.5',
      trigger:
        'rounded-md hover:text-foreground data-[state=active]:bg-primary data-[state=active]:text-primary-foreground'
    }
  }
  // underline: トリガー自身は色付きの下線を描かず、共有インジケーターが滑らかに移動する
  return {
    list: 'relative flex items-center gap-1 border-b border-border bg-card px-2',
    trigger:
      'border-b-2 border-transparent -mb-px hover:text-foreground hover:bg-muted/40 rounded-t-md data-[state=active]:text-primary'
  }
})

// ─── Sliding indicator for underline variant ───
const listRef = ref(null)
const indicatorStyle = ref({ left: '0px', width: '0px', opacity: '0' })

function getListEl() {
  const r = listRef.value
  return r?.$el || r || null
}

function updateIndicator() {
  if (props.variant !== 'underline') return
  const el = getListEl()
  if (!el) return
  const active = el.querySelector('[data-state="active"]')
  if (!active) {
    indicatorStyle.value = { ...indicatorStyle.value, opacity: '0' }
    return
  }
  indicatorStyle.value = {
    left: `${active.offsetLeft}px`,
    width: `${active.offsetWidth}px`,
    opacity: '1',
  }
}

watch(() => visibleItems.value.length, () => nextTick(updateIndicator))

// Radix の data-state 更新は reactive effect 経由で走るため onMounted+nextTick より遅れる場合がある。
// MutationObserver で属性変化を捉えて確実にインジケーター位置を更新する。
let resizeObserver = null
let mutationObserver = null

// アクティブ tab を中央にスクロール (オーバーフロー時)
function scrollActiveIntoView(smooth = true) {
  const el = getListEl()
  if (!el) return
  const active = el.querySelector('[data-state="active"]')
  if (!active) return
  active.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', inline: 'center', block: 'nearest' })
}

watch(() => props.modelValue, () => nextTick(() => scrollActiveIntoView(true)))

// マウス左ドラッグで横スクロール (touch は native scroll で対応)
let dragState = null
function onPointerDown(e) {
  if (e.pointerType !== 'mouse' || e.button !== 0) return
  const el = getListEl()
  if (!el) return
  if (el.scrollWidth <= el.clientWidth) return // オーバーフロー無し
  dragState = {
    pointerId: e.pointerId,
    startX: e.clientX,
    startScroll: el.scrollLeft,
    captured: false
  }
}
function onPointerMove(e) {
  if (!dragState || e.pointerId !== dragState.pointerId) return
  const el = getListEl()
  if (!el) return
  const dx = e.clientX - dragState.startX
  if (!dragState.captured && Math.abs(dx) > 5) {
    try { el.setPointerCapture(e.pointerId) } catch {}
    dragState.captured = true
  }
  if (dragState.captured) {
    el.scrollLeft = dragState.startScroll - dx
    e.preventDefault()
  }
}
function onPointerUp(e) {
  if (!dragState) return
  const el = getListEl()
  const wasDrag = dragState.captured
  if (el && wasDrag) {
    try { el.releasePointerCapture(dragState.pointerId) } catch {}
    // ドラッグ後に発火する click を抑制 (タブが意図せず切替わるのを防ぐ)
    const suppress = (ev) => { ev.stopPropagation(); ev.preventDefault() }
    window.addEventListener('click', suppress, { capture: true, once: true })
    setTimeout(() => window.removeEventListener('click', suppress, { capture: true }), 0)
  }
  dragState = null
}
function onPointerCancel(e) {
  if (!dragState || e.pointerId !== dragState.pointerId) return
  const el = getListEl()
  if (el && dragState.captured) {
    try { el.releasePointerCapture(dragState.pointerId) } catch {}
  }
  dragState = null
}

onMounted(() => {
  const el = getListEl()
  if (!el) return

  mutationObserver = new MutationObserver(() => updateIndicator())
  mutationObserver.observe(el, {
    attributes: true,
    attributeFilter: ['data-state'],
    subtree: true,
  })

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => updateIndicator())
    resizeObserver.observe(el)
  }

  el.addEventListener('pointerdown', onPointerDown)
  el.addEventListener('pointermove', onPointerMove)
  el.addEventListener('pointerup', onPointerUp)
  el.addEventListener('pointercancel', onPointerCancel)

  // 初期化時にもトライ（Radix の状態反映が既に完了していれば当たる）
  nextTick(() => {
    updateIndicator()
    scrollActiveIntoView(false)
  })
})
onScopeDispose(() => {
  mutationObserver?.disconnect()
  resizeObserver?.disconnect()
  const el = getListEl()
  if (el) {
    el.removeEventListener('pointerdown', onPointerDown)
    el.removeEventListener('pointermove', onPointerMove)
    el.removeEventListener('pointerup', onPointerUp)
    el.removeEventListener('pointercancel', onPointerCancel)
  }
})
</script>

<template>
  <TabsRoot
    :model-value="String(modelValue)"
    :class="cn('flex flex-col', containerClass)"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <TabsList
      ref="listRef"
      :class="
        cn(
          variantCls.list,
          sticky && 'sticky top-0 z-10',
          'overflow-x-auto overflow-y-hidden scrollbar-none',
          listClass
        )
      "
    >
      <TabsTrigger
        v-for="item in visibleItems"
        :key="item.value"
        :value="String(item.value)"
        :disabled="item.disabled"
        :class="
          cn(
            triggerBase,
            sizeCls,
            variantCls.trigger,
            item.disabled && 'opacity-50 cursor-not-allowed'
          )
        "
      >
        <component :is="item.icon" v-if="item.icon" :size="16" />
        <span>{{ item.label }}</span>
        <span
          v-if="item.badge !== undefined && item.badge !== null && item.badge !== ''"
          class="ml-1 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1.5 text-[11px] rounded-full bg-muted text-muted-foreground data-[state=active]:bg-primary/15 data-[state=active]:text-primary"
        >
          {{ item.badge }}
        </span>
      </TabsTrigger>
      <!-- Sliding underline indicator (underline variant only)
           -bottom-px (-1px) で list の bottom border (1px) を覆い、視覚厚さ 2px に抑える -->
      <span
        v-if="variant === 'underline'"
        aria-hidden="true"
        class="absolute -bottom-px h-0.5 bg-primary rounded-full pointer-events-none transition-[left,width,opacity] duration-300 ease-out"
        :style="indicatorStyle"
      />
    </TabsList>

    <!-- Grid-stack wrapper:
         全 TabsContent を同じグリッドセルに積層 → コンテナ高は最高 panel で固定。
         tab 切替時 visibility のみ変化 → layout 不変 → スクロール位置保持（antdv 同方式）。
         ※ 各 TabsContent は `force-mount` 必須（Radix が inactive 時に DOM を消さないため）。 -->
    <div class="tabs-stack grid grid-cols-1 grid-rows-1">
      <slot />
    </div>
  </TabsRoot>
</template>

<style scoped>
/* 全 TabsContent を (1,1) セルに重ねる */
.tabs-stack :deep([role="tabpanel"]) {
  grid-column: 1;
  grid-row: 1;
}
/* Radix inactive 時の `hidden` 属性 (display:none) を打ち消し、visibility で隠す */
.tabs-stack :deep([role="tabpanel"][hidden]) {
  display: block !important;
  visibility: hidden;
  pointer-events: none;
}
</style>
