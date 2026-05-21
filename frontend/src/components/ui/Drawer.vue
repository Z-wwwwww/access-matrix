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
    default: 'max-w-md'
  },
  side: {
    type: String,
    default: 'right'
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
// 'z-[90]' / 'z-50' 等の Tailwind class から数値を抽出して + 10
const popupZIndex = computed(() => {
  const m = props.zIndex.match(/\[(\d+)\]/) || props.zIndex.match(/z-(\d+)/)
  const base = m ? parseInt(m[1], 10) : 90
  return base + 10
})
provide('popupZIndex', popupZIndex)
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-overlay">
      <div
        v-if="open"
        :class="['fixed inset-0 bg-background/40 backdrop-blur-[2px]', zIndex]"
        @click="onOverlayClick"
      />
    </Transition>
    <Transition :name="side === 'left' ? 'drawer-left' : 'drawer-right'">
      <div
        v-if="open"
        :class="cn(
          'fixed top-0 bottom-0 flex flex-col w-full border-border/60 bg-card shadow-2xl ring-1 ring-black/5',
          side === 'left' ? 'left-0 border-r' : 'right-0 border-l',
          width,
          zIndex,
          props.class
        )"
      >
        <div v-if="title || $slots.header || description" class="flex items-start justify-between gap-4 px-6 pt-5 pb-4 border-b border-border/60 shrink-0">
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

        <div class="flex-1 overflow-y-auto scrollbar-thin px-6 py-5">
          <slot />
        </div>

        <div
          v-if="$slots.footer"
          class="flex items-center justify-end gap-2 px-6 py-4 border-t border-border/60 bg-muted/40 shrink-0"
        >
          <slot name="footer" />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-overlay-enter-active,
.drawer-overlay-leave-active {
  transition: opacity 0.2s ease;
}
.drawer-overlay-enter-from,
.drawer-overlay-leave-to {
  opacity: 0;
}

.drawer-right-enter-active {
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-right-leave-active {
  transition: transform 0.2s ease;
}
.drawer-right-enter-from,
.drawer-right-leave-to {
  transform: translateX(100%);
}

.drawer-left-enter-active {
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-left-leave-active {
  transition: transform 0.2s ease;
}
.drawer-left-enter-from,
.drawer-left-leave-to {
  transform: translateX(-100%);
}
</style>
