/**
 * 一級メニューの sort 値を RGB カラーへマッピング。
 * sort が小さいほど色相が小さく（赤寄り）、大きくなるほど色相が増える（黄→緑→青→紫）。
 * 360 を超えた場合は循環。彩度・明度は固定して読みやすさを担保。
 */
export function getMenuItemColor(sort) {
  const n = Number(sort) || 0
  const hue = ((n % 500) + 255) % 255
  return hslToRgb(hue, 65, 50)
}

function hslToRgb(h, s, l) {
  s /= 100
  l /= 100
  const k = (n) => (n + h / 30) % 12
  const a = s * Math.min(l, 1 - l)
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1))
  const r = Math.round(255 * f(0))
  const g = Math.round(255 * f(8))
  const b = Math.round(255 * f(4))
  return `rgb(${r}, ${g}, ${b})`
}
