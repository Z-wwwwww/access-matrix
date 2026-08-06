import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import DataTable from '@/components/shared/DataTable/DataTable.vue'

/**
 * Changing the rows-per-page must not leave the caller stranded on a page that
 * no longer exists.
 *
 * Every list page wires the shared table the same way:
 *
 *   v-model:page="page"  v-model:page-size="pageSize"
 *   @update:page="fetchData"  @update:page-size="fetchData"
 *
 * — so a page-size change re-fetches with whatever `page` happened to be. On
 * page 3 of 3 (25 rows @ 10/page), switching to 20/page leaves page = 3 while
 * the data is only 2 pages long: the request goes out as page=3&size=20, the
 * backend pages past the end, and the user lands on an empty table with the
 * footer reading "3 / 2". The `page.value = 1` resets that exist on those pages
 * are wired to search/reset, not to the size selector.
 *
 * Resetting to page 1 is what this project already does for its one
 * hand-rolled pager (`Tenant.vue`: `watch([statusFilter, pageSize], () => {
 * page.value = 1; fetchData() })`); fixing it inside DataTable covers all
 * eight tables that use the shared component at once.
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
      total: 25,
      ...props
    },
    global: { plugins: [i18n] }
  })
}

/** The rows-per-page <Select> is the only Select the table renders. */
function pageSizeSelect(wrapper) {
  const select = wrapper.findAllComponents({ name: 'Select' })[0]
      ?? wrapper.findAllComponents({ __name: 'Select' })[0]
  return select ?? wrapper.findAllComponents({}).find((c) => 'options' in (c.props() ?? {}))
}

describe('DataTable rows-per-page', () => {
  it('resets to page 1 when the page size changes off a now-invalid page', async () => {
    const wrapper = mountTable({ page: 3, pageSize: 10 })

    await pageSizeSelect(wrapper).vm.$emit('update:modelValue', 20)

    expect(wrapper.emitted('update:pageSize')).toBeTruthy()
    expect(wrapper.emitted('update:pageSize')[0]).toEqual([20])
    expect(wrapper.emitted('update:page'), 'page must be reset so the refetch is not past the end')
      .toBeTruthy()
    expect(wrapper.emitted('update:page')[0]).toEqual([1])
  })

  it('resets to page 1 even when the new size still has that many pages', async () => {
    // 25 rows: page 3 exists at 10/page and page 3 also exists at 5/page. Still
    // reset — the row set under the cursor changes completely either way, and
    // "back to the top" is the behaviour every other pager in this app has.
    const wrapper = mountTable({ page: 3, pageSize: 10 })

    await pageSizeSelect(wrapper).vm.$emit('update:modelValue', 5)

    expect(wrapper.emitted('update:page')[0]).toEqual([1])
  })

  it('does not emit a redundant page reset when already on page 1', async () => {
    const wrapper = mountTable({ page: 1, pageSize: 10 })

    await pageSizeSelect(wrapper).vm.$emit('update:modelValue', 20)

    expect(wrapper.emitted('update:pageSize')[0]).toEqual([20])
    expect(wrapper.emitted('update:page')).toBeFalsy()
  })
})
