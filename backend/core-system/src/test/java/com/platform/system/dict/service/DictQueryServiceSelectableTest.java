package com.platform.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.system.dict.entity.DictItemEntity;
import com.platform.system.dict.mapper.DictItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A retired (status=0) managed dict item stays a LEGAL value but stops being a
 * SELECTABLE one.
 *
 * <p>Why both are needed: {@code DictAdminService.deleteItem} refuses to hard-delete
 * any value the code branches on or that business data still references, and points
 * the operator at "disable it (status=0) instead so historical rows keep resolving
 * their label" — so status=0 IS the retirement mechanism for a managed dict. The read
 * API ships a per-item {@code enabled} flag exactly so the two uses can diverge, and
 * {@code useDict} does diverge ({@code items} = all, for labels; {@code options} =
 * enabled only, for {@code <Select>}). Before {@code isSelectableValue} existed the
 * server had no enabled-aware validator, so retiring an option changed nothing
 * server-side — only fresh UI stopped offering it.
 */
@ExtendWith(MockitoExtension.class)
class DictQueryServiceSelectableTest {

    @Mock DictItemMapper itemMapper;

    private DictQueryService service;

    private static DictItemEntity item(String value, int status) {
        DictItemEntity it = new DictItemEntity();
        it.setDictCode("task_priority");
        it.setItemValue(value);
        it.setStatus(status);
        it.setSortNo(0);
        return it;
    }

    @BeforeEach
    void setUp() {
        service = new DictQueryService(itemMapper,
                new DictJsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()));
        // lenient: the built-in branch never touches the DB (asserted there).
        org.mockito.Mockito.lenient().when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                item("1", 1),    // live
                item("2", 1),    // live
                item("3", 0)));  // retired
    }

    @Test
    void retiredItemIsStillReturnedByTheRead_butFlaggedDisabled() {
        // Labels of historical rows depend on the item still being in the payload.
        var view = service.read("task_priority");

        assertThat(view.items()).hasSize(3);
        assertThat(view.items().stream().filter(i -> "3".equals(i.value())).findFirst())
                .get()
                .satisfies(i -> assertThat(i.enabled()).isFalse());
    }

    @Test
    void isValidValue_acceptsARetiredValue() {
        // The weaker question — "is this a legal value of the dict at all" — must
        // keep saying yes, or old rows can't be read back or carried forward.
        assertThat(service.isValidValue("task_priority", 3)).isTrue();
        assertThat(service.isValidValue("task_priority", 1)).isTrue();
    }

    @Test
    void isSelectableValue_refusesARetiredValue() {
        assertThat(service.isSelectableValue("task_priority", 3)).isFalse();
        assertThat(service.isSelectableValue("task_priority", 1)).isTrue();
        assertThat(service.isSelectableValue("task_priority", 2)).isTrue();
    }

    @Test
    void bothRefuseUnknownAndNull() {
        assertThat(service.isValidValue("task_priority", 99)).isFalse();
        assertThat(service.isSelectableValue("task_priority", 99)).isFalse();
        assertThat(service.isValidValue("task_priority", null)).isFalse();
        assertThat(service.isSelectableValue("task_priority", null)).isFalse();
    }

    @Test
    void builtInDictItemsAreAlwaysSelectable() {
        // Form-2 (DictRegistry enum) dicts have no DB row and no status column —
        // load() hardcodes enabled=true, so the new filter must not reject them.
        // Register under a code unique to this test: DictRegistry.register fails
        // fast on a duplicate, by design.
        com.platform.core.common.dict.DictRegistry.register(
                BUILT_IN_CODE, com.platform.core.common.dict.CommonStatus.class);

        var view = service.read(BUILT_IN_CODE);

        assertThat(view.builtin()).isTrue();
        assertThat(view.items()).isNotEmpty()
                .allSatisfy(i -> assertThat(i.enabled()).isTrue());
        assertThat(service.isSelectableValue(BUILT_IN_CODE, view.items().get(0).value())).isTrue();
        assertThat(service.isSelectableValue(BUILT_IN_CODE, 99)).isFalse();
        // Built-in dicts are code, not data — the DB must not be consulted at all.
        org.mockito.Mockito.verify(itemMapper, org.mockito.Mockito.never()).selectList(any(Wrapper.class));
    }

    private static final String BUILT_IN_CODE = "selectable_test_builtin";
}
