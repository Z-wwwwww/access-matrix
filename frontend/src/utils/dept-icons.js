/**
 * 部署階層のアイコンチェーン：本社 → 拠点 → 業務単位 → チーム → 個別ユニット。
 *
 * level 0=Building2（多階建て・本社感）, 1=Building（拠点）, 2=Briefcase（業務単位）,
 * 3=Users（チーム）, 4+=User（個別ユニット）。
 *
 * 共有 util にすることで RoleEdit の部署 tab と OrgNode（DeptTreeDialog 配下）で
 * 同じ視覚言語を使う。
 */
import { Building2, Building, Briefcase, Users, User } from 'lucide-vue-next'

export const DEPT_LEVEL_ICONS = [Building2, Building, Briefcase, Users, User]

export function deptIconFor(level) {
  const safe = Math.min(Math.max(level || 0, 0), DEPT_LEVEL_ICONS.length - 1)
  return DEPT_LEVEL_ICONS[safe]
}
