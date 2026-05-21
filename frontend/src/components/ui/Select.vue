<script setup>
import { ref, computed, nextTick, onBeforeUnmount, watch, inject } from 'vue'
import { cn } from '@/lib/utils'
import { ChevronDown, Check, X, Search } from 'lucide-vue-next'
import { usePopupFollowTrigger, applyAbsolutePopupPosition } from '@/composables/usePopupFollowTrigger'

const props = defineProps({
  modelValue: {
    type: [String, Number, Array],
    default: ''
  },
  options: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '選択してください'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  clearable: {
    type: Boolean,
    default: true
  },
  error: {
    type: Boolean,
    default: false
  },
  /**
   * 複数選択モード。true の場合 modelValue は Array、選択でトグル、パネルは閉じない。
   */
  multiple: {
    type: Boolean,
    default: false
  },
  /**
   * 搜索框开关：
   *   'auto' (默认) — options.length > 10 时自动显示
   *   true          — 强制显示
   *   false         — 强制隐藏
   */
  searchable: {
    type: [Boolean, String],
    default: 'auto'
  },
  /**
   * パネル幅をオプション内容に合わせて自動伸縮 (trigger 幅はそのまま)。
   * 通常は panel width = trigger width だが、長い label が多い場合に切れてしまう。
   * true にすると panel は trigger 幅以上、panelMaxWidth 以下で内容に応じて伸びる。
   */
  panelAutoWidth: {
    type: Boolean,
    default: false
  },
  /**
   * panelAutoWidth=true のときのパネル上限幅 (CSS 値、例: '320px' / '20rem')。
   */
  panelMaxWidth: {
    type: String,
    default: '320px'
  },
  class: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const triggerRef = ref(null)
const panelRef = ref(null)
const searchInputRef = ref(null)
const searchKeyword = ref('')

// 複数選択モードでは Array、単一選択モードでは単値を扱う
const selectedValues = computed(() => {
  if (!props.multiple) return []
  return Array.isArray(props.modelValue) ? props.modelValue : []
})

function isSelected(optValue) {
  if (props.multiple) {
    return selectedValues.value.some((v) => String(v) === String(optValue))
  }
  return String(optValue) === String(props.modelValue)
}

const selectedLabel = computed(() => {
  if (props.multiple) {
    if (selectedValues.value.length === 0) return ''
    const labels = selectedValues.value
      .map((v) => {
        const opt = props.options.find((o) => String(o.value) === String(v))
        return opt ? opt.label : null
      })
      .filter(Boolean)
    return labels.join(', ')
  }
  const opt = props.options.find((o) => String(o.value) === String(props.modelValue))
  return opt ? opt.label : ''
})

const hasValue = computed(() => {
  if (props.multiple) return selectedValues.value.length > 0
  return props.modelValue !== '' && props.modelValue != null
})

// 是否显示搜索框：默认 auto（>10 条自动启用），可显式开/关
const showSearch = computed(() => {
  if (props.searchable === true) return true
  if (props.searchable === false) return false
  return props.options.length > 10
})

// 按搜索关键词过滤的选项（不分大小写匹配 label，未启用搜索时直接返回原数组）
const filteredOptions = computed(() => {
  if (!showSearch.value || !searchKeyword.value.trim()) return props.options
  const kw = searchKeyword.value.trim().toLowerCase()
  return props.options.filter((o) => String(o.label || '').toLowerCase().includes(kw))
})

function select(opt) {
  if (props.multiple) {
    const current = selectedValues.value
    const exists = current.some((v) => String(v) === String(opt.value))
    const next = exists
      ? current.filter((v) => String(v) !== String(opt.value))
      : [...current, opt.value]
    emit('update:modelValue', next)
    // 複数選択モードではパネルを閉じない・検索キーワードもクリアしない
    return
  }
  emit('update:modelValue', opt.value)
  open.value = false
  searchKeyword.value = ''
}

// 关闭面板时清空搜索词
watch(open, (val) => {
  if (!val) {
    searchKeyword.value = ''
  }
})

// Drawer/Dialog 内なら自分より高い z-index、それ以外は undefined (class z-[60] が活きる)
const injectedPopupZ = inject('popupZIndex', null)

function updatePosition() {
  if (!triggerRef.value || !panelRef.value) return
  const rect = triggerRef.value.getBoundingClientRect()
  // panelAutoWidth=true のとき panel は trigger 幅以上、panelMaxWidth 以下で内容に追従
  const widthStyle = props.panelAutoWidth
    ? { minWidth: rect.width + 'px', maxWidth: props.panelMaxWidth, width: 'max-content' }
    : { width: rect.width + 'px' }
  applyAbsolutePopupPosition(panelRef.value, rect, {
    extraStyle: widthStyle,
    zIndex: injectedPopupZ?.value,
    triggerEl: triggerRef.value
  })
}

async function toggleOpen() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    await nextTick()
    updatePosition()
    // 启用搜索时自动聚焦搜索框。
    // preventScroll: true を付けないと、focus() 時に browser が input をビューポートに滑り込ませる挙動で
    // ページ全体が最下部まで自動スクロールする (特に Teleport 面板が position を確定する前に focus が走る場合)
    if (showSearch.value && searchInputRef.value) {
      searchInputRef.value.focus({ preventScroll: true })
    }
    setTimeout(() => {
      document.addEventListener('mousedown', onDocClick, true)
    }, 0)
  } else {
    document.removeEventListener('mousedown', onDocClick, true)
  }
}

function onDocClick(e) {
  if (panelRef.value && panelRef.value.contains(e.target)) return
  if (triggerRef.value && triggerRef.value.contains(e.target)) return
  open.value = false
  document.removeEventListener('mousedown', onDocClick, true)
}

// Popup を開いている間は trigger の位置に追従 (scroll / resize 時に再配置)
usePopupFollowTrigger(open, triggerRef, updatePosition)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick, true)
})
</script>

<template>
  <div class="group relative inline-flex w-full">
    <button
      ref="triggerRef"
      type="button"
      :disabled="disabled"
      :class="cn(
        'flex items-center justify-between w-full h-9 px-3 pr-8 border border-input rounded-lg bg-card text-sm transition cursor-pointer',
        'focus:outline-none focus:ring-2 focus:ring-ring',
        'hover:border-primary/50',
        'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
        open ? 'ring-2 ring-ring border-primary/50' : '',
        error && 'border-destructive focus:ring-destructive/30',
        props.class
      )"
      @click="toggleOpen"
    >
      <span v-if="selectedLabel" class="text-foreground truncate">{{ selectedLabel }}</span>
      <span v-else class="text-muted-foreground truncate">{{ placeholder }}</span>
      <!-- Chevron: always show when not clearable, or when no value -->
      <ChevronDown
        v-if="!clearable || !hasValue"
        :size="14"
        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground transition-transform duration-200"
        :class="{ 'rotate-180': open }"
      />
    </button>
    <!-- Clear button: hover to show (only when clearable + has value) -->
    <template v-if="clearable && hasValue && !disabled">
      <span
        class="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted z-10"
        @click.stop="emit('update:modelValue', multiple ? [] : ''); open = false"
      >
        <X :size="14" class="text-muted-foreground" />
      </span>
      <ChevronDown
        :size="14"
        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground transition-all duration-200 group-hover:opacity-0 pointer-events-none"
        :class="{ 'rotate-180': open }"
      />
    </template>

    <Teleport to="body">
      <div
        v-if="open"
        ref="panelRef"
        class="fixed -top-[9999px] -left-[9999px] z-[60] flex flex-col max-h-[280px] rounded-2xl border border-border bg-card shadow-xl overflow-hidden"
      >
        <!-- 搜索框：options.length > 10 时自动显示（searchable=auto），可强制开/关 -->
        <div v-if="showSearch" class="shrink-0 border-b border-border p-1.5">
          <div class="relative">
            <Search :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              ref="searchInputRef"
              v-model="searchKeyword"
              type="text"
              placeholder="検索..."
              class="w-full h-8 pl-8 pr-2 rounded-md border border-input bg-card text-sm focus:outline-none focus:ring-2 focus:ring-ring"
              @click.stop
            />
          </div>
        </div>
        <!-- 选项列表 -->
        <div class="flex-1 overflow-y-auto py-1">
          <button
            v-for="opt in filteredOptions"
            :key="opt.value"
            type="button"
            class="flex items-center justify-between w-full px-3 py-2 text-sm transition-colors text-left rounded-lg mx-1"
            :class="isSelected(opt.value)
              ? 'bg-primary/10 text-primary font-medium'
              : 'text-foreground hover:bg-muted'"
            :style="{ width: 'calc(100% - 0.5rem)' }"
            @click.stop="select(opt)"
          >
            <span class="truncate">{{ opt.label }}</span>
            <Check
              v-if="isSelected(opt.value)"
              :size="14"
              class="shrink-0 ml-2 text-primary"
            />
          </button>
          <!-- 无匹配结果 -->
          <div
            v-if="filteredOptions.length === 0"
            class="px-3 py-4 text-center text-sm text-muted-foreground"
          >
            該当なし
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
