<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { iconMap, iconCategories } from '@/utils/icon-registry'
import { X, ChevronDown, Search } from 'lucide-vue-next'

const { t } = useI18n()

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const searchQuery = ref('')
const activeCategory = ref(0)
const pickerRef = ref(null)

// Resolved preview icon
const previewIcon = computed(() => {
  if (!props.modelValue) return null
  return iconMap[props.modelValue] || null
})

// Filtered icons based on search
const filteredCategories = computed(() => {
  if (!searchQuery.value) return iconCategories
  const q = searchQuery.value.toLowerCase()
  return iconCategories
    .map((cat) => ({
      ...cat,
      icons: cat.icons.filter((name) => name.toLowerCase().includes(q))
    }))
    .filter((cat) => cat.icons.length > 0)
})

function select(name) {
  emit('update:modelValue', name)
  open.value = false
  searchQuery.value = ''
}

function clear() {
  emit('update:modelValue', '')
}

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    searchQuery.value = ''
    // Set active category to the one containing current icon
    if (props.modelValue) {
      const idx = iconCategories.findIndex((c) => c.icons.includes(props.modelValue))
      if (idx >= 0) activeCategory.value = idx
    }
  }
}

// Close on click outside
function onClickOutside(e) {
  if (pickerRef.value && !pickerRef.value.contains(e.target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside))
</script>

<template>
  <div ref="pickerRef" class="relative">
    <!-- Trigger -->
    <button
      type="button"
      class="w-full h-10 flex items-center gap-2 px-3 border border-input rounded-lg bg-background text-sm transition focus:outline-none focus:ring-2 focus:ring-ring"
      :class="disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:border-muted-foreground'"
      @click="toggle"
    >
      <component v-if="previewIcon" :is="previewIcon" :size="16" class="shrink-0 text-foreground" />
      <span v-if="modelValue" class="flex-1 truncate text-foreground text-left">{{ modelValue }}</span>
      <span v-else class="flex-1 text-muted-foreground text-left">{{ t('picker.icon.selectPlaceholder') }}</span>
      <button
        v-if="modelValue && !disabled"
        type="button"
        class="p-0.5 rounded hover:bg-muted text-muted-foreground shrink-0"
        @click.stop="clear"
      >
        <X :size="14" />
      </button>
      <ChevronDown :size="14" class="shrink-0 text-muted-foreground" />
    </button>

    <!-- Dropdown -->
    <Transition name="picker">
      <div
        v-if="open"
        class="absolute z-50 left-0 top-full mt-1 w-[520px] max-h-[400px] bg-card border border-border rounded-xl shadow-xl flex flex-col overflow-hidden"
      >
        <!-- Search -->
        <div class="p-2 border-b border-border">
          <div class="relative">
            <Search :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              v-model="searchQuery"
              type="text"
              :placeholder="t('picker.icon.searchPlaceholder')"
              class="w-full h-8 pl-8 pr-3 border border-input rounded-md bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>
        </div>

        <div class="flex flex-1 overflow-hidden min-h-0">
          <!-- Category tabs (left) -->
          <div class="w-[130px] shrink-0 border-r border-border overflow-y-auto py-1">
            <button
              v-for="(cat, idx) in filteredCategories"
              :key="cat.label"
              type="button"
              class="w-full px-3 py-1.5 text-left text-xs transition-colors truncate"
              :class="activeCategory === idx
                ? 'bg-brand-orange/10 text-brand-orange font-semibold'
                : 'text-muted-foreground hover:text-foreground hover:bg-muted'"
              @click="activeCategory = idx"
            >
              {{ cat.label }}
            </button>
          </div>

          <!-- Icon grid (right) -->
          <div class="flex-1 overflow-y-auto p-2">
            <div
              v-if="filteredCategories[activeCategory]"
              class="grid grid-cols-8 gap-1"
            >
              <button
                v-for="name in filteredCategories[activeCategory].icons"
                :key="name"
                type="button"
                class="w-9 h-9 flex items-center justify-center rounded-lg transition-colors"
                :class="modelValue === name
                  ? 'bg-brand-orange text-white'
                  : 'text-foreground hover:bg-muted'"
                :title="name"
                @click="select(name)"
              >
                <component :is="iconMap[name]" :size="18" />
              </button>
            </div>
            <div v-else class="p-4 text-center text-sm text-muted-foreground">
              {{ t('picker.icon.noResults') }}
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.picker-enter-active,
.picker-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.picker-enter-from,
.picker-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
