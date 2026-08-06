import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import DataTable from '@/components/shared/DataTable/DataTable.vue'

/**
 * The other way a caller ends up stranded past the last page.
 *
 * `changePageSize` already covers one trigger, and its comment names the symptom
 * exactly: "後端翻过了末页 —— 用户看到空表格，页脚还写着「3 / 2」". The same state is
 * reachable without touching the size selector: delete the only row on the last
 * page. Every list page's delete handler ends in a bare `fetchData()`, which
 * refetches with `page` unchanged, and `page.value = 1` on those pages is wired
 * to search/reset only. The backend does not rescue it either —
 * `MybatisPlusConfig` sets `pagination.setOverflow(false)`, so an out-of-range
 * page returns an empty record list rather than the last page.
 *
 * Result: total drops from 41 to 40, `totalPages` becomes 2, `page` stays 3, and
 * the user is looking at an empty table whose footer says 3 / 2. The "next"
 * button is disabled (page >= totalPages) so the only way out is clicking back.
 *
 * Clamping inside DataTable fixes every list page at once, the same reasoning
 * the page-size reset used.
 */
const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: {
    en: {
      dataTable: {
        emptyState: 'No data',
        loading: 'Loading',
        pagination: { perPage: '{n} / page', total: 'total {total}' }
      }
    }
  },
  missingWarn: false,
  fallbackWarn: false
})

function mountTable(props) {
  return mount(DataTable, {
    props: {
      columns: [{ key: 'name', title: 'Name' }],
      data: [{ id: 'a', name: 'A' }],
      ...props
    },
    global: { plugins: [i18n] }
  })
}

describe('DataTable — page stranded past the end when total shrinks', () => {
  it('clamps to the new last page when the row count drops', async () => {
    const wrapper = mountTable({ page: 3, pageSize: 20, total: 41 })
    expect(wrapper.emitted('update:page')).toBeFalsy()   // page 3 is valid at 41 rows

    await wrapper.setProps({ total: 40 })                // the last row was deleted

    expect(wrapper.emitted('update:page'), 'must not leave the caller on page 3 of 2')
      .toBeTruthy()
    expect(wrapper.emitted('update:page').at(-1)).toEqual([2])
  })

  it('falls back to page 1 when everything is gone', async () => {
    const wrapper = mountTable({ page: 3, pageSize: 20, total: 41 })

    await wrapper.setProps({ total: 0 })

    expect(wrapper.emitted('update:page').at(-1)).toEqual([1])
  })

  it('stays quiet while the first load is still in flight', async () => {
    // total starts at 0 on every page before the first response lands. Page 1 is
    // already the clamp target, so nothing may be emitted — an extra emit here
    // would trigger a second fetchData on every mount.
    const wrapper = mountTable({ page: 1, pageSize: 20, total: 0 })
    await wrapper.setProps({ total: 40 })

    expect(wrapper.emitted('update:page')).toBeFalsy()
  })

  it('stays quiet when the total shrinks but the page is still valid', async () => {
    const wrapper = mountTable({ page: 2, pageSize: 20, total: 100 })

    await wrapper.setProps({ total: 40 })   // still 2 pages — page 2 is fine

    expect(wrapper.emitted('update:page')).toBeFalsy()
  })
})
