package com.platform.core.infrastructure.numbering;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Allocates human-visible reference numbers (e.g. {@code U00000001}) backed by
 * {@code core_numbering_management} and {@code core_numbering_key}.
 *
 * <h3>Tenant scoping</h3>
 * <p>Both backing tables are per-tenant:
 * <ul>
 *   <li>{@code core_numbering_management} (def + counter, PK
 *       {@code (tenant_id, code_kbn)}) — used by the {@code fixedKey == ""}
 *       path. Every tenant has its own counter so e.g. {@code U00000001} is
 *       independently the first user of demo, acme, …</li>
 *   <li>{@code core_numbering_key} (counter only, PK
 *       {@code (tenant_id, code_kbn, numbering_key)}) — used by the
 *       {@code fixedKey != ""} path for finer-grained sub-buckets within
 *       a tenant (e.g. one counter per branch office).</li>
 * </ul>
 *
 * <p>tenantId must be passed by the caller — never resolved internally.
 * The {@link #requireTenant} check rejects null/blank at every public method
 * to keep the per-tenant guarantee from being undermined by a forgetful caller.
 *
 * <h3>Optimisations vs the original implementation</h3>
 * <ul>
 *   <li>{@link NumberingDef} (format / digit / recycle / min-max-step) cached in
 *       Caffeine for 10 minutes &mdash; the volatile {@code seq_id} stays in DB
 *       and is mutated atomically per call. Cache key is
 *       {@code (tenantId, codeKbn)} so per-tenant overrides Just Work.</li>
 *   <li>Counter increment uses {@code UPDATE ... RETURNING seq_id} in a single
 *       statement (no more {@code SELECT FOR UPDATE} + {@code UPDATE} round-trip).</li>
 *   <li>{@code nextBatch} / {@code collectBatch} allocate {@code count} numbers
 *       in one DB hit, cutting bulk-import latency by roughly N times.</li>
 * </ul>
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li>Each allocation runs in its own transaction ({@code REQUIRES_NEW}).</li>
 *   <li>For {@code count == 1}, exceeding {@code max_value} wraps back to
 *       {@code min_value}. For batch ({@code count > 1}) it is an error &mdash;
 *       refusing to wrap mid-batch.</li>
 *   <li>The first allocation against a fresh {@code (tenant, codeKbn, key)}
 *       starts at {@code min_value}.</li>
 * </ul>
 */
@Service
public class NumberingService {

    /** Source tenant for {@link #seedDefaultsForTenant} — the reference business tenant. */
    private static final String TEMPLATE_TENANT = "demo";

    private final JdbcTemplate jdbc;
    private final LoadingCache<DefCacheKey, NumberingDef> defCache;

    public NumberingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.defCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build(key -> loadDef(key.tenantId(), key.codeKbn()));
    }

    /** Composite key for the def cache — codeKbn alone is no longer unique. */
    private record DefCacheKey(String tenantId, String codeKbn) {}

    // ---------- public API ----------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String codeKbn, String tenantId) {
        return doAllocate(codeKbn, "", requireTenant(tenantId), 1).get(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String collect(String codeKbn, String fixedKey, String tenantId) {
        return doAllocate(codeKbn, fixedKey, requireTenant(tenantId), 1).get(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> nextBatch(String codeKbn, String tenantId, int count) {
        return doAllocate(codeKbn, "", requireTenant(tenantId), count);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> collectBatch(String codeKbn, String fixedKey, String tenantId, int count) {
        return doAllocate(codeKbn, fixedKey, requireTenant(tenantId), count);
    }

    /**
     * Clone every numbering definition from the template tenant ({@code demo})
     * into {@code newTenantId}, resetting each counter to {@code min_value -
     * step_value} so the new tenant's first allocation returns exactly
     * {@code min_value}. Called from {@code TenantAdminService.create} right
     * after the registry row is inserted; idempotent via {@code ON CONFLICT
     * DO NOTHING} so a retry of a half-failed tenant creation doesn't
     * double-seed.
     *
     * <p>If the template tenant has zero definitions (unusual — would mean
     * the platform was installed without seeding the {@code USER} def), this
     * is a no-op and the new tenant's first {@code next(...)} call will get
     * a {@code "Numbering definition not found"} error. That failure mode is
     * preserved deliberately so an empty-defs misconfiguration surfaces loudly
     * the first time someone tries to allocate.
     */
    @Transactional
    public void seedDefaultsForTenant(String newTenantId) {
        requireTenant(newTenantId);
        jdbc.update(
                "INSERT INTO core_numbering_management " +
                "    (tenant_id, code_kbn, format_sentence, recycle_division, zero_insert, " +
                "     seq_id_digit, date_format_sentence, min_value, max_value, step_value, " +
                "     seq_id, description) " +
                "SELECT ?, code_kbn, format_sentence, recycle_division, zero_insert, " +
                "       seq_id_digit, date_format_sentence, min_value, max_value, step_value, " +
                "       min_value - step_value, description " +
                "  FROM core_numbering_management " +
                " WHERE tenant_id = ? " +
                "ON CONFLICT (tenant_id, code_kbn) DO NOTHING",
                newTenantId, TEMPLATE_TENANT);
    }

    /**
     * Reject null / blank tenantId at the API boundary so we never silently
     * route an allocation into a phantom tenant bucket. Every public method
     * funnels through here. The contract is loud: <em>NumberingService is
     * per-tenant; callers MUST resolve their tenant before invoking.</em>
     */
    private static String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "NumberingService requires a non-null tenantId — callers must resolve "
                            + "RequestContext.tenantIdOrDefault() before invoking");
        }
        return tenantId;
    }

    /** Drop the cached definition for one (tenantId, codeKbn) — call after editing a definition row. */
    public void invalidate(String tenantId, String codeKbn) {
        defCache.invalidate(new DefCacheKey(tenantId, codeKbn));
    }

    /** Drop all cached definitions — call after bulk DB updates. */
    public void invalidateAll() {
        defCache.invalidateAll();
    }

    // ---------- core logic ----------

    private List<String> doAllocate(String codeKbn, String fixedKey, String tenantId, int count) {
        if (count <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "count must be > 0");
        }
        NumberingDef def = defCache.get(new DefCacheKey(tenantId, codeKbn));
        if (def == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "Numbering definition not found: " + codeKbn + " (tenant=" + tenantId + ")");
        }

        String datePart = computeDatePart(def.recycleDivision(), def.dateFormatSentence());
        String expandedKey = def.formatSentence()
                .replace("[%K]", fixedKey == null ? "" : fixedKey)
                .replace("[%D]", datePart);

        long delta = def.stepValue() * count;
        long lastSeq;
        if (fixedKey == null || fixedKey.isEmpty()) {
            lastSeq = incrementManagement(codeKbn, tenantId, delta, def, count);
        } else {
            ensureKeyRow(codeKbn, expandedKey, tenantId, def);
            lastSeq = incrementKey(codeKbn, expandedKey, tenantId, delta, def, count);
        }

        long firstSeq = lastSeq - delta + def.stepValue();
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long s = firstSeq + i * def.stepValue();
            String formatted = padSeq(s, def.seqIdDigit(), def.zeroInsert());
            out.add(expandedKey.replace("[%]", formatted));
        }
        return out;
    }

    private long incrementManagement(String codeKbn, String tenantId,
                                     long delta, NumberingDef def, int count) {
        // Atomic conditional update: only commits if (seq_id + delta) would not exceed max_value.
        List<Long> rows = jdbc.query(
                "UPDATE core_numbering_management " +
                "   SET seq_id = seq_id + ? " +
                " WHERE tenant_id = ? AND code_kbn = ? AND seq_id + ? <= ? " +
                "RETURNING seq_id",
                (rs, n) -> rs.getLong(1),
                delta, tenantId, codeKbn, delta, def.maxValue());

        if (!rows.isEmpty()) return rows.get(0);

        if (count == 1) {
            // Preserve legacy wrap-on-overflow: reset to min_value, return min_value.
            List<Long> wrap = jdbc.query(
                    "UPDATE core_numbering_management SET seq_id = ? " +
                    "WHERE tenant_id = ? AND code_kbn = ? RETURNING seq_id",
                    (rs, n) -> rs.getLong(1),
                    def.minValue(), tenantId, codeKbn);
            if (wrap.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "Numbering definition not found: " + codeKbn + " (tenant=" + tenantId + ")");
            }
            return wrap.get(0);
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                "Numbering '" + codeKbn + "' (tenant=" + tenantId
                        + ") would exceed max_value " + def.maxValue() + " in this batch");
    }

    private void ensureKeyRow(String codeKbn, String expandedKey, String tenantId, NumberingDef def) {
        // tenantId already validated non-null by requireTenant() at the public API boundary.
        jdbc.update(
                "INSERT INTO core_numbering_key (tenant_id, code_kbn, numbering_key, seq_id) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, code_kbn, numbering_key) DO NOTHING",
                tenantId,
                codeKbn,
                expandedKey,
                def.minValue() - def.stepValue());
    }

    private long incrementKey(String codeKbn, String expandedKey, String tenantId,
                              long delta, NumberingDef def, int count) {
        List<Long> rows = jdbc.query(
                "UPDATE core_numbering_key " +
                "   SET seq_id = seq_id + ? " +
                " WHERE tenant_id = ? AND code_kbn = ? AND numbering_key = ? " +
                "   AND seq_id + ? <= ? " +
                "RETURNING seq_id",
                (rs, n) -> rs.getLong(1),
                delta, tenantId, codeKbn, expandedKey, delta, def.maxValue());

        if (!rows.isEmpty()) return rows.get(0);

        if (count == 1) {
            List<Long> wrap = jdbc.query(
                    "UPDATE core_numbering_key SET seq_id = ? " +
                    "WHERE tenant_id = ? AND code_kbn = ? AND numbering_key = ? RETURNING seq_id",
                    (rs, n) -> rs.getLong(1),
                    def.minValue(), tenantId, codeKbn, expandedKey);
            if (wrap.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "Numbering key row not found: " + codeKbn + "/" + expandedKey);
            }
            return wrap.get(0);
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                "Numbering '" + codeKbn + "/" + expandedKey + "' would exceed max_value "
                        + def.maxValue() + " in this batch");
    }

    // ---------- cache loader & helpers ----------

    private NumberingDef loadDef(String tenantId, String codeKbn) {
        try {
            return jdbc.queryForObject(
                    "SELECT format_sentence, recycle_division, zero_insert, seq_id_digit, " +
                    "       date_format_sentence, min_value, max_value, step_value " +
                    "  FROM core_numbering_management WHERE tenant_id = ? AND code_kbn = ?",
                    (rs, n) -> new NumberingDef(
                            rs.getString("format_sentence"),
                            rs.getInt("recycle_division"),
                            rs.getString("zero_insert"),
                            rs.getInt("seq_id_digit"),
                            rs.getString("date_format_sentence"),
                            rs.getLong("min_value"),
                            rs.getLong("max_value"),
                            rs.getLong("step_value")),
                    tenantId, codeKbn);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static String computeDatePart(int recycleDivision, String dateFormatSentence) {
        if (dateFormatSentence != null && !dateFormatSentence.isBlank()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormatSentence));
        }
        return switch (recycleDivision) {
            case 1 -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case 2 -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case 3 -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
            default -> "";
        };
    }

    private static String padSeq(long seq, int digit, String zeroInsert) {
        String s = Long.toString(seq);
        if (s.length() >= digit) return s;
        char pad = (zeroInsert == null || zeroInsert.isEmpty()) ? '0' : zeroInsert.charAt(0);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < digit; i++) sb.append(pad);
        sb.append(s);
        return sb.toString();
    }
}
