package com.platform.core.bootstrap.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scaffold's migration template must match the CURRENT schema conventions, not
 * the ones that were current when it was written.
 *
 * <p>The generator is the documented way to add a table (AGENTS.md § "Business code
 * recipe"), so anything stale in it is reintroduced by every new module — and nothing
 * downstream would notice, because Flyway happily creates whatever the file says.
 *
 * <p>Specifically: V58 converted every time column away from zone-less
 * {@code timestamp} — whose "meaning depended on the writing JVM's default zone" —
 * to {@code timestamptz}, and deliberately re-created the defaults as {@code now()}
 * rather than {@code CURRENT_TIMESTAMP} to avoid a session-dependent double cast.
 * The template still emitted {@code TIMESTAMP ... DEFAULT CURRENT_TIMESTAMP}, so a
 * scaffolded module got zone-less columns underneath {@code BaseEntity}'s
 * {@code OffsetDateTime} audit fields — exactly the state V58 existed to remove.
 */
class BusinessModuleScaffoldMigrationTest {

    private static final String SQL = BusinessModuleScaffold.renderMigration("widget", 1001);

    @Test
    void timeColumnsAreTimestamptzWithNowDefaults() {
        assertThat(SQL)
                .contains("create_time   TIMESTAMPTZ  NOT NULL DEFAULT now()")
                .contains("update_time   TIMESTAMPTZ  NOT NULL DEFAULT now()");
    }

    @Test
    void noZonelessTimestampColumnSurvives() {
        // Guard against a partial revert: TIMESTAMPTZ contains "TIMESTAMP", so match on
        // the column-declaration shape rather than the bare word.
        assertThat(SQL)
                .as("a zone-less timestamp column would undo V58 for this module")
                .doesNotContain("TIMESTAMP ")
                .doesNotContain("CURRENT_TIMESTAMP");
    }

    @Test
    void tenantIdAndSoftDeleteConventionsAreStillEmitted() {
        // The other conventions the guards depend on: TenantSchemaGuard fail-fasts on a
        // missing tenant_id, and @TableLogic needs mark.
        assertThat(SQL)
                .contains("tenant_id     VARCHAR(64)  NOT NULL")
                .contains("mark          SMALLINT     NOT NULL DEFAULT 1")
                .contains("id            CHAR(26)     NOT NULL PRIMARY KEY");
    }

    @Test
    void uniqueIndexLeadsWithTenantAndIsPartialOnMark() {
        // Matches every uk_* in the schema: partial on mark = 1 so a soft-deleted row
        // frees its code, and tenant-leading so two tenants can reuse a code.
        assertThat(SQL).contains("ON business_widget (tenant_id, code) WHERE mark = 1");
    }

    @Test
    void tableAndVersionAreWiredFromTheArguments() {
        assertThat(SQL)
                .contains("-- V1001__create_business_widget.sql")
                .contains("CREATE TABLE business_widget (");
    }
}
