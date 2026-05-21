<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Smartphone, Lock, Key, ShieldCheck } from 'lucide-vue-next'
import Input from '@/components/ui/Input.vue'
import Dialog from '@/components/ui/Dialog.vue'
import { toast } from '@/composables/useToast'
import { getCaptchaApi } from '../../../services/auth'

const router = useRouter()

const form = reactive({
  mobile: '',
  password: '',
  password2: '',
  captcha: '',
  key: ''
})

const errors = reactive({})
const loading = ref(false)

const showImgCode = ref(false)
const imgCode = ref('')
const imgCodeError = ref(false)
const captchaSrc = ref('')
const codeLoading = ref(false)

const countdownTime = ref(30)
let countdownTimer = null

function clearError(key) {
  if (errors[key]) delete errors[key]
}

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  const checks = [
    ['mobile', !form.mobile],
    ['password', !form.password],
    ['password2', !form.password2 || form.password2 !== form.password],
    ['captcha', !form.captcha]
  ]
  for (const [key, isInvalid] of checks) {
    if (isInvalid) errors[key] = true
  }
  return Object.keys(errors).length === 0
}

async function changeImgCode() {
  try {
    const res = await getCaptchaApi()
    if (res.data.code === 0) {
      captchaSrc.value = res.data.data.captcha
      form.key = res.data.data.key
    } else {
      toast.error(res.data.msg)
    }
  } catch (e) {
    toast.error(e.message)
  }
}

function showImgCodeCheck() {
  if (!form.mobile) {
    errors.mobile = true
    toast.warning('携帯番号を入力してください')
    return
  }
  imgCode.value = ''
  imgCodeError.value = false
  changeImgCode()
  showImgCode.value = true
}

// TODO: バックエンドで SMS 送信 API が確定したら services/auth.js に sendSmsCodeApi を追加し、
//       ここで POST /login/sendSmsCode（body: { mobile, key, imgCode }）を呼ぶよう置き換える。
//       legacy は setTimeout によるモック実装のため、現実装も同じモックを踏襲。
async function sendCode() {
  if (!imgCode.value) {
    imgCodeError.value = true
    toast.warning('画像認証コードを入力してください')
    return
  }
  codeLoading.value = true
  setTimeout(() => {
    toast.success('SMS 認証コードを送信しました')
    showImgCode.value = false
    codeLoading.value = false
    startCountdownTimer()
  }, 1000)
}

function startCountdownTimer() {
  countdownTime.value = 30
  countdownTimer = setInterval(() => {
    if (countdownTime.value <= 1) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
    countdownTime.value--
  }, 1000)
}

// TODO: バックエンドでパスワード再設定 API が確定したら services/auth.js に resetPasswordApi を追加し、
//       ここで POST /login/resetPassword（body: { mobile, password, captcha, key }）を呼ぶよう置き換える。
//       legacy は setTimeout によるモック実装のため、現実装も同じモックを踏襲。
function doSubmit() {
  if (!validate()) return
  loading.value = true
  setTimeout(() => {
    loading.value = false
    toast.success('パスワードを変更しました')
    router.push('/login')
  }, 1000)
}

onMounted(() => {
  changeImgCode()
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-background p-4">
    <div class="w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg shadow-black/[0.06] p-8">
      <!-- Brand -->
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-foreground font-serif tracking-wide">Access Matrix</h1>
        <p class="mt-1 text-sm text-muted-foreground">パスワード再設定</p>
      </div>

      <form class="space-y-4" @submit.prevent="doSubmit">
        <!-- mobile -->
        <div data-field="mobile">
          <label class="block text-sm font-medium text-foreground mb-1.5">携帯番号</label>
          <div class="relative">
            <div class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground z-10">
              <Smartphone :size="16" />
            </div>
            <Input
              v-model="form.mobile"
              placeholder="バインド済みの携帯番号を入力"
              class="pl-10"
              :error="!!errors.mobile"
              @update:model-value="clearError('mobile')"
            />
          </div>
        </div>

        <!-- password -->
        <div data-field="password">
          <label class="block text-sm font-medium text-foreground mb-1.5">新しいパスワード</label>
          <div class="relative">
            <div class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground z-10">
              <Lock :size="16" />
            </div>
            <Input
              v-model="form.password"
              type="password"
              placeholder="新しいログインパスワードを入力"
              class="pl-10"
              :error="!!errors.password"
              @update:model-value="clearError('password')"
            />
          </div>
        </div>

        <!-- password2 -->
        <div data-field="password2">
          <label class="block text-sm font-medium text-foreground mb-1.5">パスワード（確認）</label>
          <div class="relative">
            <div class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground z-10">
              <Key :size="16" />
            </div>
            <Input
              v-model="form.password2"
              type="password"
              placeholder="もう一度パスワードを入力"
              class="pl-10"
              :error="!!errors.password2"
              @update:model-value="clearError('password2')"
            />
          </div>
          <p v-if="errors.password2" class="mt-1 text-xs text-destructive">
            {{ !form.password2 ? 'パスワード（確認）を入力してください' : '2 回入力したパスワードが一致しません' }}
          </p>
        </div>

        <!-- captcha (SMS code) -->
        <div data-field="captcha">
          <label class="block text-sm font-medium text-foreground mb-1.5">認証コード</label>
          <div class="flex items-stretch gap-2">
            <div class="relative flex-1">
              <div class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground z-10">
                <ShieldCheck :size="16" />
              </div>
              <Input
                v-model="form.captcha"
                placeholder="SMS 認証コード"
                class="pl-10"
                :error="!!errors.captcha"
                @update:model-value="clearError('captcha')"
              />
            </div>
            <button
              type="button"
              :disabled="!!countdownTimer"
              class="shrink-0 h-9 px-3 rounded-lg text-sm font-medium border border-input bg-card text-foreground hover:bg-muted disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
              @click="showImgCodeCheck"
            >
              <span v-if="!countdownTimer">認証コード送信</span>
              <span v-else>送信済み {{ countdownTime }}s</span>
            </button>
          </div>
        </div>

        <!-- Back link -->
        <div class="flex justify-end">
          <router-link
            to="/login"
            class="text-sm text-primary hover:underline"
          >
            ログインに戻る
          </router-link>
        </div>

        <!-- Submit -->
        <button
          type="submit"
          :disabled="loading"
          class="w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 active:bg-primary/80 transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ loading ? '処理中...' : 'パスワード変更' }}
        </button>
      </form>
    </div>

    <!-- 画像認証コードモーダル -->
    <Dialog
      :open="showImgCode"
      title="認証コード送信"
      width="max-w-sm"
      @update:open="(v) => (showImgCode = v)"
    >
      <div class="flex items-stretch gap-2 mb-4">
        <Input
          v-model="imgCode"
          placeholder="画像認証コードを入力"
          class="flex-1"
          :error="imgCodeError"
          @update:model-value="imgCodeError = false"
        />
        <button
          type="button"
          class="shrink-0 h-9 w-[102px] rounded-lg border border-input bg-card hover:bg-muted transition-colors overflow-hidden"
          title="更新"
          @click="changeImgCode"
        >
          <img
            v-if="captchaSrc"
            alt="captcha"
            :src="captchaSrc"
            class="w-full h-full object-cover"
          />
          <span v-else class="text-xs text-muted-foreground">読込中...</span>
        </button>
      </div>
      <button
        type="button"
        :disabled="codeLoading"
        class="w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        @click="sendCode"
      >
        {{ codeLoading ? '送信中...' : '今すぐ送信' }}
      </button>
    </Dialog>
  </div>
</template>
