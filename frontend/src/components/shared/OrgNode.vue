<script setup>
import { computed } from 'vue'
import { deptIconFor } from '@/utils/dept-icons'

defineOptions({ name: 'OrgNode' })

/**
 * 縦向き org-chart の 1 ノード（自分 + 直下のサブツリー）を再帰的に描画する。
 *
 * - 連結線は CSS pseudo-element で 1px 縦・横線として描画
 * - 階層に応じたアイコン（本社 → 拠点 → 業務単位 → チーム → 個別ユニット）
 * - クリックで親に select を emit
 */
const props = defineProps({
  node: { type: Object, required: true },
  modelValue: { type: [String, Number], default: '' },
  /** 階層深さ（root=0）。子に再帰時 level+1 を渡す。 */
  level: { type: Number, default: 0 }
})

const emit = defineEmits(['select'])

const isLeaf = computed(() => !props.node.children?.length)
const isSelected = computed(() => String(props.node.id) === String(props.modelValue))
const NodeIcon = computed(() => deptIconFor(props.level))
</script>

<template>
  <div class="org-subtree">
    <button
      type="button"
      class="org-node"
      :class="{ 'is-selected': isSelected, 'is-leaf': isLeaf }"
      @click="emit('select', node)"
    >
      <component :is="NodeIcon" :size="14" class="org-node-icon" />
      <span class="org-node-name">{{ node.name || node.label }}</span>
      <span v-if="node.code" class="org-node-code">({{ node.code }})</span>
    </button>
    <div v-if="!isLeaf" class="org-children">
      <OrgNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :model-value="modelValue"
        :level="level + 1"
        @select="(n) => emit('select', n)"
      />
    </div>
  </div>
</template>

<style scoped>
/*
 * 注意: このプロジェクトの CSS 変数 (--border, --primary 等) は
 * すでに完全な hsl(...) 形で定義されている。
 *   --border: hsl(30, 12%, 82%);
 * なので background: hsl(var(--border)) は hsl(hsl(...)) の入れ子になり無効。
 * → 必ず var(--border) を直接使う。
 * 透明度を載せたい場合は color-mix(in srgb, var(--primary) X%, transparent)。
 *
 * 連結線色は --border より少し濃いほうが視認しやすいので、color-mix で軽く強調。
 */
.org-subtree {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  padding: 0 6px;
}

/* ノード本体（カード状） */
.org-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--card);
  color: var(--foreground);
  font-size: 0.8125rem;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s, color 0.15s, box-shadow 0.15s;
}
.org-node:hover {
  border-color: color-mix(in srgb, var(--primary) 50%, transparent);
  background: var(--muted);
}
.org-node.is-selected {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 10%, var(--card));
  color: var(--primary);
  font-weight: 500;
  box-shadow: 0 0 0 1px var(--primary);
}
.org-node-icon { color: var(--muted-foreground); flex-shrink: 0; }
.org-node.is-selected .org-node-icon { color: var(--primary); }
.org-node-code { color: var(--muted-foreground); font-size: 0.6875rem; }
.org-node.is-selected .org-node-code { color: color-mix(in srgb, var(--primary) 70%, transparent); }

/* 子ノード群（水平に並ぶ） */
.org-children {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  margin-top: 20px;
  position: relative;
}

/* 連結線色（--border より少しコントラスト強めにして視認性確保） */
:where(.org-children, .org-children > .org-subtree) {
  --org-line: color-mix(in srgb, var(--foreground) 25%, transparent);
}

/* 親ノード下端から横線バスまでの縦線 */
.org-children::before {
  content: '';
  position: absolute;
  top: -20px;
  left: 50%;
  width: 1px;
  height: 20px;
  background: var(--org-line);
}

/* 子サブツリーは横線バス分のスペースを top に確保 */
.org-children > .org-subtree {
  padding-top: 20px;
}

/* 横線バス（兄弟をまたぐ）。位置によって左半 / 右半 / 全幅 / 非表示 */
.org-children > .org-subtree::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--org-line);
}
.org-children > .org-subtree:first-child::before { left: 50%; }
.org-children > .org-subtree:last-child::before  { right: 50%; }
.org-children > .org-subtree:only-child::before  { display: none; }

/* 横線バスから自ノードへの縦線 */
.org-children > .org-subtree::after {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 1px;
  height: 20px;
  background: var(--org-line);
}
</style>
