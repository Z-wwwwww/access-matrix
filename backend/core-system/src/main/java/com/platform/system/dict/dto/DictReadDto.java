package com.platform.system.dict.dto;

import java.util.List;
import java.util.Map;

/**
 * Wire contract for {@code GET /dict/{code}} — the unified read shape consumed by
 * the frontend {@code useDict} composable, regardless of whether the source is a
 * built-in enum or the managed table.
 *
 * <p>Per item exactly one of {@code labelKey} / {@code labelI18n} is set:
 * <ul>
 *   <li>built-in → {@code labelKey} (frontend resolves via {@code t()}); {@code value} is a number</li>
 *   <li>managed  → {@code labelI18n} (frontend picks current locale); {@code value} is a string</li>
 * </ul>
 * {@code enabled=false} items are still returned (so historical rows resolve their
 * label) but the composable filters them out of dropdown options.
 */
public final class DictReadDto {

    private DictReadDto() {}

    public record ItemView(
            Object value,
            String labelKey,
            Map<String, String> labelI18n,
            String cssClass,
            Integer sort,
            boolean enabled) {}

    public record View(
            String code,
            boolean builtin,
            List<ItemView> items) {}
}
