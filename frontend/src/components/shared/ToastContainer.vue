<script setup>
import { useToast } from '@/composables/useToast'
import { X } from 'lucide-vue-next'

const { toasts, toast } = useToast()

const COLOR_MAP = {
  success: 'border-signal-green/40 border-l-signal-green bg-signal-green/10',
  error: 'border-destructive/40 border-l-destructive bg-destructive/10',
  warning: 'border-signal-yellow/40 border-l-signal-yellow bg-signal-yellow/10',
  info: 'border-signal-blue/40 border-l-signal-blue bg-signal-blue/10'
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed top-4 right-4 z-[10000] flex flex-col gap-2 pointer-events-none">
      <TransitionGroup name="toast" tag="div" class="flex flex-col gap-2">
        <div
          v-for="t in toasts"
          :key="t.id"
          class="toast-item pointer-events-auto relative overflow-hidden flex items-center gap-3 min-w-[320px] max-w-md pl-4 pr-2 py-3 rounded-md border border-l-[3px] backdrop-blur-sm shadow-xl"
          :class="COLOR_MAP[t.type]"
        >
          <!-- Message -->
          <div class="flex-1 text-sm text-foreground font-medium leading-snug whitespace-pre-line break-words">
            {{ t.message }}
          </div>
          <!-- Close -->
          <button
            class="shrink-0 p-1 rounded text-muted-foreground hover:text-foreground hover:bg-muted/50 transition-colors"
            @click="toast.remove(t.id)"
          >
            <X :size="14" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
/* 卷轴展开效果：从右边缘向左展开，消失时向右收起 */
.toast-item {
  transform-origin: right center;
}
.toast-enter-active,
.toast-leave-active {
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.3s ease;
}
.toast-enter-from {
  transform: scaleX(0);
  opacity: 0;
}
.toast-leave-to {
  transform: scaleX(0);
  opacity: 0;
}
.toast-leave-active {
  position: absolute;
  right: 0;
}
</style>
