package com.platform.core.common.time;

import java.time.ZoneId;

/**
 * The single business timezone of this deployment (one standalone installation
 * serves one country/market; default: Japan hotel operations).
 *
 * <p>Configured per deployment via {@code app.timezone} ({@code CORE_TIMEZONE}
 * env), bound at startup by {@code AppTimeConfigurer} in core-infrastructure —
 * an invalid zone id fails the boot. Outside a Spring context (plain unit
 * tests) the default applies.
 *
 * <p>Storage and the wire format are timezone-agnostic instants
 * ({@code timestamptz} columns / {@link java.time.OffsetDateTime} fields /
 * ISO-8601 strings with offset) — they never need this value. Use it ONLY
 * where a wall-clock or calendar decision must be made in business time:
 * cron triggers, day bucketing, date parts in generated numbers, and
 * human-facing formatting (e.g. timestamps embedded in emails).
 */
public final class AppTime {

    public static final ZoneId DEFAULT = ZoneId.of("Asia/Tokyo");

    private static volatile ZoneId zone = DEFAULT;

    /** The deployment's business timezone. */
    public static ZoneId zone() {
        return zone;
    }

    /**
     * Startup-only hook for {@code AppTimeConfigurer}. Never call from business
     * code — the value is fixed for the lifetime of the process.
     */
    public static void configure(ZoneId z) {
        zone = z;
    }

    private AppTime() {
    }
}
