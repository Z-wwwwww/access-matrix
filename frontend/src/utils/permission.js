/**
 * Check if array has ALL specified values
 */
export function arrayHas(source, targets) {
  if (!Array.isArray(source) || !Array.isArray(targets)) return false
  return targets.every((t) => source.includes(t))
}

/**
 * Check if array has ANY of specified values
 */
export function arrayHasAny(source, targets) {
  if (!Array.isArray(source) || !Array.isArray(targets)) return false
  return targets.some((t) => source.includes(t))
}
