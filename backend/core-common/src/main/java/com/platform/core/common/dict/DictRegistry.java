package com.platform.core.common.dict;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link DictEnum}-backed <b>built-in dictionaries</b>.
 *
 * <p>Mirrors the {@code PermissionRegistry} pattern: each module force-loads a
 * {@code @Component} registrar at startup whose constructor calls
 * {@link #register(String, Class)} for its enums. The {@code GET /dict/{code}}
 * read path checks this registry first ({@link #isBuiltIn}); only when a code is
 * NOT built-in does it fall through to the managed {@code core_dict_item} table.
 *
 * <p>Built-in items have no per-tenant variation and cannot be disabled at
 * runtime (they are code), so each carries {@code value} + {@code labelKey} +
 * optional {@code cssClass} and is always enabled.
 */
public final class DictRegistry {

    /** One built-in option. {@code value} is the stored numeric code; label resolved client-side from {@code labelKey}. */
    public record Item(int value, String labelKey, String cssClass) {}

    private static final Map<String, List<Item>> BUILT_IN = new ConcurrentHashMap<>();

    private DictRegistry() {}

    /**
     * Register an enum as the built-in dictionary for {@code code}. Reads the enum
     * constants in declaration order. Idempotent-unfriendly on purpose: a duplicate
     * code is a wiring bug and fails fast at startup.
     */
    public static <E extends Enum<E> & DictEnum> void register(String code, Class<E> enumClass) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Built-in dict code must not be blank");
        }
        if (BUILT_IN.containsKey(code)) {
            throw new IllegalStateException("Duplicate built-in dict code: " + code);
        }
        List<Item> items = new ArrayList<>();
        for (E e : enumClass.getEnumConstants()) {
            items.add(new Item(e.code(), e.labelKey(), e.cssClass()));
        }
        BUILT_IN.put(code, List.copyOf(items));
    }

    public static boolean isBuiltIn(String code) {
        return BUILT_IN.containsKey(code);
    }

    /** Items for a built-in code, or null if not registered. */
    public static List<Item> items(String code) {
        return BUILT_IN.get(code);
    }

    public static Set<String> codes() {
        return Set.copyOf(BUILT_IN.keySet());
    }
}
