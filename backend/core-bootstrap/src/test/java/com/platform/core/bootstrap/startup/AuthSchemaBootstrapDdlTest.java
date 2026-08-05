package com.platform.core.bootstrap.startup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthSchemaBootstrap} is the {@code IF NOT EXISTS} safety net for a dirty
 * {@code flyway_schema_history}. On a healthy database every statement in it is a
 * no-op — which is precisely why drift there stays invisible until the one moment
 * it matters, and why nothing corrects it afterwards: when it fires, the
 * migrations that would have shaped the table are already recorded as applied.
 *
 * <p>Two drifts had accumulated, both confirmed against the live schema:
 * <ul>
 *   <li>21 audit/time columns declared {@code TIMESTAMP ... DEFAULT
 *       CURRENT_TIMESTAMP} while V58 had converted every one of them to
 *       {@code timestamptz} / {@code now()} — the exact state V58 exists to
 *       eliminate, and the same drift the scaffold template had
 *       ({@code BusinessModuleScaffoldMigrationTest}).</li>
 *   <li>{@code core_numbering_management} still declared the pre-V31 global shape
 *       (no {@code tenant_id}, PK on {@code code_kbn}) while the real table has
 *       {@code tenant_id} NOT NULL and PK {@code (tenant_id, code_kbn)}.</li>
 * </ul>
 *
 * <p>Source-level assertions rather than a live-DB test: the point is to fail the
 * build the moment someone reshapes a table in a migration without updating this
 * net, and that has to hold with no database around.
 */
class AuthSchemaBootstrapDdlTest {

    private static String source() throws IOException {
        // Walk up to the repo root so the test works from any module working dir.
        Path dir = Path.of("").toAbsolutePath();
        Path rel = Path.of("core-bootstrap/src/main/java/com/platform/core/bootstrap/"
                + "startup/AuthSchemaBootstrap.java");
        for (Path p = dir; p != null; p = p.getParent()) {
            Path candidate = p.resolve(rel);
            if (Files.exists(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("AuthSchemaBootstrap.java not found from " + dir);
    }

    /** DDL body only — the javadoc legitimately mentions the old spellings. */
    private static String ddl() throws IOException {
        String s = source();
        int start = s.indexOf("public void ensureSchema()");
        assertThat(start).as("ensureSchema() not found").isGreaterThan(0);
        return s.substring(start);
    }

    @Test
    void declares_no_timezone_less_timestamp_column() throws IOException {
        // TIMESTAMP not followed by TZ. V58 converted every audit column to
        // timestamptz because a bare `timestamp` means whatever the writing JVM's
        // zone happened to be — and BaseEntity models these as OffsetDateTime.
        Matcher m = Pattern.compile("\\bTIMESTAMP\\b(?!TZ)").matcher(ddl());
        assertThat(m.find())
                .as("AuthSchemaBootstrap still declares a timezone-less TIMESTAMP column")
                .isFalse();
    }

    @Test
    void uses_now_not_current_timestamp_as_the_time_default() throws IOException {
        // V58 deliberately rebuilt the defaults as now() rather than
        // CURRENT_TIMESTAMP to avoid PostgreSQL wrapping the old default in a
        // session-dependent double cast.
        assertThat(ddl())
                .as("CURRENT_TIMESTAMP default reintroduces the drift V58 removed")
                .doesNotContain("CURRENT_TIMESTAMP");
    }

    @Test
    void every_time_column_default_is_now() throws IOException {
        // Column DECLARATIONS only — require the "NOT NULL" that follows the type,
        // so the CREATE INDEX statements naming the same columns don't match.
        Matcher m = Pattern.compile("(create_time|update_time|login_time)\\s+\\w+\\s+NOT NULL[^,\\n]*")
                .matcher(ddl());
        int seen = 0;
        while (m.find()) {
            seen++;
            assertThat(m.group(0))
                    .as("column '%s' must be TIMESTAMPTZ ... DEFAULT now()", m.group(1))
                    .contains("TIMESTAMPTZ")
                    .contains("now()");
        }
        assertThat(seen).as("expected the audit time columns to be found").isGreaterThanOrEqualTo(20);
    }

    @Test
    void numbering_management_is_tenant_scoped_matching_V31() throws IOException {
        String ddl = ddl();
        int start = ddl.indexOf("CREATE TABLE IF NOT EXISTS core_numbering_management");
        assertThat(start).as("core_numbering_management DDL not found").isGreaterThan(0);
        String table = ddl.substring(start, ddl.indexOf("\"\"\"", start));

        assertThat(table)
                .as("V31 gave this table a tenant_id; the net still created the global shape")
                .contains("tenant_id");
        assertThat(table)
                .as("V31 made the PK (tenant_id, code_kbn)")
                .contains("PRIMARY KEY (tenant_id, code_kbn)");
        // The old global shape declared the PK inline on code_kbn.
        assertThat(table).doesNotContain("code_kbn VARCHAR(64) PRIMARY KEY");
        // Match V31's constraint name so a from-scratch build is indistinguishable
        // from a Flyway-built one.
        assertThat(table).contains("CONSTRAINT core_numbering_management_pkey");
    }

    @Test
    void numbering_key_stays_tenant_scoped() throws IOException {
        String ddl = ddl();
        int start = ddl.indexOf("CREATE TABLE IF NOT EXISTS core_numbering_key");
        String table = ddl.substring(start, ddl.indexOf("\"\"\"", start));

        assertThat(table).contains("PRIMARY KEY (tenant_id, code_kbn, numbering_key)");
    }
}
