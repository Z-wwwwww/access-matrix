package com.platform.system.dict.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Counts how many business rows reference a dict value, for delete-protection
 * ({@link com.platform.system.dict.service.DictAdminService}). {@code table} /
 * {@code column} are code-declared constants from {@code DictGuards} (never user
 * input) so the {@code ${}} identifier substitution is injection-safe; the value
 * is parameterised.
 *
 * <p>Intentionally has NO {@code tenant_id} predicate (cf. Hard Rule 5): deleting a
 * GLOBAL dict value is a platform-ops action, and the question is "does ANY tenant
 * still use this value?" — a deliberate cross-tenant integrity check.
 */
public interface DictUsageMapper {

    @Select("SELECT count(*) FROM ${table} WHERE ${column} = #{value} AND mark = 1")
    long countUsage(@Param("table") String table, @Param("column") String column, @Param("value") String value);
}
