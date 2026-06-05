<script setup>
/**
 * Per-row action cluster for the tenant list (table rows + cards).
 * Icon-only buttons; the parent wraps this to control hover-reveal vs always-on.
 * Mirrors the permission gates + built-in locking of the original table actions —
 * the child only emits; the parent owns the handlers (suspend/resume/delete dialogs).
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Pencil, LifeBuoy, Send, Pause, Play, Trash2 } from 'lucide-vue-next'

const props = defineProps({ row: { type: Object, required: true } })
const emit = defineEmits(['edit', 'support', 'resend', 'suspend', 'resume', 'delete'])
const { t } = useI18n()

const isBuiltIn = computed(() => props.row.tenantCode === 'system' || props.row.tenantCode === 'demo')
const isActive = computed(() => props.row.status === 1)

const base =
  'inline-grid place-items-center size-8 rounded-md transition-colors ' +
  'disabled:opacity-30 disabled:cursor-not-allowed'
</script>

<template>
  <div class="inline-flex items-center gap-0.5" @click.stop>
    <!-- Edit -->
    <button v-permission="'platform:tenant:update'" type="button"
            :class="[base, 'text-muted-foreground hover:bg-muted hover:text-foreground disabled:hover:bg-transparent disabled:hover:text-muted-foreground']"
            :disabled="isBuiltIn"
            :title="isBuiltIn ? t('platform.tenant.tooltip.builtInLocked') : t('platform.tenant.tooltip.edit')"
            @click="emit('edit', row)">
      <Pencil class="size-4" />
    </button>

    <!-- Support session (realm / recovery) — only for active, non-built-in -->
    <button v-permission="'platform:tenant:impersonate'" type="button"
            :class="[base, 'text-muted-foreground hover:bg-primary/10 hover:text-primary disabled:hover:bg-transparent disabled:hover:text-muted-foreground']"
            :disabled="isBuiltIn || !isActive"
            :title="isBuiltIn
                ? t('platform.tenant.tooltip.builtInLocked')
                : (!isActive ? t('platform.tenant.support.tooltip.disabledSuspended') : t('platform.tenant.support.tooltip.start'))"
            @click="emit('support', row)">
      <LifeBuoy class="size-4" />
    </button>

    <!-- Resend admin invite -->
    <button v-permission="'platform:tenant:update'" type="button"
            :class="[base, 'text-muted-foreground hover:bg-primary/10 hover:text-primary disabled:hover:bg-transparent disabled:hover:text-muted-foreground']"
            :disabled="isBuiltIn"
            :title="isBuiltIn ? t('platform.tenant.tooltip.builtInLocked') : t('platform.tenant.resendInvite.tooltip.resend')"
            @click="emit('resend', row)">
      <Send class="size-4" />
    </button>

    <!-- Suspend / Resume toggle -->
    <button v-if="isActive" v-permission="'platform:tenant:update'" type="button"
            :class="[base, 'text-muted-foreground hover:bg-muted hover:text-foreground disabled:hover:bg-transparent disabled:hover:text-muted-foreground']"
            :disabled="isBuiltIn"
            :title="isBuiltIn ? t('platform.tenant.tooltip.builtInLocked') : t('platform.tenant.tooltip.suspend')"
            @click="emit('suspend', row)">
      <Pause class="size-4" />
    </button>
    <button v-else v-permission="'platform:tenant:update'" type="button"
            :class="[base, 'text-emerald-600 hover:bg-emerald-500/10 dark:text-emerald-400']"
            :title="t('platform.tenant.tooltip.resume')"
            @click="emit('resume', row)">
      <Play class="size-4" />
    </button>

    <!-- Hard delete — only suspended rows expose it (recycle-bin model) -->
    <button v-if="!isActive" v-permission="'platform:tenant:delete'" type="button"
            :class="[base, 'text-muted-foreground hover:bg-destructive/10 hover:text-destructive disabled:hover:bg-transparent disabled:hover:text-muted-foreground']"
            :disabled="isBuiltIn"
            :title="isBuiltIn ? t('platform.tenant.tooltip.builtInLocked') : t('platform.tenant.hardDelete.tooltip.confirm')"
            @click="emit('delete', row)">
      <Trash2 class="size-4" />
    </button>
  </div>
</template>
