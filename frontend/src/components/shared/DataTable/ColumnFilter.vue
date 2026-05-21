<script setup>
import { ref, computed, nextTick, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Filter, Check } from 'lucide-vue-next'

const props = defineProps({
  options: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()

const open = ref(false)
const btnRef = ref(null)
const dropRef = ref(null)
const dropStyle = ref({})

// ── Draft state：操作内部暂存、只在 Apply 时 emit 外部 ──
const draft = ref([...props.modelValue])

const hasFilter = computed(() => props.modelValue.length > 0)
const count = computed(() => props.modelValue.length)
const allSelected = computed(() => props.options.length > 0 && draft.value.length === props.options.length)

watch(() => props.modelValue, (val) => {
  if (!open.value) draft.value = [...val]
}, { deep: true })

function toggle(value) {
  const val = String(value)
  const idx = draft.value.indexOf(val)
  if (idx >= 0) {
    draft.value = draft.value.filter((v) => v !== val)
  } else {
    draft.value = [...draft.value, val]
  }
}

function isSelected(value) {
  return draft.value.includes(String(value))
}

function toggleAll() {
  if (allSelected.value) {
    draft.value = []
  } else {
    draft.value = props.options.map((o) => String(o.value))
  }
}

function apply() {
  emit('update:modelValue', [...draft.value])
  closePanel()
}

function clear() {
  draft.value = []
  emit('update:modelValue', [])
  closePanel()
}

function cancel() {
  draft.value = [...props.modelValue]
  closePanel()
}

function closePanel() {
  open.value = false
  document.removeEventListener('mousedown', onDocClick, true)
}

function updatePosition() {
  if (!btnRef.value) return
  const rect = btnRef.value.getBoundingClientRect()
  const panelMaxH = 360
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top
  if (spaceBelow < panelMaxH && spaceAbove > spaceBelow) {
    dropStyle.value = {
      position: 'fixed',
      bottom: (window.innerHeight - rect.top + 4) + 'px',
      left: rect.left + 'px'
    }
  } else {
    dropStyle.value = {
      position: 'fixed',
      top: rect.bottom + 4 + 'px',
      left: rect.left + 'px'
    }
  }
}

function onDocClick(e) {
  if (dropRef.value && dropRef.value.contains(e.target)) return
  if (btnRef.value && btnRef.value.contains(e.target)) return
  // 外部点击视为取消（不 apply）
  cancel()
}

async function toggleOpen() {
  if (open.value) {
    cancel()
    return
  }
  // 打开前同步 draft 到当前 modelValue
  draft.value = [...props.modelValue]
  open.value = true
  await nextTick()
  updatePosition()
  setTimeout(() => {
    document.addEventListener('mousedown', onDocClick, true)
  }, 0)
}

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick, true)
})
</script>

<template>
  <div class="inline-flex">
    <button
      ref="btnRef"
      type="button"
      :class="[
        'inline-flex items-center gap-1 h-6 px-1.5 rounded-md transition-colors',
        hasFilter
          ? 'text-primary bg-primary/10 hover:bg-primary/15'
          : 'text-muted-foreground/60 hover:text-foreground hover:bg-muted'
      ]"
      :title="hasFilter ? `${count} filtered` : 'Filter'"
      @click.stop="toggleOpen"
    >
      <Filter :size="13" />
      <span v-if="hasFilter" class="text-[11px] font-semibold leading-none">{{ count }}</span>
    </button>
    <Teleport to="body">
      <div
        v-if="open && options.length > 0"
        ref="dropRef"
        :style="dropStyle"
        class="z-[25] w-56 rounded-lg border border-border bg-card shadow-lg overflow-hidden flex flex-col"
      >
        <!-- Select all toggle -->
        <button
          type="button"
          class="flex items-center gap-2 w-full px-3 py-2 text-xs font-medium text-muted-foreground hover:bg-muted transition-colors text-left border-b border-border"
          @click.stop="toggleAll"
        >
          <span class="w-4 h-4 flex items-center justify-center shrink-0 rounded border border-input" :class="allSelected && 'bg-primary border-primary'">
            <Check v-if="allSelected" :size="12" class="text-primary-foreground" />
          </span>
          {{ t('common.button.selectAll') }}
        </button>

        <!-- Option list -->
        <div class="max-h-[240px] overflow-y-auto py-1">
          <button
            v-for="opt in options"
            :key="opt.value"
            type="button"
            class="flex items-center gap-2 w-full px-3 py-1.5 text-sm text-foreground hover:bg-muted transition-colors text-left"
            @click.stop="toggle(opt.value)"
          >
            <span
              class="w-4 h-4 flex items-center justify-center shrink-0 rounded border border-input"
              :class="isSelected(opt.value) && 'bg-primary border-primary'"
            >
              <Check v-if="isSelected(opt.value)" :size="12" class="text-primary-foreground" />
            </span>
            <span class="truncate">{{ opt.label }}</span>
          </button>
        </div>

        <!-- Footer actions -->
        <div class="flex items-center justify-between gap-2 px-2 py-2 border-t border-border bg-muted/30">
          <button
            type="button"
            class="inline-flex items-center justify-center h-7 px-2 rounded-md text-xs font-medium text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
            @click.stop="clear"
          >
            {{ t('common.button.clear') }}
          </button>
          <div class="flex items-center gap-1.5">
            <button
              type="button"
              class="inline-flex items-center justify-center h-7 px-2.5 rounded-md text-xs font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors"
              @click.stop="cancel"
            >
              {{ t('common.button.cancel') }}
            </button>
            <button
              type="button"
              class="inline-flex items-center justify-center h-7 px-2.5 rounded-md text-xs font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
              @click.stop="apply"
            >
              {{ t('common.button.apply') }}
            </button>
          </div>
        </div>
      </div>
      <div
        v-if="open && options.length === 0"
        ref="dropRef"
        :style="dropStyle"
        class="z-[25] min-w-[140px] rounded-lg border border-border bg-card shadow-lg py-3 px-3 text-sm text-muted-foreground"
      >
        —
      </div>
    </Teleport>
  </div>
</template>
