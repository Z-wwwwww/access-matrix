<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { LifeBuoy, X } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/composables/useToast'

/**
 * Persistent red banner shown while the platform-ops user is acting as
 * a tenant's SUPER_ADMIN. Mounted at the top of AppLayout so it's
 * visible on every page.
 *
 * Drives:
 *   - countdown to the JWT exp claim (auto-terminates locally when 0)
 *   - explicit Terminate button → restores ops session + redirects to
 *     /platform/tenants
 *
 * "Locally terminate" means: drop the support token from localStorage
 * and restore the ops one. The minted token itself stays valid until
 * its exp on the backend side; for v1 we accept this. A server-side
 * revoke list is tracked as a follow-up.
 */
const { t } = useI18n()
const auth = useAuthStore()

const now = ref(Date.now())
let tick = null

const info = computed(() => auth.supportSessionInfo)
const visible = computed(() => auth.isSupportSession && !!info.value)

const expiresAtMs = computed(() => {
  const iso = info.value?.expiresAt
  return iso ? Date.parse(iso) : 0
})

const remainingSec = computed(() => {
  if (!expiresAtMs.value) return 0
  return Math.max(0, Math.floor((expiresAtMs.value - now.value) / 1000))
})

const remainingFmt = computed(() => {
  const s = remainingSec.value
  const mm = Math.floor(s / 60).toString().padStart(2, '0')
  const ss = (s % 60).toString().padStart(2, '0')
  return `${mm}:${ss}`
})

function terminate() {
  if (!auth.terminateSupportSession()) return
  toast.success(t('platform.tenant.support.message.terminated'))
  // Hard navigation (NOT router.push) back to the platform tenants list.
  // Dynamic routes are registered once per page-load from the menu and are
  // never rebuilt on identity change — while a support session is active the
  // in-memory routes are the *tenant's* menu, which has no /platform/* paths.
  // A client-side router.push here would resolve against those stale routes,
  // hit the catch-all 404, and only recover after the follow-up reload (the
  // visible "404 flash"). A full document load reads the already-restored ops
  // token, refetches the ops menu, and resolves /platform/tenants on first
  // paint — no flash.
  window.location.assign('/platform/tenants')
}

onMounted(() => {
  tick = setInterval(() => {
    now.value = Date.now()
    if (visible.value && remainingSec.value === 0) {
      // Auto-terminate locally so the banner clears + ops session is back
      // before the user discovers their next API call 401'd.
      terminate()
    }
  }, 1000)
})
onBeforeUnmount(() => {
  if (tick) clearInterval(tick)
})
</script>

<template>
  <div v-if="visible"
       class="bg-destructive/10 border-b border-destructive/30 text-destructive">
    <div class="px-4 py-2 flex items-center gap-3 text-sm">
      <LifeBuoy class="size-4 shrink-0" />
      <div class="flex-1 min-w-0">
        <span class="font-medium">
          {{ t('platform.tenant.support.banner.acting', {
            displayName: info.displayName,
            tenantCode: info.tenantCode
          }) }}
        </span>
        <span class="mx-2 opacity-50">·</span>
        <span class="font-mono tabular-nums">{{ remainingFmt }}</span>
        <span class="mx-2 opacity-50">·</span>
        <span class="text-xs opacity-80">{{ t('platform.tenant.support.banner.note') }}</span>
      </div>
      <button class="h-7 px-3 rounded border border-destructive/40 hover:bg-destructive/15 text-xs inline-flex items-center gap-1"
              @click="terminate">
        <X class="size-3.5" />
        {{ t('platform.tenant.support.button.terminate') }}
      </button>
    </div>
  </div>
</template>
