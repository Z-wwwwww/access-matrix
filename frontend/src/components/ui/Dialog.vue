<script setup>
import { watch, computed, provide } from 'vue'
import { X } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: 'max-w-lg'
  },
  /** Optional explicit panel height (e.g. `h-[80vh]`). When set, body flex-fills the panel. */
  height: {
    type: String,
    default: ''
  },
  /** true: panel sizes to content (w-auto) and caps at `width`. false: panel fills `width` (w-full). */
  fitContent: {
    type: Boolean,
    default: false
  },
  class: {
    type: String,
    default: ''
  },
  zIndex: {
    type: String,
    default: 'z-[90]'
  },
  closeOnOverlay: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:open', 'close'])

function close() {
  emit('update:open', false)
  emit('close')
}

function onOverlayClick() {
  if (props.closeOnOverlay) close()
}

watch(() => props.open, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})

// 子孫 popup (DatePicker/Select 等) が自分より上に出るための z-index 起点を provide
const popupZIndex = computed(() => {
  const m = props.zIndex.match(/\[(\d+)\]/) || props.zIndex.match(/z-(\d+)/)
  const base = m ? parseInt(m[1], 10) : 90
  return base + 10
})
provide('popupZIndex', popupZIndex)
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-overlay">
      <div
        v-if="open"
        :class="['fixed inset-0 bg-background/40 backdrop-blur-[2px]', zIndex]"
        @click="onOverlayClick"
      />
    </Transition>
    <Transition name="dialog-panel">
      <div
        v-if="open"
        :class="['fixed inset-0 flex items-center justify-center p-4 pointer-events-none', zIndex]"
      >
        <!-- Panel -->
        <div
          :class="cn(
            'relative rounded-2xl border border-border/60 bg-card shadow-2xl ring-1 ring-black/5 pointer-events-auto',
            fitContent ? 'w-auto' : 'w-full',
            width,
            height && 'flex flex-col',
            height,
            props.class
          )"
        >
          <!-- Header -->
          <div v-if="title || $slots.header || description" class="flex items-start justify-between gap-4 px-6 pt-5 pb-4 border-b border-border/60">
            <div class="min-w-0 flex-1">
              <slot name="header">
                <h2 class="text-base font-semibold leading-6 text-foreground truncate">{{ title }}</h2>
                <p v-if="description" class="mt-1 text-sm text-muted-foreground">{{ description }}</p>
              </slot>
            </div>
            <button
              class="shrink-0 -mr-1.5 -mt-0.5 p-1.5 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="Close"
              @click="close"
            >
              <X :size="16" />
            </button>
          </div>

          <!-- Body -->
          <!-- height 指定時：flex-1 で panel の余白を埋める。なし時：従来の max-h-[70vh] cap。 -->
          <div :class="cn(
            'px-6 py-5 overflow-y-auto scrollbar-thin',
            height ? 'flex-1 min-h-0' : 'max-h-[70vh]'
          )">
            <slot />
          </div>

          <!-- Footer -->
          <div
            v-if="$slots.footer"
            class="flex items-center justify-end gap-2 px-6 py-4 border-t border-border/60 bg-muted/40 rounded-b-2xl"
          >
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog-overlay-enter-active,
.dialog-overlay-leave-active {
  transition: opacity 0.2s ease;
}
.dialog-overlay-enter-from,
.dialog-overlay-leave-to {
  opacity: 0;
}

.dialog-panel-enter-active {
  transition: opacity 0.2s ease, transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}
.dialog-panel-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.dialog-panel-enter-from {
  opacity: 0;
  transform: scale(0.96) translateY(4px);
}
.dialog-panel-leave-to {
  opacity: 0;
  transform: scale(0.98);
}
</style>
