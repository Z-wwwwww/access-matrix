package com.platform.core.infrastructure.numbering;

/**
 * Immutable subset of {@code core_numbering_management} — everything except the
 * volatile {@code seq_id}. Cached in {@link NumberingService} via Caffeine; the
 * counter itself is incremented atomically in the DB on every allocation.
 */
public record NumberingDef(
        String formatSentence,
        int recycleDivision,
        String zeroInsert,
        int seqIdDigit,
        String dateFormatSentence,
        long minValue,
        long maxValue,
        long stepValue
) {}
