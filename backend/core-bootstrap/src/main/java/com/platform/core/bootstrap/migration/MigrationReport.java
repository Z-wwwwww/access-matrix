package com.platform.core.bootstrap.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate result of a {@code PasswordToSsoMigrationService.run()} invocation.
 *
 * <p>Three buckets, mutually exclusive:
 * <ul>
 *   <li><b>created</b> — DB user successfully mirrored into Keycloak (KC user
 *       provisioned + reset-password email triggered).</li>
 *   <li><b>skipped</b> — already in Keycloak (idempotent re-run) or row
 *       missing essential data (no username / no email).</li>
 *   <li><b>failed</b> — Keycloak Admin API call threw; row left untouched
 *       so the operator can retry the same migration safely.</li>
 * </ul>
 *
 * <p>Serialized to {@code logs/migration-password-to-sso-&lt;timestamp&gt;.json}
 * by the runner so post-mortem investigation has a stable artefact (the
 * console log is shared with the rest of the app's boot output).
 */
public class MigrationReport {

    public Instant startedAt = Instant.now();
    public Instant finishedAt;
    public final List<TenantResult> tenants = new ArrayList<>();

    public TenantResult forTenant(String tenantId) {
        TenantResult t = new TenantResult();
        t.tenantId = tenantId;
        tenants.add(t);
        return t;
    }

    public int totalCreated() { return tenants.stream().mapToInt(t -> t.created.size()).sum(); }
    public int totalSkipped() { return tenants.stream().mapToInt(t -> t.skipped.size()).sum(); }
    public int totalFailed()  { return tenants.stream().mapToInt(t -> t.failed.size()).sum(); }

    public static class TenantResult {
        public String tenantId;
        public final List<Created> created = new ArrayList<>();
        public final List<Skipped> skipped = new ArrayList<>();
        public final List<Failed>  failed  = new ArrayList<>();
    }

    public static class Created {
        public String userId;
        public String username;
        public String email;
        public String keycloakId;
        public boolean emailSent;
    }

    public static class Skipped {
        public String userId;
        public String username;
        public String reason;
    }

    public static class Failed {
        public String userId;
        public String username;
        public String stage;        // create-kc-user | send-reset-email
        public String errorMessage;
    }
}
