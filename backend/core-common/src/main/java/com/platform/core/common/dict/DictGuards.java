package com.platform.core.common.dict;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delete-protection rules for dictionary items (form 1 / form 3 managed dicts).
 * A managed dict item is <b>not deletable</b> when either:
 * <ol>
 *   <li><b>it is a branch value</b> — the dict has an associated code enum and the
 *       value exists in it (i.e. backend code branches on it), or</li>
 *   <li><b>it is referenced</b> — some business column still holds the value.</li>
 * </ol>
 * Protection is <b>computed</b> (enum membership + live reference count), not a stored
 * flag, so it is always accurate. Each dict declares its rules in its module's
 * registrar; {@code DictAdminService.deleteItem} consults this before a hard delete.
 *
 * <p>There is no DB foreign key from business columns to dict items (values are
 * denormalised), so the reference set must be <em>declared</em> via {@link Guard#usedBy}.
 */
public final class DictGuards {

    /** A business column that holds this dict's values (table + column, both code-declared constants). */
    public record Usage(String table, String column) {}

    public static final class Guard {
        private Class<? extends DictEnum> branchEnum;
        private final List<Usage> usages = new ArrayList<>();

        /** The code enum whose values are code-branched (form 3) → those values can't be deleted. */
        public Guard branchEnum(Class<? extends DictEnum> e) {
            this.branchEnum = e;
            return this;
        }

        /** Declare a business column that references this dict → referenced values can't be deleted. */
        public Guard usedBy(String table, String column) {
            usages.add(new Usage(table, column));
            return this;
        }
    }

    private static final Map<String, Guard> GUARDS = new ConcurrentHashMap<>();

    private DictGuards() {}

    /** Register / fetch the guard for a dict code (idempotent — chain {@code branchEnum}/{@code usedBy}). */
    public static Guard register(String code) {
        return GUARDS.computeIfAbsent(code, k -> new Guard());
    }

    /** True if {@code value} is a code-defined (enum) value of this dict — i.e. code branches on it. */
    public static boolean isBranchValue(String code, String value) {
        Guard g = GUARDS.get(code);
        if (g == null || g.branchEnum == null || value == null) return false;
        int iv;
        try {
            iv = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        DictEnum[] constants = g.branchEnum.getEnumConstants();
        if (constants == null) return false;
        for (DictEnum c : constants) {
            if (c.code() == iv) return true;
        }
        return false;
    }

    /** Declared business columns that may reference this dict's values. */
    public static List<Usage> usages(String code) {
        Guard g = GUARDS.get(code);
        return g == null ? List.of() : List.copyOf(g.usages);
    }
}
