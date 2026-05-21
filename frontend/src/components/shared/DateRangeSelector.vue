<script setup>
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check } from 'lucide-vue-next'
import { toJST } from '@/lib/date'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'

const props = defineProps({
  initStartDate: {
    type: String,
    default: ''
  },
  initEndDate: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:outputDates'])

const { t } = useI18n()

const startDate = ref('')
const endDate = ref('')
const selectedDays = ref([])
const outputDates = ref([])

const days = t('priceInventory.weekStrings').split(',')

function calculateDates() {
  if (!startDate.value || !endDate.value || selectedDays.value.length === 0) {
    outputDates.value = []
    emit('update:outputDates', [])
    return
  }
  const result = []
  let current = toJST(startDate.value)
  const end = toJST(endDate.value)
  while (current.isBefore(end) || current.isSame(end, 'day')) {
    if (selectedDays.value.includes(current.day())) {
      result.push(current.format('YYYY-MM-DD'))
    }
    current = current.add(1, 'day')
  }
  outputDates.value = result
  emit('update:outputDates', result)
}

function updateSelectedDays() {
  if (!startDate.value || !endDate.value) {
    selectedDays.value = []
    return
  }
  const daySet = new Set()
  let current = toJST(startDate.value)
  const end = toJST(endDate.value)
  while (current.isBefore(end) || current.isSame(end, 'day')) {
    daySet.add(current.day())
    current = current.add(1, 'day')
  }
  selectedDays.value = [...daySet]
  calculateDates()
}

function toggleDay(dayIndex) {
  const idx = selectedDays.value.indexOf(dayIndex)
  if (idx >= 0) {
    selectedDays.value.splice(idx, 1)
  } else {
    selectedDays.value.push(dayIndex)
  }
  calculateDates()
}

watch([startDate, endDate], () => {
  updateSelectedDays()
})

onMounted(() => {
  if (props.initStartDate) startDate.value = props.initStartDate
  if (props.initEndDate) endDate.value = props.initEndDate
  if (startDate.value && endDate.value) {
    updateSelectedDays()
  }
})
</script>

<template>
  <div class="space-y-3">
    <DateRangePicker
      v-model:start-date="startDate"
      v-model:end-date="endDate"
      range-mode
    />
    <div class="flex flex-wrap gap-1.5">
      <button
        v-for="(day, index) in days"
        :key="index"
        :class="[
          'relative inline-flex items-center justify-center h-8 min-w-[3rem] px-2 rounded-md text-xs font-medium border transition-colors',
          selectedDays.includes(index)
            ? 'bg-primary text-primary-foreground border-primary'
            : 'bg-background text-foreground border-border hover:bg-muted'
        ]"
        @click="toggleDay(index)"
      >
        {{ day }}
        <Check v-if="selectedDays.includes(index)" :size="12" class="ml-0.5" />
      </button>
    </div>
  </div>
</template>
