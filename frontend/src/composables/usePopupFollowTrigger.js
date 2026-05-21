import { watch, onBeforeUnmount, nextTick } from 'vue'

/**
 * Teleport-to-body な popup を `position: absolute` + document 座標で配置する。
 *
 * antd (rc-align) / Floating Ui と同方式:
 *   - viewport 座標 (`position: fixed`) ではなく document 座標で書くと、
 *     ページの window scroll はブラウザがネイティブで popup と trigger を一緒に動かす。
 *     → JS 介入ゼロ、再計算ゼロ、遅延ゼロ。
 *   - 内部スクロール container 用に listener も残しているが、書く値は同一になるので no-op。
 *
 * Vue reactive (`panelStyle.value = {...}`) は microtask 1 フレーム遅延が出るため、
 * **DOM の style プロパティを直書き** して同期反映させる。
 *
 * @param {HTMLElement} panelEl - 配置対象 (popup の root 要素)
 * @param {DOMRect} triggerRect - trigger.getBoundingClientRect()
 * @param {object} [opts]
 * @param {number} [opts.gap=4] - trigger と panel の間隔 px
 * @param {number} [opts.margin=8] - viewport 端からの最小マージン
 * @param {number} [opts.assumedHeight] - 開く方向判定用の想定高さ (実測 offsetHeight より優先)
 * @param {Record<string,string>} [opts.extraStyle] - width 等の追加 style (DOM に直書き)
 */
// viewport 顶部覆盖指定 x 座標の sticky/fixed 要素のうち最も下端
// (AppHeader / AppTabBar 等の sticky bar が popup を遮蔽するのを避けるための上方安全境界)
//
// 注: Drawer/Dialog の overlay (fixed inset-0) も elementsFromPoint で拾われるが、
//     これらは「顶栏」ではなく全画面 mask なので除外。
//     ヒューリスティック: 顶栏は通常 bottom < 200px に収まる。それより大きい fixed/sticky 要素は overlay とみなしてスキップ。
const TOP_BAR_MAX_BOTTOM = 200
function getTopSafeOffset(x) {
  const clampedX = Math.max(0, Math.min(window.innerWidth - 1, x))
  const els = document.elementsFromPoint(clampedX, 1)
  let maxBottom = 0
  for (const el of els) {
    const pos = getComputedStyle(el).position
    if (pos === 'sticky' || pos === 'fixed') {
      const r = el.getBoundingClientRect()
      // 全画面 overlay は除外
      if (r.bottom >= TOP_BAR_MAX_BOTTOM) continue
      if (r.bottom > maxBottom) maxBottom = r.bottom
    }
  }
  return maxBottom
}

// trigger の最近のスクロール可能祖先の底端を「下方限界」として返す。
// (Drawer/Dialog 内容区等の overflow:auto コンテナ内で開く popup が、
//   コンテナ下のフッターボタン等を覆わないようにするための下方境界)
// 該当祖先が無いか viewport より大きい場合は viewport 底端。
function getBottomSafeOffset(triggerEl) {
  if (!triggerEl) return window.innerHeight
  let node = triggerEl.parentElement
  while (node && node !== document.body && node !== document.documentElement) {
    const style = getComputedStyle(node)
    if (/(auto|scroll|overlay)/.test(style.overflowX + style.overflowY + style.overflow)) {
      const r = node.getBoundingClientRect()
      if (r.bottom < window.innerHeight) return r.bottom
    }
    node = node.parentElement
  }
  return window.innerHeight
}

export function applyAbsolutePopupPosition(panelEl, triggerRect, opts = {}) {
  if (!panelEl || !triggerRect) return
  const { gap = 4, margin = 8, assumedHeight, extraStyle, zIndex, triggerEl } = opts
  const panelH = assumedHeight || panelEl.offsetHeight || 360
  const panelW = panelEl.offsetWidth || 224

  const scrollX = window.scrollX || window.pageXOffset || 0
  const scrollY = window.scrollY || window.pageYOffset || 0

  // 縦方向: 下方限界 (viewport or スクロールコンテナ底) - trigger 下端 = 下方空間
  //         上方限界 (sticky header の下端) - trigger 上端 = 上方空間
  const safeBottom = getBottomSafeOffset(triggerEl)
  const spaceBelow = safeBottom - triggerRect.bottom
  const safeTop = getTopSafeOffset(triggerRect.left)
  const effectiveSpaceAbove = triggerRect.top - safeTop

  // tolerance: 完全には収まらないが数 px 足りないだけならその方向採用 (側面に飛ばすほどじゃない)
  const TOL = 16
  const fitsBelow = spaceBelow >= panelH - TOL
  const fitsAbove = effectiveSpaceAbove >= panelH - TOL

  const minLeft = scrollX + margin
  const maxLeft = scrollX + window.innerWidth - panelW - margin
  let top, left

  if (fitsBelow) {
    // 下方展開 (最優先)
    top = triggerRect.bottom + scrollY + gap
    left = (triggerRect.left + panelW + margin > window.innerWidth)
      ? triggerRect.right + scrollX - panelW
      : triggerRect.left + scrollX
  } else if (fitsAbove) {
    // 上方展開
    top = triggerRect.top + scrollY - panelH - gap
    left = (triggerRect.left + panelW + margin > window.innerWidth)
      ? triggerRect.right + scrollX - panelW
      : triggerRect.left + scrollX
  } else {
    // 上下とも収まらない → 横展開 (右優先、駄目なら左)
    const spaceRight = window.innerWidth - triggerRect.right - margin
    const spaceLeft = triggerRect.left - margin
    if (spaceRight >= panelW) {
      left = triggerRect.right + scrollX + gap
    } else if (spaceLeft >= panelW) {
      left = triggerRect.left + scrollX - panelW - gap
    } else {
      // どちら側も狭い: 広い方に置いて clamp
      left = spaceRight >= spaceLeft
        ? triggerRect.right + scrollX + gap
        : triggerRect.left + scrollX - panelW - gap
    }
    // 縦方向は trigger の top に合わせ、画面内に収まるよう clamp
    const desiredTop = triggerRect.top + scrollY
    const minTop = scrollY + safeTop + margin
    const maxTop = scrollY + safeBottom - panelH - margin
    top = Math.max(minTop, Math.min(maxTop, desiredTop))
  }

  // viewport 内に clamp (どの方向でも最終チェック)
  left = Math.max(minLeft, Math.min(maxLeft, left))

  // Vue reactive 経由しない直書き = 微妙な遅延ゼロ
  panelEl.style.position = 'absolute'
  panelEl.style.top = top + 'px'
  panelEl.style.left = left + 'px'
  panelEl.style.right = ''
  panelEl.style.bottom = ''
  // zIndex が指定されていれば class の z-[60] を inline で上書き (Drawer/Dialog 内の場合等)
  if (zIndex != null) {
    panelEl.style.zIndex = String(zIndex)
  }
  if (extraStyle) {
    for (const k in extraStyle) {
      panelEl.style[k] = extraStyle[k]
    }
  }
}

/**
 * Teleport-to-body 式の popup で、開いている間 trigger の位置に追従させる。
 *
 * 設計 (antd / Floating UI autoUpdate と同方式、window scroll capture は使わない):
 * 1. trigger から祖先を遡って overflow:auto/scroll な要素だけを列挙し、
 *    **それらと window** にのみ passive scroll listener を貼る。
 *    → 関係ない別領域の scroll は完全無視 (capture:true で全 scroll 拾う前案より遥かに軽い)
 * 2. ResizeObserver で trigger 自身のサイズ変化 (フォーム再描画・textarea 伸縮等) も検知
 * 3. scroll/resize/resize-observer 通知は **requestAnimationFrame で合流**し、
 *    1 フレームあたり updatePosition は最大 1 回 → 最大 60fps reflow
 *
 * 使用:
 * ```js
 * const open = ref(false)
 * const triggerRef = ref(null)
 * function updatePosition() { ... triggerRef.value.getBoundingClientRect() ... }
 *
 * usePopupFollowTrigger(open, triggerRef, updatePosition)
 * ```
 *
 * @param {import('vue').Ref<boolean>} openRef
 * @param {import('vue').Ref<HTMLElement|any>} triggerRef - DOM 要素 or Vue component ref (.$el で解決)
 * @param {() => void} updatePosition
 */
export function usePopupFollowTrigger(openRef, triggerRef, updatePosition) {
  let cleanup = null
  let rafId = 0

  function schedule() {
    if (rafId) return
    rafId = requestAnimationFrame(() => {
      rafId = 0
      updatePosition()
    })
  }

  function isScrollable(el) {
    const s = getComputedStyle(el)
    // overflow のいずれかが auto / scroll であれば scroll parent とみなす
    return /(auto|scroll|overlay)/.test(s.overflowX + s.overflowY + s.overflow)
  }

  function getScrollParents(el) {
    const parents = []
    let node = el.parentElement
    while (node && node !== document.body && node !== document.documentElement) {
      if (isScrollable(node)) parents.push(node)
      node = node.parentElement
    }
    return parents
  }

  function attach() {
    const trigger = triggerRef.value?.$el || triggerRef.value
    if (!trigger) return

    const unbinds = []
    const scrollParents = getScrollParents(trigger)

    // 1. 実際にスクロール可能な祖先にだけ scroll 購読
    for (const p of scrollParents) {
      p.addEventListener('scroll', schedule, { passive: true })
      unbinds.push(() => p.removeEventListener('scroll', schedule))
    }
    // 2. window (document 全体のスクロール) + リサイズ
    window.addEventListener('scroll', schedule, { passive: true })
    window.addEventListener('resize', schedule)
    unbinds.push(() => {
      window.removeEventListener('scroll', schedule)
      window.removeEventListener('resize', schedule)
    })

    // 3. trigger 自身のサイズ変化を追跡 (周辺 layout 変更で trigger 位置も動く)
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(schedule)
      ro.observe(trigger)
      unbinds.push(() => ro.disconnect())
    }

    cleanup = () => {
      if (rafId) { cancelAnimationFrame(rafId); rafId = 0 }
      for (const fn of unbinds) fn()
    }
  }

  function detach() {
    if (cleanup) { cleanup(); cleanup = null }
  }

  watch(openRef, (val) => {
    if (val) nextTick(attach)
    else detach()
  })

  onBeforeUnmount(detach)
}
