package com.platform.core.bootstrap.migration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Mirrors legacy password-mode users from {@code core_auth_user} into a
 * Keycloak realm so they can sign in via SSO going forward. One-shot,
 * idempotent — the only mechanism we provide for the password→OIDC
 * transition that {@code OidcJitUserService}'s bind path was designed to
 * complete on first SSO login.
 *
 * <h3>What it does, per candidate user</h3>
 * <ol>
 *   <li>Skip if the row is missing username (defensive) or has no email
 *       — we can't reset-password-email a user with no inbox.</li>
 *   <li>If a KC user with the same username already lives in the target
 *       realm (e.g. partial earlier run), skip — idempotent re-runs are
 *       a feature, not a bug.</li>
 *   <li>{@code KeycloakUserService.createUser(realm, username, email,
 *       displayName, tempPassword=null)} — KC user exists, no credentials.</li>
 *   <li>{@code executeActionsEmail(["UPDATE_PASSWORD"])} — KC sends the user
 *       a "set your password" link; they finish enrollment without admin
 *       involvement.</li>
 * </ol>
 *
 * <h3>What it does NOT do</h3>
 * <ul>
 *   <li>Does NOT write {@code keycloak_id} back to {@code core_auth_user}.
 *       That binding happens naturally on the user's first SSO login via
 *       {@link OidcJitUserService}'s bind path (find by (tenant, username),
 *       write keycloak_id). Deferring it here keeps a clean abort path:
 *       if migration goes sideways the DB rows stay exactly as they were.</li>
 *   <li>Does NOT clear {@code password_hash}. Keeping it around lets a
 *       SUPER_ADMIN still break-glass into the system if SSO can't be made
 *       to work, AND lets an operator roll back to {@code mode=password}
 *       by editing one config line.</li>
 *   <li>Does NOT translate hashes. bcrypt cannot be converted to argon2id;
 *       users must reset their password once via the emailed link.</li>
 * </ul>
 *
 * <h3>Activation</h3>
 * Only active when {@code app.security.mode=oidc}; the bean simply isn't
 * registered otherwise (this matches the rest of the OIDC-conditional
 * infrastructure). Triggering the actual run goes through
 * {@link PasswordToSsoMigrationRunner}.
 */
@Service
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class PasswordToSsoMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordToSsoMigrationService.class);

    private final UserMapper userMapper;
    private final KeycloakUserService keycloak;

    public PasswordToSsoMigrationService(UserMapper userMapper, KeycloakUserService keycloak) {
        this.userMapper = userMapper;
        this.keycloak = keycloak;
    }

    /**
     * Mirror every unmigrated user from the given tenants into the matching
     * Keycloak realms. Tenant id == realm name by convention.
     */
    public MigrationReport run(List<String> tenantIds) {
        MigrationReport report = new MigrationReport();
        for (String tid : tenantIds) {
            if (tid == null || tid.isBlank()) continue;
            MigrationReport.TenantResult bucket = report.forTenant(tid);
            String traceId = "migration-" + UUID.randomUUID().toString().replace("-", "");
            // The MP tenant interceptor reads RequestContext.tenantId() to
            // inject WHERE tenant_id = ?. Set a synthetic context so the
            // candidate query lands on the right tenant. clear() in finally
            // so cross-tenant state doesn't leak.
            RequestContext.set(tid, "system-migration", "system-migration", Locale.JAPAN, traceId);
            try {
                migrateTenant(tid, bucket);
            } finally {
                RequestContext.clear();
            }
        }
        report.finishedAt = java.time.Instant.now();
        return report;
    }

    private void migrateTenant(String tenantId, MigrationReport.TenantResult bucket) {
        // Candidate = active row not yet linked to Keycloak. Password_hash
        // presence is irrelevant — what we care about is "DB user exists,
        // KC user doesn't (yet)". MP tenant interceptor adds
        // WHERE tenant_id = '<tenantId>' implicitly via RequestContext.
        List<UserEntity> candidates = userMapper.selectList(
                new QueryWrapper<UserEntity>()
                        .eq("mark", 1)
                        .isNull("keycloak_id")
                        .orderByAsc("create_time"));
        log.info("[migration] tenant={} found {} candidates", tenantId, candidates.size());

        for (UserEntity u : candidates) {
            if (u.getUsername() == null || u.getUsername().isBlank()) {
                bucket.skipped.add(skipped(u, "missing-username"));
                continue;
            }
            if (u.getEmail() == null || u.getEmail().isBlank()) {
                // No email = no way to deliver the reset-password link. The
                // operator must fill in the email before the user can finish
                // enrollment. We skip rather than fail so the rest of the
                // batch isn't blocked on a few row-quality issues.
                bucket.skipped.add(skipped(u, "missing-email"));
                continue;
            }
            // Idempotency: if username already exists in the realm, skip the
            // create. We deliberately do NOT re-trigger the reset email — a
            // user who already completed enrollment shouldn't get pestered
            // with another link on every re-run.
            String existingKcId = safeFindKcId(tenantId, u.getUsername());
            if (existingKcId != null) {
                MigrationReport.Skipped s = skipped(u, "kc-user-already-exists");
                bucket.skipped.add(s);
                continue;
            }
            String kcId;
            try {
                kcId = keycloak.createUser(tenantId, u.getUsername(), u.getEmail(),
                        u.getDisplayName(), /* tempPassword = */ null);
            } catch (Exception e) {
                bucket.failed.add(failed(u, "create-kc-user", e));
                continue;
            }
            boolean emailSent = true;
            try {
                keycloak.executeActionsEmail(tenantId, kcId, List.of("UPDATE_PASSWORD"));
            } catch (Exception e) {
                // The KC user is created but the email failed (typical cause:
                // realm SMTP not configured). Record as failed for visibility
                // but DON'T roll back the KC user — admin can resend manually
                // after fixing SMTP, and a re-run will find the existing user
                // and take the skip path.
                emailSent = false;
                bucket.failed.add(failed(u, "send-reset-email", e));
                continue;
            }
            MigrationReport.Created c = new MigrationReport.Created();
            c.userId = u.getId();
            c.username = u.getUsername();
            c.email = u.getEmail();
            c.keycloakId = kcId;
            c.emailSent = emailSent;
            bucket.created.add(c);
        }
        log.info("[migration] tenant={} created={} skipped={} failed={}",
                tenantId, bucket.created.size(), bucket.skipped.size(), bucket.failed.size());
    }

    /**
     * Re-send the {@code UPDATE_PASSWORD} required-action email to every
     * user who has been mirrored into Keycloak but hasn't completed
     * enrollment yet — i.e. {@code core_auth_user.keycloak_id IS NULL}
     * AND a KC user with the same username already exists in the realm.
     *
     * <p>Why this is a separate entry point from {@link #run}:
     * <ul>
     *   <li>Keycloak's reset-credentials link expires in 12 h by default
     *       ({@code actionTokenGeneratedByAdminLifespan} on the realm).
     *       Users who don't click in time can't recover on their own —
     *       admin has to re-trigger.</li>
     *   <li>{@code run} is idempotent by design: it SKIPS users that
     *       already have KC accounts. That's the right call to avoid
     *       spamming people on every restart, but it means {@code run}
     *       alone can't help expired-link victims.</li>
     *   <li>{@code resend} is the explicit opposite: it ONLY touches
     *       users with KC accounts that haven't bound yet. Users whose
     *       link is still valid get a duplicate email — KC handles that
     *       gracefully by invalidating the old token in favor of the new.</li>
     * </ul>
     *
     * <p>Safety properties (same as {@link #run}):
     * <ul>
     *   <li>Idempotent at the bind level: once a user logs in via SSO
     *       and gets their {@code keycloak_id} written, this method
     *       skips them forever — the candidate query already filters
     *       on {@code keycloak_id IS NULL}.</li>
     *   <li>Does NOT recreate KC users / does NOT touch passwords / does
     *       NOT alter DB rows. Pure side-effect: one email per candidate.</li>
     *   <li>If the row has no KC user yet (e.g. it was created
     *       post-migration via the admin UI in a separate broken flow),
     *       lands in the {@code skipped} bucket so the operator can
     *       investigate; we do NOT silently create the missing user
     *       here — that's the {@link #run} entry point's job.</li>
     * </ul>
     */
    public MigrationReport resend(List<String> tenantIds) {
        MigrationReport report = new MigrationReport();
        for (String tid : tenantIds) {
            if (tid == null || tid.isBlank()) continue;
            MigrationReport.TenantResult bucket = report.forTenant(tid);
            String traceId = "resend-" + UUID.randomUUID().toString().replace("-", "");
            RequestContext.set(tid, "system-migration", "system-migration", Locale.JAPAN, traceId);
            try {
                resendTenant(tid, bucket);
            } finally {
                RequestContext.clear();
            }
        }
        report.finishedAt = java.time.Instant.now();
        return report;
    }

    private void resendTenant(String tenantId, MigrationReport.TenantResult bucket) {
        List<UserEntity> candidates = userMapper.selectList(
                new QueryWrapper<UserEntity>()
                        .eq("mark", 1)
                        .isNull("keycloak_id")
                        .orderByAsc("create_time"));
        log.info("[migration:resend] tenant={} found {} candidates (unbound users)", tenantId, candidates.size());

        for (UserEntity u : candidates) {
            if (u.getUsername() == null || u.getUsername().isBlank()) {
                bucket.skipped.add(skipped(u, "missing-username"));
                continue;
            }
            String kcId;
            try {
                kcId = keycloak.findUserIdByUsername(tenantId, u.getUsername());
            } catch (Exception e) {
                bucket.failed.add(failed(u, "lookup-kc-user", e));
                continue;
            }
            if (kcId == null) {
                // The DB row has no matching KC user — likely never had
                // run() applied to it, or someone deleted the KC user.
                // Don't paper over by silently creating it here; that's
                // run()'s job. Surface this as a skip so the operator
                // can decide whether to follow up with run().
                bucket.skipped.add(skipped(u, "no-kc-user-yet-run-migration-first"));
                continue;
            }
            try {
                keycloak.executeActionsEmail(tenantId, kcId, List.of("UPDATE_PASSWORD"));
            } catch (Exception e) {
                bucket.failed.add(failed(u, "send-reset-email", e));
                continue;
            }
            MigrationReport.Created c = new MigrationReport.Created();
            c.userId = u.getId();
            c.username = u.getUsername();
            c.email = u.getEmail();
            c.keycloakId = kcId;
            c.emailSent = true;
            bucket.created.add(c);  // "created" bucket reused for "email re-issued" tally
        }
        log.info("[migration:resend] tenant={} emails-sent={} skipped={} failed={}",
                tenantId, bucket.created.size(), bucket.skipped.size(), bucket.failed.size());
    }

    private String safeFindKcId(String realm, String username) {
        try {
            return keycloak.findUserIdByUsername(realm, username);
        } catch (Exception e) {
            // Treat "can't even ask KC" as "not present", let createUser
            // try its own attempt. If KC really is down, createUser will
            // also throw and we'll record it as failed.
            log.warn("[migration] lookup failed for {}:{} ({}), proceeding to create", realm, username, e.toString());
            return null;
        }
    }

    private static MigrationReport.Skipped skipped(UserEntity u, String reason) {
        MigrationReport.Skipped s = new MigrationReport.Skipped();
        s.userId = u.getId();
        s.username = u.getUsername();
        s.reason = reason;
        return s;
    }

    private static MigrationReport.Failed failed(UserEntity u, String stage, Exception e) {
        MigrationReport.Failed f = new MigrationReport.Failed();
        f.userId = u.getId();
        f.username = u.getUsername();
        f.stage = stage;
        f.errorMessage = e.toString();
        return f;
    }
}
