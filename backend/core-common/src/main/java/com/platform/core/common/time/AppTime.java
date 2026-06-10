package com.platform.core.common.time;

import java.time.ZoneId;

/**
 * The single business timezone of the platform (Japan hotel operations).
 *
 * <p>Storage and the wire format are timezone-agnostic instants
 * ({@code timestamptz} columns / {@link java.time.OffsetDateTime} fields /
 * ISO-8601 strings with offset) — they never need this constant. Use it ONLY
 * where a wall-clock or calendar decision must be made in business time:
 * cron triggers, day bucketing, date parts in generated numbers, and
 * human-facing formatting (e.g. timestamps embedded in emails).
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private AppTime() {
    }
}
